import '../../domain/message.dart';
import '../error/conversation_failure.dart';

sealed class ConversationSendEvent {
  const ConversationSendEvent();
}

final class StreamStarted extends ConversationSendEvent {
  const StreamStarted(this.requestId, {this.userMessage});

  final String requestId;
  final Message? userMessage;
}

final class AssistantDelta extends ConversationSendEvent {
  const AssistantDelta({required this.requestId, required this.delta});

  final String requestId;
  final String delta;
}

final class AssistantCompleted extends ConversationSendEvent {
  const AssistantCompleted({
    required this.requestId,
    required this.messages,
    this.userMessage,
    this.assistantMessage,
  });

  final String requestId;
  final List<Message> messages;
  final Message? userMessage;
  final Message? assistantMessage;
}

final class SendFailed extends ConversationSendEvent {
  const SendFailed({this.requestId, required this.failure});

  final String? requestId;
  final ConversationFailure failure;
}
