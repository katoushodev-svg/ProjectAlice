import '../../domain/message_role.dart';

enum ConversationMessagePresentationState { canonical }

final class ConversationMessageViewData {
  const ConversationMessageViewData({
    required this.messageId,
    required this.role,
    required this.content,
    required this.createdAt,
    required this.jstCalendarDate,
    this.presentationState = ConversationMessagePresentationState.canonical,
  });

  final String messageId;
  final MessageRole role;
  final String content;
  final DateTime createdAt;
  final DateTime jstCalendarDate;
  final ConversationMessagePresentationState presentationState;
}
