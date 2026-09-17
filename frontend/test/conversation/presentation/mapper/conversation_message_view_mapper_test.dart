import 'package:alice/conversation/domain/message.dart';
import 'package:alice/conversation/domain/message_role.dart';
import 'package:alice/conversation/presentation/mapper/conversation_message_view_mapper.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const mapper = ConversationMessageViewMapper();

  test('maps canonical messages without changing their order or content', () {
    final messages = [
      Message(
        id: 'one',
        role: MessageRole.user,
        content: '  raw  ',
        createdAt: DateTime.utc(2026, 8, 19),
      ),
      Message(
        id: 'two',
        role: MessageRole.assistant,
        content: 'reply',
        createdAt: DateTime.utc(2026, 8, 19, 1),
      ),
    ];

    final data = mapper.mapAll(messages);

    expect(data.map((value) => value.messageId), ['one', 'two']);
    expect(data.first.content, '  raw  ');
    expect(data.last.role, MessageRole.assistant);
  });

  test('uses fixed JST calendar dates at the UTC boundary', () {
    final beforeBoundary = mapper.map(
      Message(
        id: 'before',
        role: MessageRole.user,
        content: 'fixture',
        createdAt: DateTime.utc(2026, 8, 18, 14, 59, 59),
      ),
    );
    final atBoundary = mapper.map(
      Message(
        id: 'at',
        role: MessageRole.user,
        content: 'fixture',
        createdAt: DateTime.utc(2026, 8, 18, 15),
      ),
    );

    expect(beforeBoundary.jstCalendarDate, DateTime.utc(2026, 8, 18));
    expect(atBoundary.jstCalendarDate, DateTime.utc(2026, 8, 19));
  });
}
