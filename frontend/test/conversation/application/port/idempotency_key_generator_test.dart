import 'package:flutter_test/flutter_test.dart';

import 'package:alice/conversation/application/port/idempotency_key_generator.dart';
import 'package:alice/conversation/domain/domain_result.dart';
import 'package:alice/conversation/domain/outgoing_message.dart';

void main() {
  test('generates a UUID v4 accepted by OutgoingMessage', () {
    final generator = UuidV4IdempotencyKeyGenerator();
    final key = generator.generate();

    final message = OutgoingMessage.create(
      idempotencyKey: key,
      content: 'Hello',
    );

    expect(message, isA<DomainSuccess<OutgoingMessage>>());
    expect(
      key,
      matches(
        RegExp(
          r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
        ),
      ),
    );
  });

  test('can be replaced with a fixed key for application tests', () {
    const key = '123e4567-e89b-42d3-a456-426614174000';
    final generator = _FixedIdempotencyKeyGenerator(key);

    expect(generator.generate(), key);
  });
}

final class _FixedIdempotencyKeyGenerator implements IdempotencyKeyGenerator {
  const _FixedIdempotencyKeyGenerator(this.key);

  final String key;

  @override
  String generate() => key;
}
