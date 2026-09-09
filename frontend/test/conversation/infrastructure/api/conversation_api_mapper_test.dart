import 'package:flutter_test/flutter_test.dart';

import 'package:alice/conversation/domain/conversation.dart';
import 'package:alice/conversation/domain/message.dart';
import 'package:alice/conversation/domain/message_role.dart';
import 'package:alice/conversation/infrastructure/api/dto/conversation_dto.dart';
import 'package:alice/conversation/infrastructure/api/dto/message_dto.dart';
import 'package:alice/conversation/infrastructure/api/mapping/conversation_api_mapper.dart';

void main() {
  group('ConversationApiMapper', () {
    test('maps conversation dto to domain model', () {
      const dto = ConversationDto(
        id: 'conversation-1',
        createdAt: '2026-08-14T15:00:02.000+09:00',
        updatedAt: '2026-08-14T15:05:02.000+09:00',
      );

      final conversation = ConversationApiMapper.toConversation(dto);

      expect(
        conversation,
        Conversation(
          id: 'conversation-1',
          createdAt: DateTime.parse('2026-08-14T06:00:02.000Z'),
          updatedAt: DateTime.parse('2026-08-14T06:05:02.000Z'),
        ),
      );
    });

    test('maps message dto to domain model', () {
      const dto = MessageDto(
        id: 'message-1',
        role: 'assistant',
        content: 'hello',
        createdAt: '2026-08-14T15:00:02.000+09:00',
      );

      final message = ConversationApiMapper.toMessage(dto);

      expect(
        message,
        Message(
          id: 'message-1',
          role: MessageRole.assistant,
          content: 'hello',
          createdAt: DateTime.parse('2026-08-14T06:00:02.000Z'),
        ),
      );
    });
  });
}
