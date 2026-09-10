import 'package:flutter_test/flutter_test.dart';

import 'package:alice/conversation/application/model/message_page.dart';
import 'package:alice/conversation/domain/message.dart';
import 'package:alice/conversation/domain/message_role.dart';

void main() {
  final message = Message(
    id: 'message-1',
    role: MessageRole.user,
    content: 'hello',
    createdAt: DateTime.utc(2026, 1, 1),
  );

  test('copies messages and preserves an opaque cursor', () {
    final source = [message];
    final page = MessagePage(
      messages: source,
      nextCursor: 'opaque-cursor',
      hasMore: true,
    );

    source.clear();

    expect(page.messages, [message]);
    expect(() => page.messages.add(message), throwsUnsupportedError);
    expect(page.nextCursor, 'opaque-cursor');
  });

  test('rejects invalid pagination combinations', () {
    expect(
      () => MessagePage(messages: const [], nextCursor: null, hasMore: true),
      throwsArgumentError,
    );
    expect(
      () => MessagePage(
        messages: const [],
        nextCursor: 'unexpected',
        hasMore: false,
      ),
      throwsArgumentError,
    );
  });
}
