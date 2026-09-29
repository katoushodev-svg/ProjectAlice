import 'package:flutter/foundation.dart';

import '../../application/model/conversation_send_event.dart';
import '../../application/model/message_page.dart';
import '../../application/port/conversation_gateway.dart';
import '../../domain/conversation.dart';
import '../../domain/message.dart';
import '../../domain/message_role.dart';
import '../../domain/outgoing_message.dart';

final class SimulatorConversationGateway implements ConversationGateway {
  const SimulatorConversationGateway();

  static const int _historyMessageCount = 150;
  static const String _cursorPrefix = 'simulator-older-before-';

  @override
  Future<GatewayResult<Conversation?>> getConversation() async {
    return const GatewaySuccess<Conversation?>(null);
  }

  @override
  Future<GatewayResult<MessagePage>> getMessages({
    int limit = 50,
    String? cursor,
  }) async {
    debugPrint(
      '[SIMULATOR] getMessages() '
      'limit=$limit '
      'cursor=$cursor',
    );
    final endExclusive = cursor == null
        ? _historyMessageCount
        : int.tryParse(cursor.substring(_cursorPrefix.length)) ?? 0;
    final start = (endExclusive - limit).clamp(0, _historyMessageCount);
    final end = endExclusive.clamp(start, _historyMessageCount);
    final messages = List<Message>.generate(
      end - start,
      (offset) => _historyMessage(start + offset + 1),
      growable: false,
    );

    return GatewaySuccess<MessagePage>(
      MessagePage(
        messages: messages,
        nextCursor: start > 0 ? '$_cursorPrefix$start' : null,
        hasMore: start > 0,
      ),
    );
  }

  Message _historyMessage(int sequence) {
    final id = sequence.toString().padLeft(3, '0');
    return Message(
      id: 'simulator-history-$id',
      role: sequence.isOdd ? MessageRole.user : MessageRole.assistant,
      content: 'Simulator用の履歴メッセージ $id',
      createdAt: DateTime.utc(2026).add(Duration(minutes: sequence)),
    );
  }

  @override
  Stream<ConversationSendEvent> sendMessage(
    OutgoingMessage outgoingMessage,
  ) async* {
    const requestId = 'simulator-request-001';

    final now = DateTime.now();

    final userMessage = Message(
      id: 'simulator-user-message-001',
      role: MessageRole.user,
      content: outgoingMessage.content,
      createdAt: now,
    );

    final assistantMessage = Message(
      id: 'simulator-assistant-message-001',
      role: MessageRole.assistant,
      content: 'こんにちは。これはSimulator用のFake Gatewayです。',
      createdAt: now,
    );

    yield StreamStarted(
      requestId,
      userMessage: userMessage,
    );

    yield const AssistantDelta(
      requestId: requestId,
      delta: 'こんにちは。これは',
    );

    yield const AssistantDelta(
      requestId: requestId,
      delta: 'Simulator用のFake Gatewayです。',
    );

    yield AssistantCompleted(
      requestId: requestId,
      messages: <Message>[
        userMessage,
        assistantMessage,
      ],
      userMessage: userMessage,
      assistantMessage: assistantMessage,
    );
  }
}
