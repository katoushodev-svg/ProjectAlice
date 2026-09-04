import 'package:flutter_test/flutter_test.dart';
import 'package:project_alice/conversation/domain/entities/message.dart';
import 'package:project_alice/conversation/domain/value_objects/message_role.dart';

void main() {
  group('Message', () {
    final String id = '1';
    final MessageRole role = MessageRole.user;
    final String content = 'Hello, World!';
    final DateTime createdAt = DateTime.now();

    test('should hold the provided values', () {
      final message = Message(id: id, role: role, content: content, createdAt: createdAt);

      expect(message.id, id);
      expect(message.role, role);
      expect(message.content, content);
      expect(message.createdAt, createdAt);
    });

    test('should be equal for the same values', () {
      final message1 = Message(id: id, role: role, content: content, createdAt: createdAt);
      final message2 = Message(id: id, role: role, content: content, createdAt: createdAt);

      expect(message1, message2);
      expect(message1.hashCode, message2.hashCode);
    });

    test('should not be equal for different values', () {
      final message1 = Message(id: id, role: role, content: content, createdAt: createdAt);
      final message2 = Message(id: '2', role: MessageRole.assistant, content: 'Goodbye!', createdAt: createdAt);

      expect(message1, isNot(equals(message2)));
    });

    test('should have a consistent toString()', () {
      final message = Message(id: id, role: role, content: content, createdAt: createdAt);
      expect(message.toString(), 'Message(id: $id, role: $role, content: $content, createdAt: $createdAt)');
    });
  });
}