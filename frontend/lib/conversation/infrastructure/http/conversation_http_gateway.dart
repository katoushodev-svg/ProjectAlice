import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../../../app/app_configuration.dart';
import '../../application/model/conversation_send_event.dart';
import '../../application/model/message_page.dart';
import '../../application/port/conversation_gateway.dart';
import '../../domain/conversation.dart';
import '../../domain/message.dart';
import '../../domain/message_role.dart';
import '../../domain/outgoing_message.dart';
import '../../infrastructure/api/dto/conversation_dto.dart';
import '../../infrastructure/api/dto/message_dto.dart';
import '../../infrastructure/api/mapping/conversation_api_mapper.dart';
import '../../infrastructure/api/parsing/api_contract_exception.dart';
import '../../infrastructure/api/parsing/sse_frame.dart';
import '../../infrastructure/api/parsing/sse_frame_parser.dart';
import '../../application/error/conversation_failure.dart';

final class ConversationHttpGateway implements ConversationGateway {
  ConversationHttpGateway({
    required AppConfiguration configuration,
    required http.Client client,
  }) : this._internal(configuration, client);

  ConversationHttpGateway._internal(this._configuration, this._client);

  static const int _conversationResponseLimit = 64 * 1024;
  static const int _messageHistoryResponseLimit = 16 * 1024 * 1024;
  static const int _requestBodyLimit = 128 * 1024;

  final AppConfiguration _configuration;
  final http.Client _client;

  Uri get _conversationUri =>
      _configuration.baseUrl.resolve('/api/v1/conversation');

  Uri get _messagesUri =>
      _configuration.baseUrl.resolve('/api/v1/conversation/messages');

  @override
  Future<GatewayResult<Conversation?>> getConversation() async {
    try {
      final response = await _client.get(
        _conversationUri,
        headers: const <String, String>{'Accept': 'application/json'},
      );

      if (response.statusCode == HttpStatus.notFound) {
        final bytes = response.bodyBytes;

        if (bytes.length > 64 * 1024) {
          return GatewayFailure<Conversation?>(
            ConversationFailure(
              category: ConversationFailureCategory.responseTooLarge,
              operation: ConversationOperation.initialLoad,
            ),
          );
        }

        final problem = _decodeObject(bytes);

        if (problem['code'] == 'CONVERSATION_NOT_FOUND') {
          return const GatewaySuccess<Conversation?>(null);
        }

        return GatewayFailure<Conversation?>(
          _parseProblemDetailsFailure(
            response,
            operation: ConversationOperation.initialLoad,
          ),
        );
      }

      if (response.statusCode != HttpStatus.ok) {
        return GatewayFailure<Conversation?>(
          _parseProblemDetailsFailure(
            response,
            operation: ConversationOperation.initialLoad,
          ),
        );
      }

      final bytes = response.bodyBytes;

      if (bytes.length > _conversationResponseLimit) {
        return GatewayFailure<Conversation?>(
          ConversationFailure(
            category: ConversationFailureCategory.responseTooLarge,
            operation: ConversationOperation.initialLoad,
          ),
        );
      }

      final json = _decodeObject(bytes);

      final dto = ConversationDto.fromJson(json);

      return GatewaySuccess<Conversation?>(
        ConversationApiMapper.toConversation(dto),
      );
    } on ApiContractException {
      return GatewayFailure<Conversation?>(
        ConversationFailure(
          category: ConversationFailureCategory.protocolViolation,
          operation: ConversationOperation.initialLoad,
        ),
      );
    } on SocketException {
      return GatewayFailure<Conversation?>(
        ConversationFailure(
          category: ConversationFailureCategory.networkUnavailable,
          operation: ConversationOperation.initialLoad,
        ),
      );
    } on TimeoutException {
      return GatewayFailure<Conversation?>(
        ConversationFailure(
          category: ConversationFailureCategory.responseTimeout,
          operation: ConversationOperation.initialLoad,
        ),
      );
    } catch (_) {
      return GatewayFailure<Conversation?>(
        ConversationFailure(
          category: ConversationFailureCategory.unknown,
          operation: ConversationOperation.initialLoad,
        ),
      );
    }
  }

  @override
  Future<GatewayResult<MessagePage>> getMessages({
    int limit = 50,
    String? cursor,
  }) async {
    try {
      if (limit < 1 || limit > 100) {
        return GatewayFailure<MessagePage>(
          ConversationFailure(
            category: ConversationFailureCategory.validation,
            operation: ConversationOperation.pagination,
          ),
        );
      }

      final queryParameters = <String, String>{
        'limit': '$limit',
        ...?(cursor == null ? null : {'cursor': cursor}),
      };

      final uri = _messagesUri.replace(queryParameters: queryParameters);

      final response = await _client.get(
        uri,
        headers: const <String, String>{'Accept': 'application/json'},
      );

      if (response.statusCode != HttpStatus.ok) {
        return GatewayFailure<MessagePage>(
          _parseProblemDetailsFailure(
            response,
            operation: ConversationOperation.pagination,
          ),
        );
      }

      final bytes = response.bodyBytes;

      if (bytes.length > _messageHistoryResponseLimit) {
        return GatewayFailure<MessagePage>(
          ConversationFailure(
            category: ConversationFailureCategory.responseTooLarge,
            operation: ConversationOperation.pagination,
          ),
        );
      }

      final json = _decodeObject(bytes);

      final rawMessages = json['messages'];
      final rawNextCursor = json['nextCursor'];
      final rawHasMore = json['hasMore'];

      if (rawMessages is! List ||
          (rawNextCursor != null && rawNextCursor is! String) ||
          rawHasMore is! bool) {
        throw const ApiContractException(category: 'invalidFieldType');
      }

      final messages = <Message>[];

      for (final rawMessage in rawMessages) {
        if (rawMessage is! Map<String, dynamic>) {
          throw const ApiContractException(category: 'invalidFieldType');
        }

        final dto = MessageDto.fromJson(rawMessage);

        messages.add(ConversationApiMapper.toMessage(dto));
      }

      try {
        return GatewaySuccess<MessagePage>(
          MessagePage(
            messages: messages,
            nextCursor: rawNextCursor as String?,
            hasMore: rawHasMore,
          ),
        );
      } on ArgumentError {
        throw const ApiContractException(
          category: 'invalidPaginationInvariant',
        );
      }
    } on ApiContractException {
      return GatewayFailure<MessagePage>(
        ConversationFailure(
          category: ConversationFailureCategory.protocolViolation,
          operation: ConversationOperation.pagination,
        ),
      );
    } on SocketException {
      return GatewayFailure<MessagePage>(
        ConversationFailure(
          category: ConversationFailureCategory.networkUnavailable,
          operation: ConversationOperation.pagination,
        ),
      );
    } on TimeoutException {
      return GatewayFailure<MessagePage>(
        ConversationFailure(
          category: ConversationFailureCategory.responseTimeout,
          operation: ConversationOperation.pagination,
        ),
      );
    } catch (_) {
      return GatewayFailure<MessagePage>(
        ConversationFailure(
          category: ConversationFailureCategory.unknown,
          operation: ConversationOperation.pagination,
        ),
      );
    }
  }

  @override
  Stream<ConversationSendEvent> sendMessage(
    OutgoingMessage outgoingMessage,
  ) async* {
    final body = jsonEncode(<String, String>{
      'content': outgoingMessage.content,
    });
    final bodyBytes = utf8.encode(body);

    if (bodyBytes.length > _requestBodyLimit) {
      yield SendFailed(
        requestId: null,
        failure: ConversationFailure(
          category: ConversationFailureCategory.requestBodyTooLarge,
          operation: ConversationOperation.send,
          resultCertainty: SendResultCertainty.knownFailed,
        ),
      );
      return;
    }

    final request = http.Request('POST', _messagesUri)
      ..headers.addAll(<String, String>{
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Idempotency-Key': outgoingMessage.idempotencyKey,
      })
      ..bodyBytes = bodyBytes;

    late final http.StreamedResponse response;
    try {
      response = await _client
          .send(request)
          .timeout(_configuration.httpRequestTimeout);
    } on SocketException {
      yield SendFailed(
        requestId: null,
        failure: ConversationFailure(
          category: ConversationFailureCategory.networkUnavailable,
          operation: ConversationOperation.send,
          resultCertainty: SendResultCertainty.knownFailed,
        ),
      );
      return;
    } on TimeoutException {
      yield SendFailed(
        requestId: null,
        failure: ConversationFailure(
          category: ConversationFailureCategory.responseTimeout,
          operation: ConversationOperation.send,
          resultCertainty: SendResultCertainty.knownFailed,
        ),
      );
      return;
    } catch (_) {
      yield SendFailed(
        requestId: null,
        failure: ConversationFailure(
          category: ConversationFailureCategory.unknown,
          operation: ConversationOperation.send,
          resultCertainty: SendResultCertainty.knownFailed,
        ),
      );
      return;
    }

    if (response.statusCode != HttpStatus.ok) {
      final bytes = await _readBounded(response.stream, 64 * 1024);
      final failure = _mapProblemDetailsBytes(
        bytes,
        statusCode: response.statusCode,
        retryAfter: _retryAfter(response.headers['retry-after']),
      );
      yield SendFailed(requestId: _extractRequestId(bytes), failure: failure);
      return;
    }

    final contentType = response.headers['content-type']?.toLowerCase();
    if (contentType == null || !contentType.startsWith('text/event-stream')) {
      yield SendFailed(
        requestId: null,
        failure: ConversationFailure(
          category: ConversationFailureCategory.protocolViolation,
          operation: ConversationOperation.send,
          resultCertainty: SendResultCertainty.resultUnknown,
        ),
      );
      return;
    }

    String? activeRequestId;
    var terminalReceived = false;
    var accumulatedAssistantCodePoints = 0;

    try {
      final frames = SseFrameParser.parseIncrementally(response.stream);

      await for (final frame in frames) {
        final event = _mapSseFrame(frame, outgoingMessage);
        if (event == null) continue;

        if (terminalReceived) {
          throw const ApiContractException(category: 'protocolViolation');
        }

        final requestId = switch (event) {
          StreamStarted(:final requestId) => requestId,
          AssistantDelta(:final requestId) => requestId,
          AssistantCompleted(:final requestId) => requestId,
          SendFailed(:final requestId) => requestId,
        };

        if (requestId == null || requestId.isEmpty) {
          throw const ApiContractException(category: 'protocolViolation');
        }

        if (event is StreamStarted) {
          if (activeRequestId != null) {
            throw const ApiContractException(category: 'protocolViolation');
          }
          activeRequestId = requestId;
          yield event;
          continue;
        }

        if (activeRequestId == null || requestId != activeRequestId) {
          throw const ApiContractException(category: 'protocolViolation');
        }

        if (event is AssistantDelta) {
          accumulatedAssistantCodePoints += event.delta.runes.length;
          if (accumulatedAssistantCodePoints > 50000) {
            throw const ApiContractException(
              category: 'assistantContentTooLong',
            );
          }
          yield event;
          continue;
        }

        if (event is AssistantCompleted) {
          final userMessage = event.userMessage;
          final assistantMessage = event.assistantMessage;
          if (userMessage == null ||
              assistantMessage == null ||
              userMessage.role != MessageRole.user ||
              userMessage.content != outgoingMessage.content ||
              assistantMessage.role != MessageRole.assistant ||
              assistantMessage.content.runes.length > 50000) {
            throw const ApiContractException(category: 'protocolViolation');
          }
          terminalReceived = true;
          yield event;
          continue;
        }

        if (event is SendFailed) {
          terminalReceived = true;
          yield event;
        }
      }

      if (!terminalReceived) {
        yield SendFailed(
          requestId: activeRequestId,
          failure: ConversationFailure(
            category: ConversationFailureCategory.resultUnknown,
            operation: ConversationOperation.send,
            resultCertainty: SendResultCertainty.resultUnknown,
            requestId: activeRequestId,
          ),
        );
      }
    } on ApiContractException catch (error) {
      yield SendFailed(
        requestId: activeRequestId,
        failure: _mapSseContractFailure(
          error.category,
          requestId: activeRequestId,
        ),
      );
    } on SocketException {
      yield SendFailed(
        requestId: activeRequestId,
        failure: ConversationFailure(
          category: ConversationFailureCategory.resultUnknown,
          operation: ConversationOperation.send,
          resultCertainty: SendResultCertainty.resultUnknown,
          requestId: activeRequestId,
        ),
      );
    } on TimeoutException {
      yield SendFailed(
        requestId: activeRequestId,
        failure: ConversationFailure(
          category: ConversationFailureCategory.resultUnknown,
          operation: ConversationOperation.send,
          resultCertainty: SendResultCertainty.resultUnknown,
          requestId: activeRequestId,
        ),
      );
    } catch (_) {
      yield SendFailed(
        requestId: activeRequestId,
        failure: ConversationFailure(
          category: ConversationFailureCategory.resultUnknown,
          operation: ConversationOperation.send,
          resultCertainty: SendResultCertainty.resultUnknown,
          requestId: activeRequestId,
        ),
      );
    }
  }

  static Map<String, dynamic> _decodeObject(List<int> bytes) {
    try {
      final decoded = jsonDecode(utf8.decode(bytes));

      if (decoded is! Map<String, dynamic>) {
        throw const ApiContractException(category: 'invalidRootType');
      }

      return decoded;
    } on ApiContractException {
      rethrow;
    } on FormatException {
      throw const ApiContractException(category: 'malformedJson');
    }
  }

  static Future<List<int>> _readBounded(
    Stream<List<int>> stream,
    int maxBytes,
  ) async {
    final result = <int>[];
    await for (final chunk in stream) {
      if (result.length + chunk.length > maxBytes) {
        throw const ApiContractException(category: 'responseTooLarge');
      }

      result.addAll(chunk);
    }

    return result;
  }

  static ConversationFailure _parseProblemDetailsFailure(
    http.Response response, {
    required ConversationOperation operation,
  }) {
    return _mapProblemDetailsBytes(
      response.bodyBytes,
      statusCode: response.statusCode,
      operation: operation,
    );
  }

  static ConversationFailure _mapProblemDetailsBytes(
    List<int> bytes, {
    required int statusCode,
    ConversationOperation operation = ConversationOperation.send,
    Duration? retryAfter,
  }) {
    try {
      if (bytes.length > 64 * 1024) {
        return ConversationFailure(
          category: ConversationFailureCategory.responseTooLarge,
          operation: operation,
          resultCertainty: operation == ConversationOperation.send
              ? SendResultCertainty.knownFailed
              : null,
        );
      }

      final decoded = jsonDecode(utf8.decode(bytes));

      if (decoded is! Map<String, dynamic>) {
        return ConversationFailure(
          category: ConversationFailureCategory.protocolViolation,
          operation: operation,
          resultCertainty: operation == ConversationOperation.send
              ? SendResultCertainty.knownFailed
              : null,
        );
      }

      final code = decoded['code'];

      if (code is! String) {
        return ConversationFailure(
          category: ConversationFailureCategory.protocolViolation,
          operation: operation,
          resultCertainty: operation == ConversationOperation.send
              ? SendResultCertainty.knownFailed
              : null,
        );
      }

      final category = _mapProblemCode(code);

      return ConversationFailure(
        category: category,
        operation: operation,
        resultCertainty: operation == ConversationOperation.send
            ? SendResultCertainty.knownFailed
            : null,
        requestId: decoded['requestId'] is String
            ? decoded['requestId'] as String
            : null,
        retryAfter: category == ConversationFailureCategory.requestInProgress
            ? retryAfter
            : null,
      );
    } catch (_) {
      return ConversationFailure(
        category: ConversationFailureCategory.protocolViolation,
        operation: operation,
        resultCertainty: operation == ConversationOperation.send
            ? SendResultCertainty.knownFailed
            : null,
      );
    }
  }

  static Duration? _retryAfter(String? value) {
    final seconds = value == null ? null : int.tryParse(value);
    if (seconds == null || seconds < 0) return null;
    return Duration(seconds: seconds);
  }

  static ConversationFailureCategory _mapProblemCode(String code) {
    switch (code) {
      case 'VALIDATION_ERROR':
      case 'MALFORMED_REQUEST':
      case 'IDEMPOTENCY_KEY_REQUIRED':
      case 'INVALID_IDEMPOTENCY_KEY':
        return ConversationFailureCategory.validation;

      case 'INVALID_CURSOR':
        return ConversationFailureCategory.invalidCursor;

      case 'CONVERSATION_NOT_FOUND':
        return ConversationFailureCategory.unknown;

      case 'CONVERSATION_BUSY':
        return ConversationFailureCategory.conversationBusy;

      case 'REQUEST_IN_PROGRESS':
        return ConversationFailureCategory.requestInProgress;

      case 'IDEMPOTENCY_KEY_CONFLICT':
        return ConversationFailureCategory.idempotencyConflict;

      case 'RESPONSE_GENERATION_FAILED':
        return ConversationFailureCategory.generationFailed;

      case 'RESPONSE_TIMEOUT':
        return ConversationFailureCategory.responseTimeout;

      case 'MESSAGE_SAVE_FAILED':
        return ConversationFailureCategory.messageSaveFailed;

      case 'REQUEST_INTERRUPTED':
        return ConversationFailureCategory.requestInterrupted;

      case 'PAYLOAD_TOO_LARGE':
        return ConversationFailureCategory.requestBodyTooLarge;

      case 'INTERNAL_ERROR':
      case 'SERVICE_UNAVAILABLE':
        return ConversationFailureCategory.serverFailure;

      default:
        return ConversationFailureCategory.unknown;
    }
  }

  static String? _extractRequestId(List<int> bytes) {
    try {
      final decoded = jsonDecode(utf8.decode(bytes));

      if (decoded is Map<String, dynamic> && decoded['requestId'] is String) {
        return decoded['requestId'] as String;
      }
    } catch (_) {
      // Intentionally ignored.
    }

    return null;
  }

  static ConversationSendEvent? _mapSseFrame(
    SseFrame frame,
    OutgoingMessage outgoingMessage,
  ) {
    try {
      const knownEvents = <String>{
        'stream.started',
        'assistant.delta',
        'assistant.completed',
        'stream.failed',
      };

      if (!knownEvents.contains(frame.eventName)) return null;

      if (frame.eventName == 'assistant.delta' &&
          utf8.encode(frame.data).length > 64 * 1024) {
        throw const ApiContractException(category: 'assistantDeltaTooLarge');
      }

      final decoded = jsonDecode(frame.data);
      if (decoded is! Map<String, dynamic>) {
        throw const ApiContractException(category: 'invalidSsePayload');
      }

      final requestId = decoded['requestId'];
      if (requestId is! String || requestId.isEmpty) {
        throw const ApiContractException(category: 'invalidSsePayload');
      }

      switch (frame.eventName) {
        case 'stream.started':
          final rawUserMessage = decoded['userMessage'];
          if (rawUserMessage is! Map<String, dynamic>) {
            throw const ApiContractException(category: 'invalidSsePayload');
          }
          final userMessage = ConversationApiMapper.toMessage(
            MessageDto.fromJson(rawUserMessage),
          );
          if (userMessage.role != MessageRole.user ||
              userMessage.content != outgoingMessage.content) {
            throw const ApiContractException(category: 'protocolViolation');
          }
          return StreamStarted(requestId, userMessage: userMessage);

        case 'assistant.delta':
          final delta = decoded['delta'];
          if (delta is! String) {
            throw const ApiContractException(category: 'invalidSsePayload');
          }
          return AssistantDelta(requestId: requestId, delta: delta);

        case 'assistant.completed':
          final rawUserMessage = decoded['userMessage'];
          final rawAssistantMessage = decoded['assistantMessage'];
          if (rawUserMessage is! Map<String, dynamic> ||
              rawAssistantMessage is! Map<String, dynamic>) {
            throw const ApiContractException(category: 'invalidSsePayload');
          }

          final userMessage = ConversationApiMapper.toMessage(
            MessageDto.fromJson(rawUserMessage),
          );
          final assistantMessage = ConversationApiMapper.toMessage(
            MessageDto.fromJson(rawAssistantMessage),
          );

          return AssistantCompleted(
            requestId: requestId,
            messages: <Message>[userMessage, assistantMessage],
            userMessage: userMessage,
            assistantMessage: assistantMessage,
          );

        case 'stream.failed':
          final code = decoded['code'];
          if (code is! String) {
            throw const ApiContractException(category: 'invalidSsePayload');
          }
          return SendFailed(
            requestId: requestId,
            failure: ConversationFailure(
              category: _mapProblemCode(code),
              operation: ConversationOperation.send,
              resultCertainty: SendResultCertainty.knownFailed,
              requestId: requestId,
              terminalStreamFailure: true,
            ),
          );

        default:
          return null;
      }
    } on ApiContractException {
      rethrow;
    } on FormatException {
      throw const ApiContractException(category: 'invalidSsePayload');
    }
  }

  static ConversationFailure _mapSseContractFailure(
    String category, {
    required String? requestId,
  }) {
    final mappedCategory = switch (category) {
      'sseFrameTooLarge' => ConversationFailureCategory.sseFrameTooLarge,
      'sseStreamTooLarge' => ConversationFailureCategory.sseStreamTooLarge,
      'assistantContentTooLong' =>
        ConversationFailureCategory.assistantContentTooLong,
      'assistantDeltaTooLarge' => ConversationFailureCategory.responseTooLarge,
      'tooManySseEvents' => ConversationFailureCategory.tooManySseEvents,
      _ => ConversationFailureCategory.protocolViolation,
    };

    return ConversationFailure(
      category: mappedCategory,
      operation: ConversationOperation.send,
      resultCertainty: SendResultCertainty.resultUnknown,
      requestId: requestId,
    );
  }
}
