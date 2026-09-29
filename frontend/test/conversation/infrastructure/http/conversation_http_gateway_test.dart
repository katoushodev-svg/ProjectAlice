import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:alice/app/app_configuration.dart';
import 'package:alice/conversation/application/error/conversation_failure.dart';
import 'package:alice/conversation/application/model/conversation_send_event.dart';
import 'package:alice/conversation/application/model/message_page.dart';
import 'package:alice/conversation/application/port/conversation_gateway.dart';
import 'package:alice/conversation/domain/outgoing_message.dart';
import 'package:alice/conversation/infrastructure/http/conversation_http_gateway.dart';

void main() {
  AppConfiguration configuration() {
    return AppConfiguration.fromRaw(
      rawBaseUrl: 'http://127.0.0.1:8080',
      allowLocalHttp: true,
    );
  }

  test('GET conversation maps a successful response', () async {
    final client = MockClient((request) async {
      expect(request.method, 'GET');
      expect(
        request.url.toString(),
        'http://127.0.0.1:8080/api/v1/conversation',
      );
      expect(request.headers['accept'], 'application/json');

      return http.Response(
        jsonEncode({
          'id': '7f1f8e2a-4b3c-4d5e-8f60-123456789abc',
          'createdAt': '2026-08-14T15:00:00.000+09:00',
          'updatedAt': '2026-08-14T15:05:30.000+09:00',
        }),
        200,
        headers: {'content-type': 'application/json'},
      );
    });

    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: client,
    );

    final result = await gateway.getConversation();

    expect(result, isA<GatewaySuccess>());
  });

  test('GET conversation maps 404 CONVERSATION_NOT_FOUND to null', () async {
    final client = MockClient((request) async {
      return http.Response.bytes(
        utf8.encode(
          jsonEncode({
            'type': 'urn:project-alice:problem:conversation-not-found',
            'title': 'Conversation not found',
            'status': 404,
            'detail': 'Conversationはまだ作成されていません。',
            'code': 'CONVERSATION_NOT_FOUND',
            'requestId': '4de9670d-7533-4fc8-a432-3be5263db590',
          }),
        ),
        404,
        headers: {'content-type': 'application/problem+json'},
      );
    });

    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: client,
    );

    final result = await gateway.getConversation();

    expect(result, isA<GatewaySuccess>(), reason: 'actual result: $result');
    expect((result as GatewaySuccess).value, isNull);
  });

  test('GET messages sends limit and opaque cursor unchanged', () async {
    final client = MockClient((request) async {
      expect(request.method, 'GET');
      expect(request.url.path, '/api/v1/conversation/messages');
      expect(request.url.queryParameters['limit'], '50');
      expect(request.url.queryParameters['cursor'], 'opaque-cursor-value');

      return http.Response(
        jsonEncode({'messages': [], 'nextCursor': null, 'hasMore': false}),
        200,
        headers: {'content-type': 'application/json'},
      );
    });

    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: client,
    );

    final result = await gateway.getMessages(
      limit: 50,
      cursor: 'opaque-cursor-value',
    );

    expect(result, isA<GatewaySuccess<MessagePage>>());
  });

  test('GET messages rejects an invalid limit locally', () async {
    final client = MockClient((request) async {
      fail('HTTP request must not be sent');
    });

    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: client,
    );

    final result = await gateway.getMessages(limit: 101);

    expect(result, isA<GatewayFailure>());
    expect(
      (result as GatewayFailure).failure.category,
      ConversationFailureCategory.validation,
    );
  });

  test('GET messages maps INVALID_CURSOR to its pagination category', () async {
    final client = MockClient((request) async {
      return http.Response(
        jsonEncode({
          'type': 'urn:project-alice:problem:invalid-cursor',
          'title': 'Invalid cursor',
          'status': 400,
          'detail': 'Cursor is no longer valid.',
          'code': 'INVALID_CURSOR',
        }),
        400,
        headers: {'content-type': 'application/problem+json'},
      );
    });

    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: client,
    );
    final result = await gateway.getMessages(cursor: 'stale-cursor');

    expect(result, isA<GatewayFailure<MessagePage>>());
    expect(
      (result as GatewayFailure<MessagePage>).failure.category,
      ConversationFailureCategory.invalidCursor,
    );
  });

  test('POST sends only content and required headers', () async {
    const key = '123e4567-e89b-42d3-a456-426614174000';

    http.BaseRequest? capturedRequest;

    final client = MockClient((request) async {
      capturedRequest = request;

      return http.Response.bytes(
        utf8.encode(
          'event: stream.started\n'
          'data: '
          '{"requestId":"request-1",'
          '"userMessage":{'
          '"id":"11111111-1111-4111-8111-111111111111",'
          '"role":"user",'
          '"content":"こんにちは",'
          '"createdAt":"2026-09-22T22:00:00.000+09:00"'
          '}}\n\n'
          'event: assistant.completed\n'
          'data: '
          '{"requestId":"request-1",'
          '"userMessage":{'
          '"id":"11111111-1111-4111-8111-111111111111",'
          '"role":"user",'
          '"content":"こんにちは",'
          '"createdAt":"2026-09-22T22:00:00.000+09:00"'
          '},'
          '"assistantMessage":{'
          '"id":"22222222-2222-4222-8222-222222222222",'
          '"role":"assistant",'
          '"content":"こんにちは。どうしましたか？",'
          '"createdAt":"2026-09-22T22:00:01.000+09:00"'
          '}}\n\n',
        ),
        200,
        headers: {'content-type': 'text/event-stream'},
      );
    });

    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: client,
    );

    final outgoing =
        (OutgoingMessage.create(
              idempotencyKey: key,
              content: 'こんにちは',
            ) as dynamic).value
            as OutgoingMessage;

    final events = await gateway.sendMessage(outgoing).toList();

    expect(capturedRequest, isNotNull);
    expect(capturedRequest!.method, 'POST');
    expect(
      capturedRequest!.url.toString(),
      'http://127.0.0.1:8080/api/v1/conversation/messages',
    );
    expect(capturedRequest!.headers['content-type'], 'application/json');
    expect(capturedRequest!.headers['accept'], 'text/event-stream');
    expect(capturedRequest!.headers['idempotency-key'], key);
    expect(capturedRequest, isA<http.Request>());
    final capturedHttpRequest = capturedRequest! as http.Request;
    expect(
      jsonDecode(utf8.decode(capturedHttpRequest.bodyBytes)),
      equals({'content': 'こんにちは'}),
    );

    expect(events.length, 2);
    expect(events[0], isA<StreamStarted>());
    expect(events[1], isA<AssistantCompleted>());
  });

  test('POST preserves Retry-After for REQUEST_IN_PROGRESS', () async {
    final client = MockClient(
      (request) async => http.Response(
        jsonEncode({
          'type': 'urn:project-alice:problem:request-in-progress',
          'title': 'Request in progress',
          'status': 409,
          'detail': 'Internal detail is not presented.',
          'code': 'REQUEST_IN_PROGRESS',
          'requestId': 'request-1',
        }),
        409,
        headers: {
          'content-type': 'application/problem+json',
          'retry-after': '2',
        },
      ),
    );
    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: client,
    );

    final event = await gateway
        .sendMessage(_outgoing('retry-after', 'こんにちは'))
        .single;

    expect(event, isA<SendFailed>());
    final failure = (event as SendFailed).failure;
    expect(failure.category, ConversationFailureCategory.requestInProgress);
    expect(failure.retryAfter, const Duration(seconds: 2));
  });

  test('POST duplicate assistant.completed is a protocol violation', () async {
    const key = '123e4567-e89b-42d3-a456-426614174000';

    final client = MockClient((request) async {
      return http.Response.bytes(
        utf8.encode(
          'event: stream.started\n'
          'data: '
          '{"requestId":"request-duplicate-completed",'
          '"userMessage":{'
          '"id":"11111111-1111-4111-8111-111111111111",'
          '"role":"user",'
          '"content":"こんにちは",'
          '"createdAt":"2026-09-22T22:00:00.000+09:00"'
          '}}\n\n'
          'event: assistant.completed\n'
          'data: '
          '{"requestId":"request-duplicate-completed",'
          '"userMessage":{'
          '"id":"11111111-1111-4111-8111-111111111111",'
          '"role":"user",'
          '"content":"こんにちは",'
          '"createdAt":"2026-09-22T22:00:00.000+09:00"'
          '},'
          '"assistantMessage":{'
          '"id":"22222222-2222-4222-8222-222222222222",'
          '"role":"assistant",'
          '"content":"回答",'
          '"createdAt":"2026-09-22T22:00:01.000+09:00"'
          '}}\n\n'
          'event: assistant.completed\n'
          'data: '
          '{"requestId":"request-duplicate-completed",'
          '"userMessage":{'
          '"id":"11111111-1111-4111-8111-111111111111",'
          '"role":"user",'
          '"content":"こんにちは",'
          '"createdAt":"2026-09-22T22:00:00.000+09:00"'
          '},'
          '"assistantMessage":{'
          '"id":"22222222-2222-4222-8222-222222222222",'
          '"role":"assistant",'
          '"content":"回答",'
          '"createdAt":"2026-09-22T22:00:01.000+09:00"'
          '}}\n\n',
        ),
        200,
        headers: {'content-type': 'text/event-stream'},
      );
    });

    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: client,
    );

    final outgoing =
        (OutgoingMessage.create(
              idempotencyKey: key,
              content: 'こんにちは',
            ) as dynamic).value
            as OutgoingMessage;

    final events = await gateway.sendMessage(outgoing).toList();

    expect(events, hasLength(3));
    expect(events[0], isA<StreamStarted>());
    expect(events[1], isA<AssistantCompleted>());
    expect(events[2], isA<SendFailed>());

    final failure = (events[2] as SendFailed).failure;
    expect(failure.category, ConversationFailureCategory.protocolViolation);
    expect(failure.resultCertainty, SendResultCertainty.resultUnknown);
  });

  test(
    'POST event after assistant.completed is a protocol violation',
    () async {
      const key = '123e4567-e89b-42d3-a456-426614174000';

      final client = MockClient((request) async {
        return http.Response.bytes(
          utf8.encode(
            'event: stream.started\n'
            'data: '
            '{"requestId":"request-post-terminal",'
            '"userMessage":{'
            '"id":"11111111-1111-4111-8111-111111111111",'
            '"role":"user",'
            '"content":"こんにちは",'
            '"createdAt":"2026-09-22T22:00:00.000+09:00"'
            '}}\n\n'
            'event: assistant.completed\n'
            'data: '
            '{"requestId":"request-post-terminal",'
            '"userMessage":{'
            '"id":"11111111-1111-4111-8111-111111111111",'
            '"role":"user",'
            '"content":"こんにちは",'
            '"createdAt":"2026-09-22T22:00:00.000+09:00"'
            '},'
            '"assistantMessage":{'
            '"id":"22222222-2222-4222-8222-222222222222",'
            '"role":"assistant",'
            '"content":"回答",'
            '"createdAt":"2026-09-22T22:00:01.000+09:00"'
            '}}\n\n'
            'event: assistant.delta\n'
            'data: '
            '{"requestId":"request-post-terminal","delta":"後続"}\n\n',
          ),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      });

      final gateway = ConversationHttpGateway(
        configuration: configuration(),
        client: client,
      );

      final outgoing =
          (OutgoingMessage.create(
                idempotencyKey: key,
                content: 'こんにちは',
              ) as dynamic).value
              as OutgoingMessage;

      final events = await gateway.sendMessage(outgoing).toList();

      expect(events, hasLength(3));
      expect(events[0], isA<StreamStarted>());
      expect(events[1], isA<AssistantCompleted>());
      expect(events[2], isA<SendFailed>());

      final failure = (events[2] as SendFailed).failure;
      expect(failure.category, ConversationFailureCategory.protocolViolation);
      expect(failure.resultCertainty, SendResultCertainty.resultUnknown);
    },
  );

  test('POST event after stream.failed is a protocol violation', () async {
    const key = '123e4567-e89b-42d3-a456-426614174000';

    final client = MockClient((request) async {
      return http.Response.bytes(
        utf8.encode(
          'event: stream.started\n'
          'data: '
          '{"requestId":"request-failed-terminal",'
          '"userMessage":{'
          '"id":"11111111-1111-4111-8111-111111111111",'
          '"role":"user",'
          '"content":"こんにちは",'
          '"createdAt":"2026-09-22T22:00:00.000+09:00"'
          '}}\n\n'
          'event: stream.failed\n'
          'data: '
          '{"requestId":"request-failed-terminal",'
          '"code":"RESPONSE_GENERATION_FAILED"}\n\n'
          'event: assistant.delta\n'
          'data: '
          '{"requestId":"request-failed-terminal","delta":"後続"}\n\n',
        ),
        200,
        headers: {'content-type': 'text/event-stream'},
      );
    });

    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: client,
    );

    final outgoing =
        (OutgoingMessage.create(
              idempotencyKey: key,
              content: 'こんにちは',
            ) as dynamic).value
            as OutgoingMessage;

    final events = await gateway.sendMessage(outgoing).toList();

    expect(events, hasLength(3));
    expect(events[0], isA<StreamStarted>());
    expect(events[1], isA<SendFailed>());
    expect(events[2], isA<SendFailed>());

    final failure = (events[2] as SendFailed).failure;
    expect(failure.category, ConversationFailureCategory.protocolViolation);
    expect(failure.resultCertainty, SendResultCertainty.resultUnknown);
  });
  test('POST rejects assistant.delta before stream.started', () async {
    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        return http.Response.bytes(
          utf8.encode(
            'event: assistant.delta\n'
            'data: {"requestId":"request-1","delta":"先行"}\n\n',
          ),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }),
    );

    final outgoing = _outgoing('before-start', 'こんにちは');
    final events = await gateway.sendMessage(outgoing).toList();

    expect(events, hasLength(1));
    expect(events.single, isA<SendFailed>());
    expect(
      (events.single as SendFailed).failure.category,
      ConversationFailureCategory.protocolViolation,
    );
  });

  test('POST rejects request ID mismatch after stream.started', () async {
    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        return http.Response.bytes(
          utf8.encode(
            '${_startedEvent('request-1')}'
            'event: assistant.delta\n'
            'data: {"requestId":"request-2","delta":"回答"}\n\n',
          ),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }),
    );

    final events = await gateway
        .sendMessage(_outgoing('id-mismatch', 'こんにちは'))
        .toList();

    expect(events, hasLength(2));
    expect(events[0], isA<StreamStarted>());
    expect(events[1], isA<SendFailed>());
    expect(
      (events[1] as SendFailed).failure.category,
      ConversationFailureCategory.protocolViolation,
    );
  });

  test('POST rejects duplicate stream.started', () async {
    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        return http.Response.bytes(
          utf8.encode(_startedEvent('request-1') + _startedEvent('request-1')),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }),
    );

    final events = await gateway
        .sendMessage(_outgoing('duplicate-start', 'こんにちは'))
        .toList();

    expect(events, hasLength(2));
    expect(events[0], isA<StreamStarted>());
    expect(events[1], isA<SendFailed>());
    expect(
      (events[1] as SendFailed).failure.category,
      ConversationFailureCategory.protocolViolation,
    );
  });

  test(
    'POST maps connection close without terminal to resultUnknown',
    () async {
      final gateway = ConversationHttpGateway(
        configuration: configuration(),
        client: MockClient((request) async {
          return http.Response.bytes(
            utf8.encode(_startedEvent('request-1')),
            200,
            headers: {'content-type': 'text/event-stream'},
          );
        }),
      );

      final events = await gateway
          .sendMessage(_outgoing('no-terminal', 'こんにちは'))
          .toList();

      expect(events, hasLength(2));
      expect(events[0], isA<StreamStarted>());
      expect(events[1], isA<SendFailed>());
      expect(
        (events[1] as SendFailed).failure.category,
        ConversationFailureCategory.resultUnknown,
      );
      expect(
        (events[1] as SendFailed).failure.resultCertainty,
        SendResultCertainty.resultUnknown,
      );
    },
  );

  test('POST accepts replay completed without assistant.delta', () async {
    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        return http.Response.bytes(
          utf8.encode(
            _startedEvent('request-1') + _completedEvent('request-1'),
          ),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }),
    );

    final events = await gateway
        .sendMessage(_outgoing('replay', 'こんにちは'))
        .toList();

    expect(events, hasLength(2));
    expect(events[0], isA<StreamStarted>());
    expect(events[1], isA<AssistantCompleted>());
  });

  test('POST ignores unknown event type', () async {
    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        return http.Response.bytes(
          utf8.encode(
            '${_startedEvent('request-1')}event: future.event\n'
            'data: {"requestId":"request-1","value":"ignored"}\n\n'
            '${_completedEvent('request-1')}',
          ),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }),
    );

    final events = await gateway
        .sendMessage(_outgoing('unknown-event', 'こんにちは'))
        .toList();

    expect(events, hasLength(2));
    expect(events[0], isA<StreamStarted>());
    expect(events[1], isA<AssistantCompleted>());
  });

  test('POST rejects non event-stream content type', () async {
    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        return http.Response(
          '{}',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final events = await gateway
        .sendMessage(_outgoing('bad-content-type', 'こんにちは'))
        .toList();

    expect(events, hasLength(1));
    expect(events.single, isA<SendFailed>());
    expect(
      (events.single as SendFailed).failure.category,
      ConversationFailureCategory.protocolViolation,
    );
  });

  test('POST rejects assistant.delta JSON data over 64 KiB', () async {
    final delta = 'a' * (64 * 1024);
    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        return http.Response.bytes(
          utf8.encode(
            '${_startedEvent('request-1')}event: assistant.delta\n'
            'data: ${jsonEncode({'requestId': 'request-1', 'delta': delta})}\n\n',
          ),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }),
    );

    final events = await gateway
        .sendMessage(_outgoing('delta-limit', 'こんにちは'))
        .toList();

    expect(events.last, isA<SendFailed>());
    expect(
      (events.last as SendFailed).failure.category,
      ConversationFailureCategory.responseTooLarge,
    );
  });

  test(
    'POST rejects accumulated assistant text over 50,000 code points',
    () async {
      final delta = 'a' * 25001;
      final stream =
          '${_startedEvent('request-1')}'
          'event: assistant.delta\n'
          'data: ${jsonEncode({'requestId': 'request-1', 'delta': delta})}\n\n'
          'event: assistant.delta\n'
          'data: ${jsonEncode({'requestId': 'request-1', 'delta': delta})}\n\n';

      final gateway = ConversationHttpGateway(
        configuration: configuration(),
        client: MockClient((request) async {
          return http.Response.bytes(
            utf8.encode(stream),
            200,
            headers: {'content-type': 'text/event-stream'},
          );
        }),
      );

      final events = await gateway
          .sendMessage(_outgoing('text-limit', 'こんにちは'))
          .toList();

      expect(events.last, isA<SendFailed>());
      expect(
        (events.last as SendFailed).failure.category,
        ConversationFailureCategory.assistantContentTooLong,
      );
    },
  );

  test('POST rejects SSE frame over 1 MiB', () async {
    final data = 'x' * (1024 * 1024);
    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        return http.Response.bytes(
          utf8.encode('event: future.event\ndata: $data\n\n'),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }),
    );

    final events = await gateway
        .sendMessage(_outgoing('frame-limit', 'こんにちは'))
        .toList();

    expect(events, hasLength(1));
    expect(events.single, isA<SendFailed>());
    expect(
      (events.single as SendFailed).failure.category,
      ConversationFailureCategory.sseFrameTooLarge,
    );
  });

  test('POST rejects SSE stream over 8 MiB', () async {
    final event = 'event: future.event\ndata: ${'x' * 1024}\n\n';
    final stream = StringBuffer();
    for (var i = 0; i < 8200; i++) {
      stream.write(event);
    }

    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        return http.Response.bytes(
          utf8.encode(stream.toString()),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }),
    );

    final events = await gateway
        .sendMessage(_outgoing('stream-limit', 'こんにちは'))
        .toList();

    expect(events, hasLength(1));
    expect(events.single, isA<SendFailed>());
    expect(
      (events.single as SendFailed).failure.category,
      ConversationFailureCategory.sseStreamTooLarge,
    );
  });

  test('POST rejects more than 10,000 non-comment SSE events', () async {
    final event = 'event: future.event\ndata: x\n\n';
    final stream = StringBuffer(_startedEvent('request-1'));
    for (var i = 0; i < 10001; i++) {
      stream.write(event);
    }

    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        return http.Response.bytes(
          utf8.encode(stream.toString()),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }),
    );

    final events = await gateway
        .sendMessage(_outgoing('event-limit', 'こんにちは'))
        .toList();

    expect(events.last, isA<SendFailed>());
    expect(
      (events.last as SendFailed).failure.category,
      ConversationFailureCategory.tooManySseEvents,
    );
  });

  test(
    'POST rejects canonical completion with mismatched user content',
    () async {
      final completed = jsonEncode({
        'requestId': 'request-1',
        'userMessage': {
          'id': '11111111-1111-4111-8111-111111111111',
          'role': 'user',
          'content': '別の内容',
          'createdAt': '2026-09-22T22:00:00.000+09:00',
        },
        'assistantMessage': {
          'id': '22222222-2222-4222-8222-222222222222',
          'role': 'assistant',
          'content': '回答',
          'createdAt': '2026-09-22T22:00:01.000+09:00',
        },
      });

      final gateway = ConversationHttpGateway(
        configuration: configuration(),
        client: MockClient((request) async {
          return http.Response.bytes(
            utf8.encode(
              '${_startedEvent('request-1')}event: assistant.completed\n'
              'data: $completed\n\n',
            ),
            200,
            headers: {'content-type': 'text/event-stream'},
          );
        }),
      );

      final events = await gateway
          .sendMessage(_outgoing('canonical-mismatch', 'こんにちは'))
          .toList();

      expect(events, hasLength(2));
      expect(events[0], isA<StreamStarted>());
      expect(events[1], isA<SendFailed>());
      expect(
        (events[1] as SendFailed).failure.category,
        ConversationFailureCategory.protocolViolation,
      );
    },
  );

  test('POST preserves UTF-8 content without trimming', () async {
    const content = '  こんにちは\n世界  ';
    http.Request? captured;

    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        captured = request;
        return http.Response.bytes(
          utf8.encode(
            _startedEvent('request-1') + _completedEvent('request-1'),
          ),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }),
    );

    await gateway.sendMessage(_outgoing('preserve', content)).toList();

    expect(captured, isNotNull);
    expect(jsonDecode(captured!.body), equals({'content': content}));
  });

  test('POST accepts stream.failed as terminal event', () async {
    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        return http.Response.bytes(
          utf8.encode(
            '${_startedEvent('request-1')}event: stream.failed\n'
            'data: {"requestId":"request-1","code":"RESPONSE_GENERATION_FAILED"}\n\n',
          ),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }),
    );

    final events = await gateway
        .sendMessage(_outgoing('failed-terminal', 'こんにちは'))
        .toList();

    expect(events, hasLength(2));
    expect(events[0], isA<StreamStarted>());
    expect(events[1], isA<SendFailed>());
    expect(
      (events[1] as SendFailed).failure.category,
      ConversationFailureCategory.generationFailed,
    );
  });

  test('POST accepts CRLF SSE line endings', () async {
    final gateway = ConversationHttpGateway(
      configuration: configuration(),
      client: MockClient((request) async {
        return http.Response.bytes(
          utf8.encode(
            'event: stream.started\r\n'
            'data: ${_startedData('request-1')}\r\n\r\n'
            'event: assistant.completed\r\n'
            'data: ${_completedData('request-1')}\r\n\r\n',
          ),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }),
    );

    final events = await gateway
        .sendMessage(_outgoing('crlf', 'こんにちは'))
        .toList();

    expect(events, hasLength(2));
    expect(events[0], isA<StreamStarted>());
    expect(events[1], isA<AssistantCompleted>());
  });
}

OutgoingMessage _outgoing(String key, String content) {
  // The test cases use human-readable labels for readability, but
  // OutgoingMessage requires a UUID-shaped idempotency key.
  final result = OutgoingMessage.create(
    idempotencyKey: '123e4567-e89b-42d3-a456-426614174000',
    content: content,
  );

  final dynamic dynamicResult = result;
  if (dynamicResult.value is OutgoingMessage) {
    return dynamicResult.value as OutgoingMessage;
  }

  throw StateError('Failed to create valid OutgoingMessage for test: $key');
}

String _startedData(String requestId) => jsonEncode({
  'requestId': requestId,
  'userMessage': {
    'id': '11111111-1111-4111-8111-111111111111',
    'role': 'user',
    'content': 'こんにちは',
    'createdAt': '2026-09-22T22:00:00.000+09:00',
  },
});

String _completedData(String requestId) => jsonEncode({
  'requestId': requestId,
  'userMessage': {
    'id': '11111111-1111-4111-8111-111111111111',
    'role': 'user',
    'content': 'こんにちは',
    'createdAt': '2026-09-22T22:00:00.000+09:00',
  },
  'assistantMessage': {
    'id': '22222222-2222-4222-8222-222222222222',
    'role': 'assistant',
    'content': '回答',
    'createdAt': '2026-09-22T22:00:01.000+09:00',
  },
});

String _startedEvent(String requestId) =>
    'event: stream.started\ndata: ${_startedData(requestId)}\n\n';

String _completedEvent(String requestId) =>
    'event: assistant.completed\ndata: ${_completedData(requestId)}\n\n';
