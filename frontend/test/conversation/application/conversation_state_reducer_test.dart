import 'package:flutter_test/flutter_test.dart';

import 'package:alice/conversation/application/error/conversation_failure.dart';
import 'package:alice/conversation/application/model/message_page.dart';
import 'package:alice/conversation/application/state/conversation_screen_state.dart';
import 'package:alice/conversation/application/state/conversation_state_event.dart';
import 'package:alice/conversation/application/state/conversation_state_reducer.dart';
import 'package:alice/conversation/domain/message.dart';
import 'package:alice/conversation/domain/message_role.dart';
import 'package:alice/conversation/domain/outgoing_message.dart';

void main() {
  const reducer = ConversationStateReducer();
  final createdAt = DateTime.utc(2026, 1, 1);
  final userMessage = Message(
    id: 'user-1',
    role: MessageRole.user,
    content: 'hello',
    createdAt: createdAt,
  );
  final assistantMessage = Message(
    id: 'assistant-1',
    role: MessageRole.assistant,
    content: 'world',
    createdAt: createdAt.add(const Duration(seconds: 1)),
  );
  final outgoing =
      (OutgoingMessage.create(
            idempotencyKey: '123e4567-e89b-12d3-a456-426614174000',
            content: 'hello',
          ) as dynamic).value
          as OutgoingMessage;

  ConversationScreenState readyState({
    List<Message> messages = const [],
    String? nextCursor,
    bool hasMore = false,
  }) {
    return (reducer.reduce(
      ConversationScreenState.initial(),
      InitialLoadSucceeded(
        conversation: null,
        page: MessagePage(
          messages: messages,
          nextCursor: nextCursor,
          hasMore: hasMore,
        ),
      ),
    ) as StateTransition).nextState;
  }

  ConversationScreenState sendingState(ConversationScreenState ready) {
    return (reducer.reduce(
      ready,
      SendStarted(outgoing),
    ) as StateTransition).nextState;
  }

  ConversationScreenState streamingState(
    ConversationScreenState sending, {
    String requestId = 'request-1',
  }) {
    return (reducer.reduce(
      sending,
      StreamStarted(requestId),
    ) as StateTransition).nextState;
  }

  test('loads history and merges an older page without reordering', () {
    final loaded = reducer.reduce(
      ConversationScreenState.initial(),
      InitialLoadSucceeded(
        conversation: null,
        page: MessagePage(
          messages: [userMessage, assistantMessage],
          nextCursor: 'cursor-1',
          hasMore: true,
        ),
      ),
    ) as StateTransition;
    final loadingOlder = reducer.reduce(
      loaded.nextState,
      const OlderPageLoadStarted('cursor-1'),
    ) as StateTransition;
    final result = reducer.reduce(
      loadingOlder.nextState,
      OlderPageLoadSucceeded(
        requestedCursor: 'cursor-1',
        page: MessagePage(messages: const [], nextCursor: null, hasMore: false),
      ),
    ) as StateTransition;

    expect(result.nextState.status, ConversationScreenStatus.ready);
    expect(result.nextState.messages, [userMessage, assistantMessage]);
    expect(result.nextState.pagination.hasMore, isFalse);
  });

  test('keeps the same logical send when the result is unknown', () {
    final ready = reducer.reduce(
      ConversationScreenState.initial(),
      InitialLoadSucceeded(
        conversation: null,
        page: MessagePage(messages: const [], nextCursor: null, hasMore: false),
      ),
    ) as StateTransition;
    final sending = reducer.reduce(
      ready.nextState,
      SendStarted(outgoing),
    ) as StateTransition;
    final failed = reducer.reduce(
      sending.nextState,
      SendResultUnknown(
        ConversationFailure(
          category: ConversationFailureCategory.resultUnknown,
          operation: ConversationOperation.send,
          resultCertainty: SendResultCertainty.resultUnknown,
        ),
      ),
    ) as StateTransition;

    expect(failed.nextState.status, ConversationScreenStatus.sendFailed);
    expect(failed.nextState.pendingSend, outgoing);
    expect(failed.nextState.toString(), isNot(contains(outgoing.content)));
  });

  test('keeps deltas temporary and rejects a mismatched request id', () {
    final ready = reducer.reduce(
      ConversationScreenState.initial(),
      InitialLoadSucceeded(
        conversation: null,
        page: MessagePage(messages: const [], nextCursor: null, hasMore: false),
      ),
    ) as StateTransition;
    final sending = reducer.reduce(
      ready.nextState,
      SendStarted(outgoing),
    ) as StateTransition;
    final streaming = reducer.reduce(
      sending.nextState,
      const StreamStarted('request-1'),
    ) as StateTransition;
    final delta = reducer.reduce(
      streaming.nextState,
      const AssistantDeltaReceived(requestId: 'request-1', delta: 'partial'),
    ) as StateTransition;
    final invalid = reducer.reduce(
      delta.nextState,
      const AssistantDeltaReceived(requestId: 'request-2', delta: 'ignored'),
    );

    expect(delta.nextState.messages, isEmpty);
    expect(delta.nextState.temporaryAssistantText, 'partial');
    expect(invalid, isA<StateTransitionFailure>());
  });

  test('rejects a completed result with a mismatched user message', () {
    final ready = reducer.reduce(
      ConversationScreenState.initial(),
      InitialLoadSucceeded(
        conversation: null,
        page: MessagePage(messages: const [], nextCursor: null, hasMore: false),
      ),
    ) as StateTransition;
    final sending = reducer.reduce(
      ready.nextState,
      SendStarted(outgoing),
    ) as StateTransition;
    final streaming = reducer.reduce(
      sending.nextState,
      const StreamStarted('request-1'),
    ) as StateTransition;
    final invalid = reducer.reduce(
      streaming.nextState,
      AssistantCompleted(
        requestId: 'request-1',
        messages: [
          Message(
            id: 'user-2',
            role: MessageRole.user,
            content: 'different',
            createdAt: createdAt,
          ),
          assistantMessage,
        ],
      ),
    );

    expect(invalid, isA<StateTransitionFailure>());
  });

  test('initial state is initialLoading', () {
    expect(
      ConversationScreenState.initial().status,
      ConversationScreenStatus.initialLoading,
    );
  });

  test('rejects sendStarted from every non-ready status', () {
    final initialLoading = ConversationScreenState.initial();
    final ready = readyState();
    final sending = sendingState(ready);
    final streaming = streamingState(sending);

    for (final state in [initialLoading, sending, streaming]) {
      final result = reducer.reduce(state, SendStarted(outgoing));
      expect(result, isA<StateTransitionFailure>());
    }
  });

  test(
    'rejects initialLoadFailed with a non-initialLoad failure operation',
    () {
      final result = reducer.reduce(
        ConversationScreenState.initial(),
        InitialLoadFailed(
          ConversationFailure(
            category: ConversationFailureCategory.networkUnavailable,
            operation: ConversationOperation.send,
          ),
        ),
      );

      expect(result, isA<StateTransitionFailure>());
    },
  );

  test('reaches initialLoadFailed on initial load failure', () {
    final result = reducer.reduce(
      ConversationScreenState.initial(),
      InitialLoadFailed(
        ConversationFailure(
          category: ConversationFailureCategory.networkUnavailable,
          operation: ConversationOperation.initialLoad,
        ),
      ),
    ) as StateTransition;

    expect(result.nextState.status, ConversationScreenStatus.initialLoadFailed);
    expect(result.nextState.failure, isNotNull);
  });

  test('reloads from initialLoadFailed back to initialLoading', () {
    final failed = reducer.reduce(
      ConversationScreenState.initial(),
      InitialLoadFailed(
        ConversationFailure(
          category: ConversationFailureCategory.networkUnavailable,
          operation: ConversationOperation.initialLoad,
        ),
      ),
    ) as StateTransition;
    final reload = reducer.reduce(
      failed.nextState,
      const InitialLoadStarted(),
    ) as StateTransition;

    expect(reload.nextState.status, ConversationScreenStatus.initialLoading);
    expect(reload.nextState.failure, isNull);
  });

  test('rejects olderPageLoadStarted with a mismatched cursor', () {
    final ready = readyState(nextCursor: 'cursor-1', hasMore: true);
    final result = reducer.reduce(
      ready,
      const OlderPageLoadStarted('wrong-cursor'),
    );

    expect(result, isA<StateTransitionFailure>());
  });

  test('rejects olderPageLoadStarted while sending, streaming, or loading', () {
    final initialLoading = ConversationScreenState.initial();
    final readyWithMore = readyState(nextCursor: 'cursor-1', hasMore: true);
    final sending = sendingState(readyWithMore);
    final streaming = streamingState(sending);

    for (final state in [initialLoading, sending, streaming]) {
      final result = reducer.reduce(
        state,
        const OlderPageLoadStarted('cursor-1'),
      );
      expect(result, isA<StateTransitionFailure>());
    }
  });

  test('keeps existing messages and cursor when pagination fails', () {
    final ready = readyState(
      messages: [userMessage],
      nextCursor: 'cursor-1',
      hasMore: true,
    );
    final loadingOlder = reducer.reduce(
      ready,
      const OlderPageLoadStarted('cursor-1'),
    ) as StateTransition;
    final failed = reducer.reduce(
      loadingOlder.nextState,
      OlderPageLoadFailed(
        requestedCursor: 'cursor-1',
        failure: ConversationFailure(
          category: ConversationFailureCategory.networkUnavailable,
          operation: ConversationOperation.pagination,
        ),
      ),
    ) as StateTransition;

    expect(failed.nextState.status, ConversationScreenStatus.ready);
    expect(failed.nextState.messages, [userMessage]);
    expect(failed.nextState.pagination.nextCursor, 'cursor-1');
    expect(failed.nextState.pagination.hasMore, isTrue);
    expect(failed.nextState.failure, isNotNull);
  });

  test('merges an identical boundary duplicate into a single message', () {
    final ready = readyState(
      messages: [userMessage, assistantMessage],
      nextCursor: 'cursor-1',
      hasMore: true,
    );
    final loadingOlder = reducer.reduce(
      ready,
      const OlderPageLoadStarted('cursor-1'),
    ) as StateTransition;
    final result = reducer.reduce(
      loadingOlder.nextState,
      OlderPageLoadSucceeded(
        requestedCursor: 'cursor-1',
        page: MessagePage(
          messages: [userMessage],
          nextCursor: null,
          hasMore: false,
        ),
      ),
    ) as StateTransition;

    expect(result.nextState.messages, [userMessage, assistantMessage]);
  });

  test('treats a conflicting boundary duplicate as a protocol violation', () {
    final ready = readyState(
      messages: [userMessage, assistantMessage],
      nextCursor: 'cursor-1',
      hasMore: true,
    );
    final loadingOlder = reducer.reduce(
      ready,
      const OlderPageLoadStarted('cursor-1'),
    ) as StateTransition;
    final conflicting = Message(
      id: userMessage.id,
      role: MessageRole.user,
      content: 'different content',
      createdAt: createdAt,
    );
    final result = reducer.reduce(
      loadingOlder.nextState,
      OlderPageLoadSucceeded(
        requestedCursor: 'cursor-1',
        page: MessagePage(
          messages: [conflicting],
          nextCursor: null,
          hasMore: false,
        ),
      ),
    );

    expect(result, isA<StateTransitionFailure>());
  });

  test('accepts a zero-delta completion after streaming starts', () {
    final ready = readyState();
    final sending = sendingState(ready);
    final streaming = streamingState(sending);
    final completed = reducer.reduce(
      streaming,
      AssistantCompleted(
        requestId: 'request-1',
        messages: [userMessage, assistantMessage],
      ),
    ) as StateTransition;

    expect(completed.nextState.status, ConversationScreenStatus.ready);
    expect(completed.nextState.temporaryAssistantText, isNull);
    expect(completed.nextState.messages, [userMessage, assistantMessage]);
  });

  test('rejects a completed event with a mismatched request id', () {
    final ready = readyState();
    final sending = sendingState(ready);
    final streaming = streamingState(sending);
    final result = reducer.reduce(
      streaming,
      AssistantCompleted(
        requestId: 'other-request',
        messages: [userMessage, assistantMessage],
      ),
    );

    expect(result, isA<StateTransitionFailure>());
  });

  test('rejects a duplicate terminal completed event', () {
    final ready = readyState();
    final sending = sendingState(ready);
    final streaming = streamingState(sending);
    final completed = reducer.reduce(
      streaming,
      AssistantCompleted(
        requestId: 'request-1',
        messages: [userMessage, assistantMessage],
      ),
    ) as StateTransition;
    final duplicate = reducer.reduce(
      completed.nextState,
      AssistantCompleted(
        requestId: 'request-1',
        messages: [userMessage, assistantMessage],
      ),
    );

    expect(duplicate, isA<StateTransitionFailure>());
  });

  test('rejects an event delivered after a terminal sendFailed', () {
    final ready = readyState();
    final sending = sendingState(ready);
    final streaming = streamingState(sending);
    final failed = reducer.reduce(
      streaming,
      SendFailed(
        ConversationFailure(
          category: ConversationFailureCategory.generationFailed,
          operation: ConversationOperation.send,
          requestId: 'request-1',
        ),
      ),
    ) as StateTransition;
    final afterTerminal = reducer.reduce(
      failed.nextState,
      const AssistantDeltaReceived(requestId: 'request-1', delta: 'late'),
    );

    expect(afterTerminal, isA<StateTransitionFailure>());
  });

  test('allows a null requestId for a pre-stream sendFailed while sending', () {
    final ready = readyState();
    final sending = sendingState(ready);
    final failed = reducer.reduce(
      sending,
      SendFailed(
        ConversationFailure(
          category: ConversationFailureCategory.networkUnavailable,
          operation: ConversationOperation.send,
        ),
      ),
    ) as StateTransition;

    expect(failed.nextState.status, ConversationScreenStatus.sendFailed);
  });

  test('requires a matching requestId for sendFailed while streaming', () {
    final ready = readyState();
    final sending = sendingState(ready);
    final streaming = streamingState(sending);
    final mismatched = reducer.reduce(
      streaming,
      SendFailed(
        ConversationFailure(
          category: ConversationFailureCategory.generationFailed,
          operation: ConversationOperation.send,
          requestId: 'other-request',
        ),
      ),
    );
    final missing = reducer.reduce(
      streaming,
      SendFailed(
        ConversationFailure(
          category: ConversationFailureCategory.generationFailed,
          operation: ConversationOperation.send,
        ),
      ),
    );
    final matching = reducer.reduce(
      streaming,
      SendFailed(
        ConversationFailure(
          category: ConversationFailureCategory.generationFailed,
          operation: ConversationOperation.send,
          requestId: 'request-1',
        ),
      ),
    ) as StateTransition;

    expect(mismatched, isA<StateTransitionFailure>());
    expect(missing, isA<StateTransitionFailure>());
    expect(matching.nextState.status, ConversationScreenStatus.sendFailed);
  });

  test('accepts exactly 50,000 code points and rejects 50,001', () {
    final ready = readyState();
    final sending = sendingState(ready);
    final streaming = streamingState(sending);
    final atLimit = 'a' * 50000;
    final withinLimit = reducer.reduce(
      streaming,
      AssistantDeltaReceived(requestId: 'request-1', delta: atLimit),
    ) as StateTransition;

    expect(withinLimit.nextState.status, ConversationScreenStatus.streaming);
    expect(withinLimit.nextState.temporaryAssistantText!.runes.length, 50000);

    final overLimit = reducer.reduce(
      withinLimit.nextState,
      const AssistantDeltaReceived(requestId: 'request-1', delta: 'a'),
    ) as StateTransition;

    expect(overLimit.nextState.status, ConversationScreenStatus.sendFailed);
    expect(
      overLimit.nextState.failure!.category,
      ConversationFailureCategory.assistantContentTooLong,
    );
  });

  test('dismisses a pagination failure held in ready', () {
    final ready = readyState(nextCursor: 'cursor-1', hasMore: true);
    final loadingOlder = reducer.reduce(
      ready,
      const OlderPageLoadStarted('cursor-1'),
    ) as StateTransition;
    final failed = reducer.reduce(
      loadingOlder.nextState,
      OlderPageLoadFailed(
        requestedCursor: 'cursor-1',
        failure: ConversationFailure(
          category: ConversationFailureCategory.networkUnavailable,
          operation: ConversationOperation.pagination,
        ),
      ),
    ) as StateTransition;
    final dismissed = reducer.reduce(
      failed.nextState,
      const FailureDismissed(),
    ) as StateTransition;

    expect(dismissed.nextState.status, ConversationScreenStatus.ready);
    expect(dismissed.nextState.failure, isNull);
  });

  test('rejects failureDismissed from sendFailed (FIP-010 responsibility)', () {
    final ready = readyState();
    final sending = sendingState(ready);
    final failed = reducer.reduce(
      sending,
      SendFailed(
        ConversationFailure(
          category: ConversationFailureCategory.networkUnavailable,
          operation: ConversationOperation.send,
        ),
      ),
    ) as StateTransition;
    final dismissed = reducer.reduce(
      failed.nextState,
      const FailureDismissed(),
    );

    expect(dismissed, isA<StateTransitionFailure>());
  });

  test('starts history reconciliation from sendFailed', () {
    final ready = readyState();
    final sending = sendingState(ready);
    final failed = reducer.reduce(
      sending,
      SendResultUnknown(
        ConversationFailure(
          category: ConversationFailureCategory.resultUnknown,
          operation: ConversationOperation.send,
          resultCertainty: SendResultCertainty.resultUnknown,
        ),
      ),
    ) as StateTransition;
    final reconciling = reducer.reduce(
      failed.nextState,
      const HistoryReconciliationStarted(),
    ) as StateTransition;

    expect(
      reconciling.nextState.status,
      ConversationScreenStatus.initialLoading,
    );
    expect(reconciling.nextState.pendingSend, outgoing);
  });

  test('clears pendingSend when reconciliation confirms completion', () {
    final ready = readyState();
    final sending = sendingState(ready);
    final failed = reducer.reduce(
      sending,
      SendFailed(
        ConversationFailure(
          category: ConversationFailureCategory.networkUnavailable,
          operation: ConversationOperation.send,
        ),
      ),
    ) as StateTransition;
    final reconciling = reducer.reduce(
      failed.nextState,
      const HistoryReconciliationStarted(),
    ) as StateTransition;
    final succeeded = reducer.reduce(
      reconciling.nextState,
      HistoryReconciliationSucceeded(
        canonicalMessages: [userMessage, assistantMessage],
        sendResult: ReconciliationSendResult.confirmedCompleted,
      ),
    ) as StateTransition;

    expect(succeeded.nextState.status, ConversationScreenStatus.ready);
    expect(succeeded.nextState.pendingSend, isNull);
    expect(succeeded.nextState.messages, [userMessage, assistantMessage]);
  });

  test('returns to sendFailed when history reconciliation fails', () {
    final ready = readyState();
    final sending = sendingState(ready);
    final failed = reducer.reduce(
      sending,
      SendResultUnknown(
        ConversationFailure(
          category: ConversationFailureCategory.resultUnknown,
          operation: ConversationOperation.send,
          resultCertainty: SendResultCertainty.resultUnknown,
        ),
      ),
    ) as StateTransition;
    final reconciling = reducer.reduce(
      failed.nextState,
      const HistoryReconciliationStarted(),
    ) as StateTransition;
    final reconciliationFailure = ConversationFailure(
      category: ConversationFailureCategory.networkUnavailable,
      operation: ConversationOperation.reconciliation,
    );
    final result = reducer.reduce(
      reconciling.nextState,
      HistoryReconciliationFailed(reconciliationFailure),
    ) as StateTransition;

    expect(result.nextState.status, ConversationScreenStatus.sendFailed);
    expect(result.nextState.pendingSend, outgoing);
    expect(result.nextState.failure, reconciliationFailure);
  });

  test('transitions to sendFailed and retains pendingSend when reconciliation result is notConfirmed without invariant violation', () {
    final ready = readyState();
    final sending = sendingState(ready);
    final failed = reducer.reduce(
      sending,
      SendResultUnknown(
        ConversationFailure(
          category: ConversationFailureCategory.resultUnknown,
          operation: ConversationOperation.send,
          resultCertainty: SendResultCertainty.resultUnknown,
        ),
      ),
    ) as StateTransition;
    final reconciling = reducer.reduce(
      failed.nextState,
      const HistoryReconciliationStarted(),
    ) as StateTransition;
    final succeededNotConfirmed = reducer.reduce(
      reconciling.nextState,
      HistoryReconciliationSucceeded(
        canonicalMessages: [userMessage],
        sendResult: ReconciliationSendResult.notConfirmed,
      ),
    ) as StateTransition;

    expect(
      succeededNotConfirmed.nextState.status,
      ConversationScreenStatus.sendFailed,
    );
    expect(succeededNotConfirmed.nextState.pendingSend, outgoing);
    expect(succeededNotConfirmed.nextState.messages, [userMessage]);
    expect(succeededNotConfirmed.nextState.failure, isNotNull);
    expect(
      succeededNotConfirmed.nextState.failure!.category,
      ConversationFailureCategory.resultUnknown,
    );
    expect(
      succeededNotConfirmed.nextState.failure!.operation,
      ConversationOperation.reconciliation,
    );
  });
}
