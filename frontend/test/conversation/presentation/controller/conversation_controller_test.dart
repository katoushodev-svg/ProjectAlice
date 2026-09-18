import 'dart:async';

import 'package:alice/conversation/application/error/conversation_failure.dart';
import 'package:alice/conversation/application/model/conversation_send_event.dart';
import 'package:alice/conversation/application/model/message_page.dart';
import 'package:alice/conversation/application/port/conversation_gateway.dart';
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
  }) : _conversationResults =
           conversationResults ?? [conversation ?? const GatewaySuccess(null)],
       _messageResults = messageResults ?? [GatewaySuccess(_page())];

  final Future<GatewayResult<Conversation?>>? conversationFuture;
  final List<GatewayResult<Conversation?>> _conversationResults;
  final List<GatewayResult<MessagePage>> _messageResults;
  final calls = <String>[];
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
    return Future.value(_messageResults.removeAt(0));
  }

  @override
  Stream<ConversationSendEvent> sendMessage(OutgoingMessage outgoingMessage) =>
      const Stream.empty();
}

MessagePage _page() => MessagePage(
  messages: [
    Message(
      id: 'message-1',
      role: MessageRole.user,
      content: 'fixture',
      createdAt: DateTime.utc(2026),
    ),
  ],
  nextCursor: null,
  hasMore: false,
);
