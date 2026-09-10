enum ConversationFailureCategory {
  validation,
  conversationBusy,
  requestInProgress,
  idempotencyConflict,
  generationFailed,
  responseTimeout,
  networkUnavailable,
  resultUnknown,
  protocolViolation,
  responseTooLarge,
  sseFrameTooLarge,
  sseStreamTooLarge,
  assistantContentTooLong,
  tooManySseEvents,
  serverFailure,
  unknown,
}

enum ConversationOperation { initialLoad, pagination, send, reconciliation }

enum SendResultCertainty { knownFailed, resultUnknown }

final class ConversationFailure {
  ConversationFailure({
    required this.category,
    required this.operation,
    this.resultCertainty,
    this.requestId,
    this.retryAfter,
  }) {
    final validRetryAfter =
        category == ConversationFailureCategory.requestInProgress
        ? retryAfter != null
        : retryAfter == null;
    if (!validRetryAfter) {
      throw ArgumentError(
        'retryAfter is only valid for requestInProgress failures.',
      );
    }
  }

  final ConversationFailureCategory category;
  final ConversationOperation operation;
  final SendResultCertainty? resultCertainty;
  final String? requestId;
  final Duration? retryAfter;

  @override
  bool operator ==(Object other) {
    return other is ConversationFailure &&
        other.category == category &&
        other.operation == operation &&
        other.resultCertainty == resultCertainty &&
        other.requestId == requestId &&
        other.retryAfter == retryAfter;
  }

  @override
  int get hashCode =>
      Object.hash(category, operation, resultCertainty, requestId, retryAfter);

  @override
  String toString() => 'ConversationFailure($category, $operation)';
}
