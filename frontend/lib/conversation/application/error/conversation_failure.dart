enum ConversationFailureCategory {
  validation,
  invalidCursor,
  conversationBusy,
  requestInProgress,
  idempotencyConflict,
  generationFailed,
  responseTimeout,
  messageSaveFailed,
  requestInterrupted,
  networkUnavailable,
  resultUnknown,
  protocolViolation,
  responseTooLarge,
  requestBodyTooLarge,
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
    this.terminalStreamFailure = false,
  });

  final ConversationFailureCategory category;
  final ConversationOperation operation;
  final SendResultCertainty? resultCertainty;
  final String? requestId;
  final Duration? retryAfter;
  final bool terminalStreamFailure;

  @override
  bool operator ==(Object other) {
    return other is ConversationFailure &&
        other.category == category &&
        other.operation == operation &&
        other.resultCertainty == resultCertainty &&
        other.requestId == requestId &&
        other.retryAfter == retryAfter &&
        other.terminalStreamFailure == terminalStreamFailure;
  }

  @override
  int get hashCode => Object.hash(
    category,
    operation,
    resultCertainty,
    requestId,
    retryAfter,
    terminalStreamFailure,
  );

  @override
  String toString() => 'ConversationFailure($category, $operation)';
}
