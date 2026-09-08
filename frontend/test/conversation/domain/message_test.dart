import 'package:flutter_test/flutter_test.dart';

import 'package:alice/conversation/domain/message.dart';
import 'package:alice/conversation/domain/message_role.dart';

void main() {
  final createdAt = DateTime.utc(2026, 1, 1);
  const content = '  **hello**\n  code();  ';

  test('supports both Phase 1 roles and preserves content', () {
    expect(MessageRole.values, [MessageRole.user, MessageRole.assistant]);

    final message = Message(
      id: 'message-1',
      role: MessageRole.user,
      content: content,
      createdAt: createdAt,
    );

    expect(message.content, content);
    expect(
      message,
      Message(
        id: 'message-1',
        role: MessageRole.user,
        content: content,
        createdAt: createdAt,
      ),
    );
    expect(message.toString(), isNot(contains(content)));
  });
}
