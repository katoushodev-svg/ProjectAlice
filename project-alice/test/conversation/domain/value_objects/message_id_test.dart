import 'package:flutter_test/flutter_test.dart';
import 'package:project_alice/conversation/domain/value_objects/message_id.dart';

void main() {
  group('MessageId', () {
    test('should create a valid MessageId', () {
      final messageId = MessageId('12345');
      expect(messageId.value, '12345');
    });

    test('should be equal for the same value', () {
      final messageId1 = MessageId('12345');
      final messageId2 = MessageId('12345');
      expect(messageId1, messageId2);
    });

    test('should not be equal for different values', () {
      final messageId1 = MessageId('12345');
      final messageId2 = MessageId('67890');
      expect(messageId1, isNot(equals(messageId2)));
    });

    test('should have consistent hashCode', () {
      final messageId1 = MessageId('12345');
      final messageId2 = MessageId('12345');
      expect(messageId1.hashCode, messageId2.hashCode);
    });

    test('should throw an error for empty value', () {
      expect(() => MessageId(''), throwsA(isA<AssertionError>()));
    });
  });
}