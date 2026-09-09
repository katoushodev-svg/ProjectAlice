import '../../../domain/conversation.dart';
import '../../../domain/message.dart';
import '../../../domain/message_role.dart';
import '../dto/conversation_dto.dart';
import '../dto/message_dto.dart';
import '../parsing/api_contract_exception.dart';
import '../parsing/api_timestamp_parser.dart';

class ConversationApiMapper {
  ConversationApiMapper._();

  static Conversation toConversation(ConversationDto dto) {
    return Conversation(
      id: dto.id,
      createdAt: ApiTimestampParser.parse(dto.createdAt),
      updatedAt: ApiTimestampParser.parse(dto.updatedAt),
    );
  }

  static Message toMessage(MessageDto dto) {
    return Message(
      id: dto.id,
      role: _parseRole(dto.role),
      content: dto.content,
      createdAt: ApiTimestampParser.parse(dto.createdAt),
    );
  }

  static MessageRole _parseRole(String rawRole) {
    switch (rawRole) {
      case 'user':
        return MessageRole.user;
      case 'assistant':
        return MessageRole.assistant;
      default:
        throw ApiContractException(category: 'unknownMessageRole');
    }
  }
}
