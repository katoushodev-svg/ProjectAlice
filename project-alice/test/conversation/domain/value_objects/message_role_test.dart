import 'package:flutter_test/flutter_test.dart';
import 'package:project_alice/conversation/domain/value_objects/message_role.dart';

void main() {
  group('MessageRole', () {
    test('MessageRole.user should have the correct value', () {
      expect(MessageRole.user.toString(), 'MessageRole.user');
    });

    test('MessageRole.assistant should have the correct value', () {
      expect(MessageRole.assistant.toString(), 'MessageRole.assistant');
    });

    test('MessageRole values should be equal to their string representation', () {
      expect(MessageRole.values[0].toString(), 'MessageRole.user');
      expect(MessageRole.values[1].toString(), 'MessageRole.assistant');
    });

    test('MessageRole values should not be equal', () {
      expect(MessageRole.user, isNot(equals(MessageRole.assistant)));
    });
  });
}