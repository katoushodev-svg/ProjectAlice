import 'package:uuid/uuid.dart';

abstract interface class IdempotencyKeyGenerator {
  String generate();
}

final class UuidV4IdempotencyKeyGenerator implements IdempotencyKeyGenerator {
  UuidV4IdempotencyKeyGenerator({Uuid? uuid}) : _uuid = uuid ?? Uuid();

  final Uuid _uuid;

  @override
  String generate() => _uuid.v4();
}
