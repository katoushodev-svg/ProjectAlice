import 'package:flutter_test/flutter_test.dart';

import 'package:alice/conversation/domain/conversation_domain_error.dart';
import 'package:alice/conversation/domain/domain_result.dart';
import 'package:alice/conversation/domain/outgoing_message.dart';

void main() {
  const validKey = '123e4567-e89b-12d3-a456-426614174000';

  test('preserves valid content and key', () {
    final result = OutgoingMessage.create(
      idempotencyKey: validKey,
      content: '  hello\nworld  ',
    );

    expect(result, isA<DomainSuccess<OutgoingMessage>>());
    final message = (result as DomainSuccess<OutgoingMessage>).value;
    expect(message.content, '  hello\nworld  ');
    expect(message.idempotencyKey, validKey);
  });

  test('counts Unicode code points and accepts exactly the limit', () {
    final result = OutgoingMessage.create(
      idempotencyKey: validKey,
      content: String.fromCharCodes(List.filled(10000, 0x1f600)),
    );

    expect(result, isA<DomainSuccess<OutgoingMessage>>());
  });

  test('rejects empty, whitespace-only, too-long, and invalid key input', () {
    expectError('', ConversationDomainErrorCode.emptyContent);
    expectError(' \t\n', ConversationDomainErrorCode.whitespaceOnlyContent);
    expectError(
      String.fromCharCodes(List.filled(10001, 0x1f600)),
      ConversationDomainErrorCode.contentTooLong,
    );
    expectInvalidKey(ConversationDomainErrorCode.invalidIdempotencyKey);
  });

  test('does not expose content or key in failure or string output', () {
    const secretContent = 'private-content';
    const secretKey = 'invalid-private-key';
    final result = OutgoingMessage.create(
      idempotencyKey: secretKey,
      content: secretContent,
    );

    expect(result, isA<DomainFailure<OutgoingMessage>>());
    expect(result.toString(), isNot(contains(secretContent)));
    expect(result.toString(), isNot(contains(secretKey)));
  });
}

void expectError(String content, ConversationDomainErrorCode expectedCode) {
  final result = OutgoingMessage.create(
    idempotencyKey: '123e4567-e89b-12d3-a456-426614174000',
    content: content,
  );

  expect(result, isA<DomainFailure<OutgoingMessage>>());
  expect((result as DomainFailure<OutgoingMessage>).error.code, expectedCode);
}

void expectInvalidKey(ConversationDomainErrorCode expectedCode) {
  final result = OutgoingMessage.create(
    idempotencyKey: 'not-a-uuid',
    content: 'valid content',
  );

  expect(result, isA<DomainFailure<OutgoingMessage>>());
  expect((result as DomainFailure<OutgoingMessage>).error.code, expectedCode);
}
