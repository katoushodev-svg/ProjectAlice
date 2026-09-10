import '../../domain/conversation.dart';
import '../../domain/outgoing_message.dart';
import '../error/conversation_failure.dart';
import '../model/conversation_send_event.dart';
import '../model/message_page.dart';

sealed class GatewayResult<T> {
  const GatewayResult();
}

final class GatewaySuccess<T> extends GatewayResult<T> {
  const GatewaySuccess(this.value);

  final T value;
}

final class GatewayFailure<T> extends GatewayResult<T> {
  const GatewayFailure(this.failure);

  final ConversationFailure failure;
}

abstract interface class ConversationGateway {
  Future<GatewayResult<Conversation?>> getConversation();

  Future<GatewayResult<MessagePage>> getMessages({
    int limit = 50,
    String? cursor,
  });

  Stream<ConversationSendEvent> sendMessage(OutgoingMessage outgoingMessage);
}
