import 'package:flutter_test/flutter_test.dart';
import 'package:project_alice/conversation/domain/value_objects/conversation_id.dart';

void main() {
  group('ConversationId', () {
    test('should create a valid ConversationId', () {
      final conversationId = ConversationId('123e4567-e89b-12d3-a456-426614174000');
      expect(conversationId.value, '123e4567-e89b-12d3-a456-426614174000');
    });

    test('should be equal for the same value', () {
      final conversationId1 = ConversationId('123e4567-e89b-12d3-a456-426614174000');
      final conversationId2 = ConversationId('123e4567-e89b-12d3-a456-426614174000');
      expect(conversationId1, conversationId2);
    });

    test('should not be equal for different values', () {
      final conversationId1 = ConversationId('123e4567-e89b-12d3-a456-426614174000');
      final conversationId2 = ConversationId('123e4567-e89b-12d3-a456-426614174001');
      expect(conversationId1, isNot(equals(conversationId2)));
    });

    test('should have consistent hashCode', () {
      final conversationId1 = ConversationId('123e4567-e89b-12d3-a456-426614174000');
      final conversationId2 = ConversationId('123e4567-e89b-12d3-a456-426614174000');
      expect(conversationId1.hashCode, conversationId2.hashCode);
    });

    test('should have a correct toString representation', () {
      final conversationId = ConversationId('123e4567-e89b-12d3-a456-426614174000');
      expect(conversationId.toString(), 'ConversationId(value: 123e4567-e89b-12d3-a456-426614174000)');
    });
  });
}