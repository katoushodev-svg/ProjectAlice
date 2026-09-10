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

  test('requires retryAfter only for request-in-progress failures', () {
    expect(
      () => ConversationFailure(
        category: ConversationFailureCategory.requestInProgress,
        operation: ConversationOperation.send,
      ),
      throwsArgumentError,
    );
    expect(
      () => ConversationFailure(
        category: ConversationFailureCategory.serverFailure,
        operation: ConversationOperation.send,
        retryAfter: Duration(seconds: 1),
      ),
      throwsArgumentError,
    );
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
}
