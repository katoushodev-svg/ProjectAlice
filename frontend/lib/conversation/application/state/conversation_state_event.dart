import '../../domain/conversation.dart';
import '../../domain/message.dart';
import '../../domain/outgoing_message.dart';
import '../error/conversation_failure.dart';
import '../model/message_page.dart';

sealed class ConversationStateEvent {
  const ConversationStateEvent();
}

final class InitialLoadStarted extends ConversationStateEvent {
  const InitialLoadStarted();
}

final class InitialLoadSucceeded extends ConversationStateEvent {
  const InitialLoadSucceeded({required this.conversation, required this.page});

  final Conversation? conversation;
  final MessagePage page;
}

final class InitialLoadFailed extends ConversationStateEvent {
  const InitialLoadFailed(this.failure);

  final ConversationFailure failure;
}

final class OlderPageLoadStarted extends ConversationStateEvent {
  const OlderPageLoadStarted(this.requestedCursor);

  final String requestedCursor;
}

final class OlderPageLoadSucceeded extends ConversationStateEvent {
  const OlderPageLoadSucceeded({
    required this.requestedCursor,
    required this.page,
  });

  final String requestedCursor;
  final MessagePage page;
}

final class OlderPageLoadFailed extends ConversationStateEvent {
  const OlderPageLoadFailed({
    required this.requestedCursor,
    required this.failure,
  });

  final String requestedCursor;
  final ConversationFailure failure;
}

final class SendStarted extends ConversationStateEvent {
  const SendStarted(this.outgoingMessage);

  final OutgoingMessage outgoingMessage;
}

final class StreamStarted extends ConversationStateEvent {
  const StreamStarted(this.requestId);

  final String requestId;
}

final class AssistantDeltaReceived extends ConversationStateEvent {
  const AssistantDeltaReceived({required this.requestId, required this.delta});

  final String requestId;
  final String delta;
}

final class AssistantCompleted extends ConversationStateEvent {
  const AssistantCompleted({required this.requestId, required this.messages});

  final String requestId;
  final List<Message> messages;
}

final class SendFailed extends ConversationStateEvent {
  const SendFailed(this.failure);

  final ConversationFailure failure;
}

final class SendResultUnknown extends ConversationStateEvent {
  const SendResultUnknown(this.failure);

  final ConversationFailure failure;
}

final class FailureDismissed extends ConversationStateEvent {
  const FailureDismissed();
}

final class HistoryReconciliationStarted extends ConversationStateEvent {
  const HistoryReconciliationStarted();
}

enum ReconciliationSendResult { confirmedCompleted, notConfirmed }

final class HistoryReconciliationSucceeded extends ConversationStateEvent {
  const HistoryReconciliationSucceeded({
    required this.canonicalMessages,
    required this.sendResult,
  });

  final List<Message> canonicalMessages;
  final ReconciliationSendResult sendResult;
}

final class HistoryReconciliationFailed extends ConversationStateEvent {
  const HistoryReconciliationFailed(this.failure);

  final ConversationFailure failure;
}
