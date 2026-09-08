enum ConversationDomainErrorCode {
  emptyContent,
  whitespaceOnlyContent,
  contentTooLong,
  invalidIdempotencyKey,
}

final class ConversationDomainError {
  const ConversationDomainError(this.code);

  final ConversationDomainErrorCode code;

  @override
  bool operator ==(Object other) {
    return other is ConversationDomainError && other.code == code;
  }

  @override
  int get hashCode => code.hashCode;

  @override
  String toString() => 'ConversationDomainError(code: $code)';
}
