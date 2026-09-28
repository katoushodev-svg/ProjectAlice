import '../../application/model/conversation_send_event.dart';
import '../../application/model/message_page.dart';
import '../../application/port/conversation_gateway.dart';
import '../../domain/conversation.dart';
import '../../domain/message.dart';
import '../../domain/message_role.dart';
import '../../domain/outgoing_message.dart';

final class SimulatorConversationGateway implements ConversationGateway {
  const SimulatorConversationGateway();

  @override
  Future<GatewayResult<Conversation?>> getConversation() async {
    return const GatewaySuccess<Conversation?>(null);
  }

  @override
  Future<GatewayResult<MessagePage>> getMessages({
    int limit = 50,
    String? cursor,
  }) async {
    return GatewaySuccess<MessagePage>(
      MessagePage(
        messages: const [],
        nextCursor: null,
        hasMore: false,
      ),
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