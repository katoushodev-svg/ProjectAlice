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
  });

  final ConversationFailureCategory category;
  final ConversationOperation operation;
  final SendResultCertainty? resultCertainty;
  final String? requestId;

  @override
  bool operator ==(Object other) {
    return other is ConversationFailure &&
        other.category == category &&
        other.operation == operation &&
        other.resultCertainty == resultCertainty &&
        other.requestId == requestId;
  }

  @override
  int get hashCode =>
      Object.hash(category, operation, resultCertainty, requestId);

  @override
  String toString() => 'ConversationFailure($category, $operation)';
}
