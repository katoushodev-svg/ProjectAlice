import 'package:flutter_test/flutter_test.dart';
import 'package:project_alice/conversation/domain/value_objects/message_content.dart';

void main() {
  group('MessageContent', () {
    test('should create a valid MessageContent', () {
      const content = 'Hello, World!';
      final messageContent = MessageContent(content);

      expect(messageContent.value, content);
    });

    test('should throw an error for empty content', () {
      expect(() => MessageContent(''), throwsA(isA<ArgumentError>()));
    });

    test('should throw an error for whitespace-only content', () {
      expect(() => MessageContent('   '), throwsA(isA<ArgumentError>()));
    });

    test('should throw an error for content exceeding length limit', () {
      const longContent = 'a' * 10001; // Assuming the limit is 10000
      expect(() => MessageContent(longContent), throwsA(isA<ArgumentError>()));
    });

    test('should be equal for same content', () {
      final messageContent1 = MessageContent('Hello');
      final messageContent2 = MessageContent('Hello');

      expect(messageContent1, messageContent2);
    });

    test('should not be equal for different content', () {
      final messageContent1 = MessageContent('Hello');
      final messageContent2 = MessageContent('World');

      expect(messageContent1, isNot(equals(messageContent2)));
    });

    test('should have consistent hashCode', () {
      final messageContent1 = MessageContent('Hello');
      final messageContent2 = MessageContent('Hello');

      expect(messageContent1.hashCode, messageContent2.hashCode);
    });
  });
}