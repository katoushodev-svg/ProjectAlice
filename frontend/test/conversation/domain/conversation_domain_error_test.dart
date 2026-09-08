import 'package:flutter_test/flutter_test.dart';

import 'package:alice/conversation/domain/conversation_domain_error.dart';

void main() {
  test('compares errors by safe code only', () {
    const first = ConversationDomainError(
      ConversationDomainErrorCode.emptyContent,
    );
    const second = ConversationDomainError(
      ConversationDomainErrorCode.emptyContent,
    );

    expect(first, second);
    expect(first.hashCode, second.hashCode);
    expect(first.toString(), contains('emptyContent'));
  });
}
