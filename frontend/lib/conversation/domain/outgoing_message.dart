import 'conversation_domain_error.dart';
import 'domain_result.dart';

final class OutgoingMessage {
  const OutgoingMessage._({
    required this.idempotencyKey,
    required this.content,
  });

  static const int maxContentCodePoints = 10000;

  final String idempotencyKey;
  final String content;

  static DomainResult<OutgoingMessage> create({
    required String idempotencyKey,
    required String content,
  }) {
    if (content.isEmpty) {
      return const DomainFailure(
        ConversationDomainError(ConversationDomainErrorCode.emptyContent),
      );
    }
    if (content.trim().isEmpty) {
      return const DomainFailure(
        ConversationDomainError(
          ConversationDomainErrorCode.whitespaceOnlyContent,
        ),
      );
    }
    if (content.runes.length > maxContentCodePoints) {
      return const DomainFailure(
        ConversationDomainError(ConversationDomainErrorCode.contentTooLong),
      );
    }
    if (!_uuidPattern.hasMatch(idempotencyKey)) {
      return const DomainFailure(
        ConversationDomainError(
          ConversationDomainErrorCode.invalidIdempotencyKey,
        ),
      );
    }

    return DomainSuccess(
      OutgoingMessage._(idempotencyKey: idempotencyKey, content: content),
    );
  }

  static final RegExp _uuidPattern = RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
  );

  @override
  bool operator ==(Object other) {
    return other is OutgoingMessage &&
        other.idempotencyKey == idempotencyKey &&
        other.content == content;
  }

  @override
  int get hashCode => Object.hash(idempotencyKey, content);

  @override
  String toString() => 'OutgoingMessage';
}
