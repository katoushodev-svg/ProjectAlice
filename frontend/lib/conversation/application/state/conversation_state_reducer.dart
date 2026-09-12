import '../../domain/message.dart';
import '../../domain/message_role.dart';
import '../error/conversation_failure.dart';
import 'conversation_screen_state.dart';
import 'conversation_state_event.dart';

sealed class ConversationStateReducerResult {
  const ConversationStateReducerResult();
}

final class StateTransition extends ConversationStateReducerResult {
  const StateTransition(this.nextState);

  final ConversationScreenState nextState;
}

final class StateTransitionFailure extends ConversationStateReducerResult {
  const StateTransitionFailure({
    required this.category,
    required this.operation,
  });

  final ConversationFailureCategory category;
  final ConversationOperation operation;
}

final class ConversationStateReducer {
  const ConversationStateReducer();

  ConversationStateReducerResult reduce(
    ConversationScreenState state,
    ConversationStateEvent event,
  ) {
    if (event is InitialLoadStarted) {
      if (state.status == ConversationScreenStatus.sending ||
          state.status == ConversationScreenStatus.streaming ||
          state.status == ConversationScreenStatus.loadingOlder) {
        return _invalid(ConversationOperation.initialLoad);
      }
      return StateTransition(
        state.status == ConversationScreenStatus.initialLoading
            ? state
            : ConversationScreenState.initial(),
      );
    }
    if (event is InitialLoadSucceeded) {
      if (state.status != ConversationScreenStatus.initialLoading) {
        return _invalid(ConversationOperation.initialLoad);
      }
      final pageResult = _initialMessages(event.page.messages);
      if (pageResult == null) {
        return _protocol(ConversationOperation.initialLoad);
      }
      return StateTransition(
        ConversationScreenState.fromReducer(
          status: ConversationScreenStatus.ready,
          conversation: event.conversation,
          messages: event.page.messages,
          pagination: ConversationPaginationState(
            hasMore: event.page.hasMore,
            nextCursor: event.page.nextCursor,
          ),
        ),
      );
    }
    if (event is InitialLoadFailed) {
      if (state.status != ConversationScreenStatus.initialLoading ||
          event.failure.operation != ConversationOperation.initialLoad) {
        return _invalid(ConversationOperation.initialLoad);
      }
      return StateTransition(
        ConversationScreenState.fromReducer(
          status: ConversationScreenStatus.initialLoadFailed,
          failure: event.failure,
        ),
      );
    }
    if (event is OlderPageLoadStarted) return _startOlder(state, event);
    if (event is OlderPageLoadSucceeded) return _completeOlder(state, event);
    if (event is OlderPageLoadFailed) return _failOlder(state, event);
    if (event is SendStarted) {
      if (state.status != ConversationScreenStatus.ready) {
        return _invalid(ConversationOperation.send);
      }
      return StateTransition(
        ConversationScreenState.fromReducer(
          status: ConversationScreenStatus.sending,
          conversation: state.conversation,
          messages: state.messages,
          pagination: state.pagination,
          pendingSend: event.outgoingMessage,
        ),
      );
    }
    if (event is StreamStarted) return _startStream(state, event);
    if (event is AssistantDeltaReceived) return _delta(state, event);
    if (event is AssistantCompleted) return _complete(state, event);
    if (event is SendFailed) return _sendFailed(state, event.failure);
    if (event is SendResultUnknown) {
      if (event.failure.resultCertainty != SendResultCertainty.resultUnknown) {
        return _protocol(ConversationOperation.send);
      }
      return _sendFailed(state, event.failure, retainPending: true);
    }
    if (event is FailureDismissed) return _dismiss(state);
    if (event is HistoryReconciliationStarted) return _reconcileStart(state);
    if (event is HistoryReconciliationSucceeded) {
      return _reconcileSuccess(state, event);
    }
    if (event is HistoryReconciliationFailed) {
      return _reconcileFailure(state, event);
    }
    return _protocol(ConversationOperation.reconciliation);
  }

  ConversationStateReducerResult _startOlder(
    ConversationScreenState state,
    OlderPageLoadStarted event,
  ) {
    if (state.status != ConversationScreenStatus.ready ||
        !state.pagination.hasMore ||
        event.requestedCursor != state.pagination.nextCursor) {
      return _invalid(ConversationOperation.pagination);
    }
    return StateTransition(
      ConversationScreenState.fromReducer(
        status: ConversationScreenStatus.loadingOlder,
        conversation: state.conversation,
        messages: state.messages,
        pagination: state.pagination,
      ),
    );
  }

  ConversationStateReducerResult _completeOlder(
    ConversationScreenState state,
    OlderPageLoadSucceeded event,
  ) {
    if (state.status != ConversationScreenStatus.loadingOlder ||
        event.requestedCursor != state.pagination.nextCursor) {
      return _invalid(ConversationOperation.pagination);
    }
    final merged = _mergeMessages(event.page.messages, state.messages);
    if (merged == null) return _protocol(ConversationOperation.pagination);
    return StateTransition(
      ConversationScreenState.fromReducer(
        status: ConversationScreenStatus.ready,
        conversation: state.conversation,
        messages: merged,
        pagination: ConversationPaginationState(
          hasMore: event.page.hasMore,
          nextCursor: event.page.nextCursor,
        ),
      ),
    );
  }

  ConversationStateReducerResult _failOlder(
    ConversationScreenState state,
    OlderPageLoadFailed event,
  ) {
    if (state.status != ConversationScreenStatus.loadingOlder ||
        event.requestedCursor != state.pagination.nextCursor ||
        event.failure.operation != ConversationOperation.pagination) {
      return _invalid(ConversationOperation.pagination);
    }
    return StateTransition(
      ConversationScreenState.fromReducer(
        status: ConversationScreenStatus.ready,
        conversation: state.conversation,
        messages: state.messages,
        pagination: state.pagination,
        failure: event.failure,
      ),
    );
  }

  ConversationStateReducerResult _startStream(
    ConversationScreenState state,
    StreamStarted event,
  ) {
    if (state.status != ConversationScreenStatus.sending ||
        event.requestId.isEmpty) {
      return _invalid(ConversationOperation.send);
    }
    return StateTransition(
      ConversationScreenState.fromReducer(
        status: ConversationScreenStatus.streaming,
        conversation: state.conversation,
        messages: state.messages,
        pagination: state.pagination,
        pendingSend: state.pendingSend,
        temporaryAssistantText: '',
        activeRequestId: event.requestId,
      ),
    );
  }

  ConversationStateReducerResult _delta(
    ConversationScreenState state,
    AssistantDeltaReceived event,
  ) {
    if (!_matchesRequest(state, event.requestId)) {
      return _protocol(ConversationOperation.send);
    }
    final text = '${state.temporaryAssistantText!}${event.delta}';
    if (text.runes.length > 50000) {
      return StateTransition(
        ConversationScreenState.fromReducer(
          status: ConversationScreenStatus.sendFailed,
          conversation: state.conversation,
          messages: state.messages,
          pagination: state.pagination,
          pendingSend: state.pendingSend,
          temporaryAssistantText: state.temporaryAssistantText,
          failure: ConversationFailure(
            category: ConversationFailureCategory.assistantContentTooLong,
            operation: ConversationOperation.send,
            resultCertainty: SendResultCertainty.knownFailed,
          ),
        ),
      );
    }
    return StateTransition(
      ConversationScreenState.fromReducer(
        status: ConversationScreenStatus.streaming,
        conversation: state.conversation,
        messages: state.messages,
        pagination: state.pagination,
        pendingSend: state.pendingSend,
        temporaryAssistantText: text,
        activeRequestId: state.activeRequestId,
      ),
    );
  }

  ConversationStateReducerResult _complete(
    ConversationScreenState state,
    AssistantCompleted event,
  ) {
    if (!_matchesRequest(state, event.requestId)) {
      return _protocol(ConversationOperation.send);
    }
    if (!_containsPendingUserMessage(state, event.messages)) {
      return _protocol(ConversationOperation.send);
    }
    final merged = _mergeMessages(state.messages, event.messages);
    if (merged == null) return _protocol(ConversationOperation.send);
    return StateTransition(
      ConversationScreenState.fromReducer(
        status: ConversationScreenStatus.ready,
        conversation: state.conversation,
        messages: merged,
        pagination: state.pagination,
      ),
    );
  }

  ConversationStateReducerResult _sendFailed(
    ConversationScreenState state,
    ConversationFailure failure, {
    bool retainPending = false,
  }) {
    if ((state.status != ConversationScreenStatus.sending &&
            state.status != ConversationScreenStatus.streaming) ||
        failure.operation != ConversationOperation.send) {
      return _invalid(ConversationOperation.send);
    }
    // In `streaming`, requestId must be set and match the active request.
    if (state.status == ConversationScreenStatus.streaming &&
        failure.requestId != state.activeRequestId) {
      return _protocol(ConversationOperation.send);
    }
    final keepPending =
        retainPending ||
        failure.resultCertainty == SendResultCertainty.resultUnknown;
    return StateTransition(
      ConversationScreenState.fromReducer(
        status: ConversationScreenStatus.sendFailed,
        conversation: state.conversation,
        messages: state.messages,
        pagination: state.pagination,
        pendingSend: keepPending ? state.pendingSend : null,
        temporaryAssistantText: state.temporaryAssistantText,
        failure: failure,
      ),
    );
  }

  // sendFailed dismissal policy is FIP-010's responsibility; not defined here.
  ConversationStateReducerResult _dismiss(ConversationScreenState state) {
    if (state.status == ConversationScreenStatus.ready &&
        state.failure != null) {
      return StateTransition(state.copyWith(failure: null));
    }
    return _invalid(ConversationOperation.send);
  }

  ConversationStateReducerResult _reconcileStart(
    ConversationScreenState state,
  ) {
    if (state.status != ConversationScreenStatus.sendFailed) {
      return _invalid(ConversationOperation.reconciliation);
    }
    return StateTransition(
      ConversationScreenState.fromReducer(
        status: ConversationScreenStatus.initialLoading,
        conversation: state.conversation,
        messages: state.messages,
        pagination: state.pagination,
        pendingSend: state.pendingSend,
      ),
    );
  }

  ConversationStateReducerResult _reconcileSuccess(
    ConversationScreenState state,
    HistoryReconciliationSucceeded event,
  ) {
    if (state.status != ConversationScreenStatus.initialLoading) {
      return _invalid(ConversationOperation.reconciliation);
    }
    if (event.sendResult == ReconciliationSendResult.confirmedCompleted) {
      return StateTransition(
        ConversationScreenState.fromReducer(
          status: ConversationScreenStatus.ready,
          conversation: state.conversation,
          messages: event.canonicalMessages,
          pagination: state.pagination,
          pendingSend: null,
        ),
      );
    }
    return StateTransition(
      ConversationScreenState.fromReducer(
        status: ConversationScreenStatus.sendFailed,
        conversation: state.conversation,
        messages: event.canonicalMessages,
        pagination: state.pagination,
        pendingSend: state.pendingSend,
        failure: ConversationFailure(
          category: ConversationFailureCategory.resultUnknown,
          operation: ConversationOperation.reconciliation,
          resultCertainty: SendResultCertainty.resultUnknown,
        ),
      ),
    );
  }

  ConversationStateReducerResult _reconcileFailure(
    ConversationScreenState state,
    HistoryReconciliationFailed event,
  ) {
    if (state.status != ConversationScreenStatus.initialLoading ||
        event.failure.operation != ConversationOperation.reconciliation) {
      return _invalid(ConversationOperation.reconciliation);
    }
    return StateTransition(
      ConversationScreenState.fromReducer(
        status: ConversationScreenStatus.sendFailed,
        conversation: state.conversation,
        messages: state.messages,
        pagination: state.pagination,
        pendingSend: state.pendingSend,
        failure: event.failure,
      ),
    );
  }

  bool _matchesRequest(ConversationScreenState state, String requestId) {
    return state.status == ConversationScreenStatus.streaming &&
        state.activeRequestId == requestId;
  }

  bool _containsPendingUserMessage(
    ConversationScreenState state,
    List<Message> messages,
  ) {
    final pendingSend = state.pendingSend;
    if (pendingSend == null) return false;
    return messages.any(
      (message) =>
          message.role == MessageRole.user &&
          message.content == pendingSend.content,
    );
  }

  List<Message>? _initialMessages(List<Message> messages) {
    final seen = <String>{};
    for (final message in messages) {
      if (!seen.add(message.id)) return null;
    }
    return messages;
  }

  List<Message>? _mergeMessages(List<Message> first, List<Message> second) {
    final result = List<Message>.of(first);
    final byId = <String, Message>{
      for (final message in first) message.id: message,
    };
    final seenSecond = <String>{};
    for (final message in second) {
      if (!seenSecond.add(message.id)) return null;
      final existing = byId[message.id];
      if (existing != null) {
        if (existing != message) return null;
        continue;
      }
      byId[message.id] = message;
      result.add(message);
    }
    return result;
  }

  StateTransitionFailure _invalid(ConversationOperation operation) {
    return StateTransitionFailure(
      category: ConversationFailureCategory.protocolViolation,
      operation: operation,
    );
  }

  StateTransitionFailure _protocol(ConversationOperation operation) =>
      _invalid(operation);
}
