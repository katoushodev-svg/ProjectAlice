import 'package:flutter_test/flutter_test.dart';
import 'package:project_alice/conversation/domain/entities/conversation.dart';

void main() {
  group('Conversation', () {
    final String testId = '12345';
    final DateTime testCreatedAt = DateTime.now();
    final DateTime testUpdatedAt = DateTime.now();

    test('should hold the correct id', () {
      final conversation = Conversation(id: testId, createdAt: testCreatedAt, updatedAt: testUpdatedAt);
      expect(conversation.id, testId);
    });

    test('should hold the correct createdAt', () {
      final conversation = Conversation(id: testId, createdAt: testCreatedAt, updatedAt: testUpdatedAt);
      expect(conversation.createdAt, testCreatedAt);
    });

    test('should hold the correct updatedAt', () {
      final conversation = Conversation(id: testId, createdAt: testCreatedAt, updatedAt: testUpdatedAt);
      expect(conversation.updatedAt, testUpdatedAt);
    });

    test('should be equal for the same values', () {
      final conversation1 = Conversation(id: testId, createdAt: testCreatedAt, updatedAt: testUpdatedAt);
      final conversation2 = Conversation(id: testId, createdAt: testCreatedAt, updatedAt: testUpdatedAt);
      expect(conversation1, conversation2);
    });

    test('should not be equal for different values', () {
      final conversation1 = Conversation(id: testId, createdAt: testCreatedAt, updatedAt: testUpdatedAt);
      final conversation2 = Conversation(id: '54321', createdAt: testCreatedAt, updatedAt: testUpdatedAt);
      expect(conversation1, isNot(equals(conversation2)));
    });

    test('should have consistent hashCode', () {
      final conversation1 = Conversation(id: testId, createdAt: testCreatedAt, updatedAt: testUpdatedAt);
      final conversation2 = Conversation(id: testId, createdAt: testCreatedAt, updatedAt: testUpdatedAt);
      expect(conversation1.hashCode, conversation2.hashCode);
    });

    test('should have a consistent toString()', () {
      final conversation = Conversation(id: testId, createdAt: testCreatedAt, updatedAt: testUpdatedAt);
      expect(conversation.toString(), 'Conversation(id: $testId, createdAt: $testCreatedAt, updatedAt: $testUpdatedAt)');
    });
  });
}