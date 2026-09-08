import 'package:flutter_test/flutter_test.dart';

import 'package:alice/conversation/domain/conversation.dart';

void main() {
  final createdAt = DateTime.utc(2026, 1, 1);
  final updatedAt = DateTime.utc(2026, 1, 2);

  test('retains fields and compares by value', () {
    final conversation = Conversation(
      id: 'conversation-1',
      createdAt: createdAt,
      updatedAt: updatedAt,
    );

    expect(conversation.id, 'conversation-1');
    expect(conversation.createdAt, createdAt);
    expect(conversation.updatedAt, updatedAt);
    expect(
      conversation,
      Conversation(
        id: 'conversation-1',
        createdAt: createdAt,
        updatedAt: updatedAt,
      ),
    );
    expect(
      conversation.hashCode,
      Conversation(
        id: 'conversation-1',
        createdAt: createdAt,
        updatedAt: updatedAt,
      ).hashCode,
    );
  });

  test('toString contains no unexpected content', () {
    expect(conversation.toString(), isNot(contains('secret')));
  });
}

final conversation = Conversation(
  id: 'conversation-1',
  createdAt: DateTime.utc(2026, 1, 1),
  updatedAt: DateTime.utc(2026, 1, 2),
);
