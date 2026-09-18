import '../../domain/message.dart';
import '../model/conversation_message_view_data.dart';

final class ConversationMessageViewMapper {
  const ConversationMessageViewMapper();

  List<ConversationMessageViewData> mapAll(List<Message> messages) {
    return messages.map(map).toList(growable: false);
  }

  ConversationMessageViewData map(Message message) {
    final jst = message.createdAt.toUtc().add(const Duration(hours: 9));
    return ConversationMessageViewData(
      messageId: message.id,
      role: message.role,
      content: message.content,
      createdAt: message.createdAt,
      jstCalendarDate: DateTime.utc(jst.year, jst.month, jst.day),
    );
  }
}
