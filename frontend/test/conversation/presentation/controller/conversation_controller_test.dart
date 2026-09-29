import 'dart:async';

import 'package:alice/conversation/application/error/conversation_failure.dart';
import 'package:alice/conversation/application/model/conversation_send_event.dart';
import 'package:alice/conversation/application/model/message_page.dart';
import 'package:alice/conversation/application/port/conversation_gateway.dart';
import 'package:alice/conversation/application/port/idempotency_key_generator.dart';
import 'package:alice/conversation/application/state/conversation_screen_state.dart';
import 'package:alice/conversation/domain/conversation.dart';
import 'package:alice/conversation/domain/message.dart';
import 'package:alice/conversation/domain/message_role.dart';
import 'package:alice/conversation/domain/outgoing_message.dart';
import 'package:alice/conversation/presentation/controller/conversation_controller.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('loads conversation before the latest 50-message page', () async {
    final gateway = _FakeGateway();
    final controller = ConversationController(gateway: gateway);
    addTearDown(controller.dispose);

    await controller.loadInitial();

    expect(gateway.calls, ['conversation', 'messages']);
    expect(gateway.limit, 50);
    expect(gateway.cursor, isNull);
    expect(controller.state.messages, hasLength(1));
  });

  test(
    'reconciles an invalid older cursor with a cursorless latest page',
    () async {
      final latestMessage = Message(
        id: 'latest-message',
        role: MessageRole.assistant,
        content: 'latest',
        createdAt: DateTime.utc(2026, 2),
      );
      final gateway = _FakeGateway(
        messageResults: [
          GatewaySuccess(_page(nextCursor: 'stale-cursor', hasMore: true)),
          GatewayFailure(
            ConversationFailure(
              category: ConversationFailureCategory.invalidCursor,
              operation: ConversationOperation.pagination,
            ),
          ),
          GatewaySuccess(_page(messages: [latestMessage])),
        ],
      );
      final controller = ConversationController(gateway: gateway);
      addTearDown(controller.dispose);

      await controller.loadInitial();
      await controller.loadOlder();

      expect(gateway.requestedCursors, [null, 'stale-cursor', null]);
      expect(controller.state.status, ConversationScreenStatus.ready);
      expect(controller.state.messages, [latestMessage]);
      expect(controller.state.pagination.hasMore, isFalse);
    },
  );

  test('continues to messages when conversation is not found', () async {
    final gateway = _FakeGateway(conversation: const GatewaySuccess(null));
    final controller = ConversationController(gateway: gateway);
    addTearDown(controller.dispose);

    await controller.loadInitial();

    expect(gateway.calls, ['conversation', 'messages']);
    expect(controller.state.messages, isNotEmpty);
  });

  test(
    'does not request messages after initial conversation failure',
    () async {
      final gateway = _FakeGateway(
        conversation: GatewayFailure(
          ConversationFailure(
            category: ConversationFailureCategory.networkUnavailable,
            operation: ConversationOperation.initialLoad,
          ),
        ),
      );
      final controller = ConversationController(gateway: gateway);
      addTearDown(controller.dispose);

      await controller.loadInitial();

      expect(gateway.calls, ['conversation']);
      expect(
        controller.state.failure!.category,
        ConversationFailureCategory.networkUnavailable,
      );
    },
  );

  test('prevents concurrent initial loads', () async {
    final gate = Completer<GatewayResult<Conversation?>>();
    final gateway = _FakeGateway(conversationFuture: gate.future);
    final controller = ConversationController(gateway: gateway);
    addTearDown(controller.dispose);

    final first = controller.loadInitial();
    final second = controller.loadInitial();
    gate.complete(const GatewaySuccess(null));
    await Future.wait([first, second]);

    expect(gateway.calls, ['conversation', 'messages']);
  });

  test('retries the full flow after an initial failure', () async {
    final gateway = _FakeGateway(
      conversationResults: [
        GatewayFailure(
          ConversationFailure(
            category: ConversationFailureCategory.networkUnavailable,
            operation: ConversationOperation.initialLoad,
          ),
        ),
        const GatewaySuccess(null),
      ],
    );
    final controller = ConversationController(gateway: gateway);
    addTearDown(controller.dispose);

    await controller.loadInitial();
    await controller.loadInitial();

    expect(gateway.calls, ['conversation', 'conversation', 'messages']);
    expect(controller.state.messages, hasLength(1));
  });

  test('ignores a delayed result after disposal', () async {
    final gate = Completer<GatewayResult<Conversation?>>();
    final gateway = _FakeGateway(conversationFuture: gate.future);
    final controller = ConversationController(gateway: gateway);

    final load = controller.loadInitial();
    controller.dispose();
    gate.complete(const GatewaySuccess(null));
    await load;

    expect(gateway.calls, ['conversation']);
  });

  test(
    'same-send retry reuses the original content and idempotency key',
    () async {
      final gateway = _FakeGateway(
        sendResults: [
          Stream.value(
            SendFailed(
              requestId: null,
              failure: ConversationFailure(
                category: ConversationFailureCategory.conversationBusy,
                operation: ConversationOperation.send,
                resultCertainty: SendResultCertainty.knownFailed,
              ),
            ),
          ),
          const Stream<ConversationSendEvent>.empty(),
        ],
      );
      final keys = _Keys();
      final controller = ConversationController(
        gateway: gateway,
        idempotencyKeyGenerator: keys,
      );
      addTearDown(controller.dispose);
      await controller.loadInitial();

      expect(controller.send('original'), isTrue);
      await Future<void>.delayed(Duration.zero);
      final original = gateway.outgoing.single;
      expect(controller.retrySameSend(), isTrue);
      await Future<void>.delayed(Duration.zero);

      expect(gateway.outgoing, [original, original]);
      expect(keys.count, 1);
    },
  );

  test(
    'request in progress waits for a user action and keeps the key',
    () async {
      final gateway = _FakeGateway(
        sendResults: [
          Stream.value(
            SendFailed(
              requestId: null,
              failure: ConversationFailure(
                category: ConversationFailureCategory.requestInProgress,
                operation: ConversationOperation.send,
                resultCertainty: SendResultCertainty.knownFailed,
                retryAfter: const Duration(minutes: 1),
              ),
            ),
          ),
          const Stream<ConversationSendEvent>.empty(),
        ],
      );
      final keys = _Keys();
      final controller = ConversationController(
        gateway: gateway,
        idempotencyKeyGenerator: keys,
      );
      addTearDown(controller.dispose);
      await controller.loadInitial();
      controller.send('original');
      await Future<void>.delayed(Duration.zero);

      expect(controller.canCheckResult, isFalse);
      expect(controller.checkResult(), isFalse);
      expect(gateway.outgoing, hasLength(1));
      expect(keys.count, 1);
    },
  );

  test('terminal failure starts a new logical send with a fresh key', () async {
    final user = Message(
      id: 'canonical-user',
      role: MessageRole.user,
      content: 'original',
      createdAt: DateTime.utc(2026),
    );
    final gateway = _FakeGateway(
      sendResults: [
        Stream.fromIterable([
          StreamStarted('request-1', userMessage: user),
          SendFailed(
            requestId: 'request-1',
            failure: ConversationFailure(
              category: ConversationFailureCategory.generationFailed,
              operation: ConversationOperation.send,
              resultCertainty: SendResultCertainty.knownFailed,
              requestId: 'request-1',
              terminalStreamFailure: true,
            ),
          ),
        ]),
        const Stream<ConversationSendEvent>.empty(),
      ],
    );
    final keys = _Keys();
    final controller = ConversationController(
      gateway: gateway,
      idempotencyKeyGenerator: keys,
    );
    addTearDown(controller.dispose);
    await controller.loadInitial();
    controller.send('original');
    await Future<void>.delayed(Duration.zero);
    final old = gateway.outgoing.single;

    expect(controller.sendSameContentAsNew(), isTrue);
    await Future<void>.delayed(Duration.zero);

    expect(gateway.outgoing, hasLength(2));
    expect(gateway.outgoing.last.content, 'original');
    expect(gateway.outgoing.last.idempotencyKey, isNot(old.idempotencyKey));
    expect(keys.count, 2);
  });

  test(
    'fails initial load when the message page fails and can retry',
    () async {
      final gateway = _FakeGateway(
        conversationResults: [
          const GatewaySuccess(null),
          const GatewaySuccess(null),
        ],
        messageResults: [
          GatewayFailure(
            ConversationFailure(
              category: ConversationFailureCategory.networkUnavailable,
              operation: ConversationOperation.initialLoad,
            ),
          ),
          GatewaySuccess(_page()),
        ],
      );
      final controller = ConversationController(gateway: gateway);
      addTearDown(controller.dispose);

      await controller.loadInitial();
      expect(
        controller.state.status,
        ConversationScreenStatus.initialLoadFailed,
      );
      expect(controller.state.messages, isEmpty);

      await controller.loadInitial();
      expect(controller.state.status, ConversationScreenStatus.ready);
      expect(controller.state.messages, hasLength(1));
    },
  );
}

final class _FakeGateway implements ConversationGateway {
  _FakeGateway({
    GatewayResult<Conversation?>? conversation,
    this.conversationFuture,
    List<GatewayResult<Conversation?>>? conversationResults,
    List<GatewayResult<MessagePage>>? messageResults,
    List<Stream<ConversationSendEvent>>? sendResults,
  }) : _conversationResults =
           conversationResults ?? [conversation ?? const GatewaySuccess(null)],
       _messageResults = messageResults ?? [GatewaySuccess(_page())],
       _sendResults =
           sendResults ?? [const Stream<ConversationSendEvent>.empty()];

  final Future<GatewayResult<Conversation?>>? conversationFuture;
  final List<GatewayResult<Conversation?>> _conversationResults;
  final List<GatewayResult<MessagePage>> _messageResults;
  final List<Stream<ConversationSendEvent>> _sendResults;
  final calls = <String>[];
  final requestedCursors = <String?>[];
  final outgoing = <OutgoingMessage>[];
  int? limit;
  String? cursor;

  @override
  Future<GatewayResult<Conversation?>> getConversation() {
    calls.add('conversation');
    return conversationFuture ?? Future.value(_conversationResults.removeAt(0));
  }

  @override
  Future<GatewayResult<MessagePage>> getMessages({
    int limit = 50,
    String? cursor,
  }) {
    calls.add('messages');
    this.limit = limit;
    this.cursor = cursor;
    requestedCursors.add(cursor);
    return Future.value(_messageResults.removeAt(0));
  }

  @override
  Stream<ConversationSendEvent> sendMessage(OutgoingMessage outgoingMessage) {
    outgoing.add(outgoingMessage);
    return _sendResults.removeAt(0);
  }
}

final class _Keys implements IdempotencyKeyGenerator {
  int count = 0;
  @override
  String generate() {
    count++;
    return '00000000-0000-4000-8000-${count.toString().padLeft(12, '0')}';
  }
}

MessagePage _page({
  List<Message>? messages,
  String? nextCursor,
  bool hasMore = false,
}) => MessagePage(
  messages:
      messages ??
      [
        Message(
          id: 'message-1',
          role: MessageRole.user,
          content: 'fixture',
          createdAt: DateTime.utc(2026),
        ),
      ],
  nextCursor: nextCursor,
  hasMore: hasMore,
);
