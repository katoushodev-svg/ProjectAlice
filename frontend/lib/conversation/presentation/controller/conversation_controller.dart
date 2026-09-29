import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../application/error/conversation_failure.dart';
import '../../application/model/conversation_send_event.dart' as send_event;
import '../../application/model/message_page.dart';
import '../../application/port/conversation_gateway.dart';
import '../../application/port/idempotency_key_generator.dart';
import '../../application/state/conversation_screen_state.dart';
import '../../application/state/conversation_state_event.dart' as state_event;
import '../../application/state/conversation_state_reducer.dart';
import '../../domain/conversation.dart';
import '../../domain/domain_result.dart';
import '../../domain/outgoing_message.dart';

enum ConversationSendValidation { valid, empty, whitespaceOnly, tooLong }

final class ConversationController extends ChangeNotifier {
  ConversationController({
    required this._gateway,
    IdempotencyKeyGenerator? idempotencyKeyGenerator,
    DateTime Function()? clock,
    this._reducer = const ConversationStateReducer(),
  }) : _idempotencyKeyGenerator =
           idempotencyKeyGenerator ?? UuidV4IdempotencyKeyGenerator(),
       _state = ConversationScreenState.initial(),
       _clock = clock ?? DateTime.now;

  static const Duration deltaBatchWindow = Duration(milliseconds: 50);

  final ConversationGateway _gateway;
  final IdempotencyKeyGenerator _idempotencyKeyGenerator;
  final ConversationStateReducer _reducer;
  final DateTime Function() _clock;
  ConversationScreenState _state;
  bool _isLoading = false;
  bool _isDisposed = false;
  int _generation = 0;
  StreamSubscription<send_event.ConversationSendEvent>? _sendSubscription;
  Timer? _deltaTimer;
  String _pendingDelta = '';
  DateTime? _retryNotBefore;
  Timer? _retryTimer;
  bool _recoveryInProgress = false;

  ConversationScreenState get state => _state;
  DateTime? get retryNotBefore => _retryNotBefore;
  bool get recoveryInProgress => _recoveryInProgress;
  bool get canCheckResult =>
      !_recoveryInProgress &&
      (_retryNotBefore == null || !_clock().isBefore(_retryNotBefore!));

  bool retrySameSend() {
    final failure = _state.failure;
    final outgoing = _state.pendingSend;
    if (_isDisposed ||
        _recoveryInProgress ||
        outgoing == null ||
        failure == null ||
        failure.terminalStreamFailure ||
        (failure.category != ConversationFailureCategory.conversationBusy &&
            failure.category != ConversationFailureCategory.serverFailure &&
            failure.category !=
                ConversationFailureCategory.networkUnavailable)) {
      return false;
    }
    return _startRecovery(outgoing);
  }

  bool checkResult() {
    final failure = _state.failure;
    final outgoing = _state.pendingSend;
    if (_isDisposed ||
        !canCheckResult ||
        outgoing == null ||
        failure == null ||
        (failure.category != ConversationFailureCategory.resultUnknown &&
            failure.category !=
                ConversationFailureCategory.requestInProgress)) {
      return false;
    }
    return _startRecovery(outgoing);
  }

  bool sendSameContentAsNew() {
    final old = _state.pendingSend;
    if (_isDisposed ||
        _recoveryInProgress ||
        old == null ||
        _state.status != ConversationScreenStatus.sendFailed ||
        _state.failure?.terminalStreamFailure != true) {
      return false;
    }
    final result = OutgoingMessage.create(
      idempotencyKey: _idempotencyKeyGenerator.generate(),
      content: old.content,
    );
    if (result is! DomainSuccess<OutgoingMessage>) return false;
    return _startRecovery(result.value);
  }

  bool _startRecovery(OutgoingMessage outgoing) {
    _retryTimer?.cancel();
    _retryNotBefore = null;
    _recoveryInProgress = true;
    _reduce(state_event.RecoverySendStarted(outgoing));
    if (_state.status != ConversationScreenStatus.sending) {
      _recoveryInProgress = false;
      return false;
    }
    unawaited(_consumeSend(outgoing));
    return true;
  }

  Future<bool> reloadConversation() async {
    if (_isDisposed ||
        _recoveryInProgress ||
        _state.status != ConversationScreenStatus.sendFailed) {
      return false;
    }
    _recoveryInProgress = true;
    _reduce(const state_event.HistoryReconciliationStarted());
    try {
      final conversationResult = await _gateway.getConversation();
      if (conversationResult is GatewayFailure<Conversation?>) {
        _reduce(
          state_event.HistoryReconciliationFailed(
            _asReconciliationFailure(conversationResult.failure),
          ),
        );
        return false;
      }
      final messagesResult = await _gateway.getMessages(
        limit: 50,
        cursor: null,
      );
      if (messagesResult is GatewayFailure<MessagePage>) {
        _reduce(
          state_event.HistoryReconciliationFailed(
            _asReconciliationFailure(messagesResult.failure),
          ),
        );
        return false;
      }
      final page = (messagesResult as GatewaySuccess<MessagePage>).value;
      _reduce(
        state_event.HistoryReconciliationSucceeded(
          canonicalMessages: page.messages,
          sendResult: state_event.ReconciliationSendResult.notConfirmed,
        ),
      );
      return _state.status == ConversationScreenStatus.sendFailed;
    } catch (_) {
      if (!_isDisposed &&
          _state.status == ConversationScreenStatus.initialLoading) {
        _reduce(
          state_event.HistoryReconciliationFailed(
            ConversationFailure(
              category: ConversationFailureCategory.unknown,
              operation: ConversationOperation.reconciliation,
            ),
          ),
        );
      }
      return false;
    } finally {
      _recoveryInProgress = false;
      if (!_isDisposed) notifyListeners();
    }
  }

  ConversationFailure _asReconciliationFailure(ConversationFailure failure) =>
      ConversationFailure(
        category: failure.category,
        operation: ConversationOperation.reconciliation,
      );

  void _armRetryAfter(Duration? delay) {
    _retryTimer?.cancel();
    if (delay == null || delay <= Duration.zero) {
      _retryNotBefore = null;
      _recoveryInProgress = false;
      return;
    }
    _retryNotBefore = _clock().add(delay);
    _recoveryInProgress = false;
    _retryTimer = Timer(delay, () {
      if (!_isDisposed) notifyListeners();
    });
  }

  static ConversationSendValidation validateContent(String content) {
    if (content.isEmpty) return ConversationSendValidation.empty;
    if (content.trim().isEmpty) {
      return ConversationSendValidation.whitespaceOnly;
    }
    if (content.runes.length > OutgoingMessage.maxContentCodePoints) {
      return ConversationSendValidation.tooLong;
    }
    return ConversationSendValidation.valid;
  }

  Future<void> loadInitial() async {
    if (_isLoading || _isDisposed) return;

    _isLoading = true;
    final generation = ++_generation;
    _reduce(const state_event.InitialLoadStarted());

    try {
      final conversationResult = await _gateway.getConversation();
      if (!_isCurrent(generation)) return;
      if (conversationResult is GatewayFailure<Conversation?>) {
        _failInitialLoad(conversationResult.failure);
        return;
      }

      final conversation =
          (conversationResult as GatewaySuccess<Conversation?>).value;
      final messagesResult = await _gateway.getMessages(
        limit: 50,
        cursor: null,
      );
      if (!_isCurrent(generation)) return;
      if (messagesResult is GatewayFailure<MessagePage>) {
        _failInitialLoad(messagesResult.failure);
        return;
      }

      final page = (messagesResult as GatewaySuccess<MessagePage>).value;
      _reduce(
        state_event.InitialLoadSucceeded(
          conversation: conversation,
          page: page,
        ),
      );
    } finally {
      if (_isCurrent(generation)) _isLoading = false;
    }
  }

  Future<void> loadOlder() async {
    if (_isDisposed || _state.status != ConversationScreenStatus.ready) return;
    final cursor = _state.pagination.nextCursor;
    if (!_state.pagination.hasMore || cursor == null) return;
    _reduce(state_event.OlderPageLoadStarted(cursor));
    try {
      final result = await _gateway.getMessages(limit: 50, cursor: cursor);
      if (_isDisposed ||
          _state.status != ConversationScreenStatus.loadingOlder) {
        return;
      }
      switch (result) {
        case GatewaySuccess<MessagePage>(:final value):
          _reduce(
            state_event.OlderPageLoadSucceeded(
              requestedCursor: cursor,
              page: value,
            ),
          );
        case GatewayFailure<MessagePage>(:final failure):
          if (failure.category == ConversationFailureCategory.invalidCursor) {
            _reduce(const state_event.LatestPageReconciliationStarted());
            await _requestLatestPage();
            return;
          }
          _reduce(
            state_event.OlderPageLoadFailed(
              requestedCursor: cursor,
              failure: ConversationFailure(
                category: failure.category,
                operation: ConversationOperation.pagination,
                resultCertainty: failure.resultCertainty,
              ),
            ),
          );
      }
    } catch (_) {
      if (!_isDisposed &&
          _state.status == ConversationScreenStatus.loadingOlder) {
        _reduce(
          state_event.OlderPageLoadFailed(
            requestedCursor: cursor,
            failure: ConversationFailure(
              category: ConversationFailureCategory.unknown,
              operation: ConversationOperation.pagination,
            ),
          ),
        );
      }
    }
  }

  Future<void> reloadLatestHistory() async {
    final failure = _state.failure;
    if (_isDisposed ||
        _state.status != ConversationScreenStatus.ready ||
        failure?.operation != ConversationOperation.pagination ||
        failure?.category != ConversationFailureCategory.invalidCursor) {
      return;
    }
    _reduce(const state_event.LatestPageReconciliationStarted());
    await _requestLatestPage();
  }

  Future<void> _requestLatestPage() async {
    try {
      final result = await _gateway.getMessages(limit: 50, cursor: null);
      if (_isDisposed ||
          _state.status != ConversationScreenStatus.reconcilingHistory) {
        return;
      }
      switch (result) {
        case GatewaySuccess<MessagePage>(:final value):
          _reduce(state_event.LatestPageReconciliationSucceeded(value));
        case GatewayFailure<MessagePage>():
          _reduce(
            state_event.LatestPageReconciliationFailed(
              ConversationFailure(
                category: ConversationFailureCategory.invalidCursor,
                operation: ConversationOperation.pagination,
              ),
            ),
          );
      }
    } catch (_) {
      if (!_isDisposed &&
          _state.status == ConversationScreenStatus.reconcilingHistory) {
        _reduce(
          state_event.LatestPageReconciliationFailed(
            ConversationFailure(
              category: ConversationFailureCategory.invalidCursor,
              operation: ConversationOperation.pagination,
            ),
          ),
        );
      }
    }
  }

  bool send(String content) {
    final editRecovery =
        _state.status == ConversationScreenStatus.sendFailed &&
        (_state.failure?.category == ConversationFailureCategory.validation ||
            _state.failure?.category ==
                ConversationFailureCategory.requestBodyTooLarge);
    if (_isDisposed ||
        (_state.status != ConversationScreenStatus.ready && !editRecovery)) {
      return false;
    }
    if (validateContent(content) != ConversationSendValidation.valid) {
      return false;
    }

    final idempotencyKey = _idempotencyKeyGenerator.generate();
    final result = OutgoingMessage.create(
      idempotencyKey: idempotencyKey,
      content: content,
    );
    if (result is! DomainSuccess<OutgoingMessage>) {
      return false;
    }

    final outgoing = result.value;
    final transition = _reducer.reduce(
      _state,
      editRecovery
          ? state_event.RecoverySendStarted(outgoing)
          : state_event.SendStarted(outgoing),
    );
    if (transition is! StateTransition) return false;

    _state = transition.nextState;
    notifyListeners();
    unawaited(_consumeSend(outgoing));
    return true;
  }

  Future<void> _consumeSend(OutgoingMessage outgoing) async {
    _clearDeltaBatch();

    try {
      final subscription = _gateway
          .sendMessage(outgoing)
          .listen(
            _handleSendEvent,
            onError: (Object _) {
              _flushDelta();
              _applyFailure(
                ConversationFailure(
                  category: ConversationFailureCategory.resultUnknown,
                  operation: ConversationOperation.send,
                  resultCertainty: SendResultCertainty.resultUnknown,
                  requestId: _state.activeRequestId,
                ),
              );
            },
            onDone: () {},
            cancelOnError: false,
          );
      _sendSubscription = subscription;
      await subscription.asFuture<void>();
    } catch (_) {
      _flushDelta();
      if (!_isDisposed &&
          (_state.status == ConversationScreenStatus.sending ||
              _state.status == ConversationScreenStatus.streaming)) {
        _applyFailure(
          ConversationFailure(
            category: ConversationFailureCategory.resultUnknown,
            operation: ConversationOperation.send,
            resultCertainty: SendResultCertainty.resultUnknown,
            requestId: _state.activeRequestId,
          ),
        );
      }
    } finally {
      _sendSubscription = null;
    }
  }

  void _handleSendEvent(send_event.ConversationSendEvent event) {
    if (_isDisposed) return;

    switch (event) {
      case send_event.StreamStarted(:final requestId, :final userMessage):
        _flushDelta();
        _reduce(state_event.StreamStarted(requestId, userMessage: userMessage));
      case send_event.AssistantDelta(:final requestId, :final delta):
        _handleDelta(requestId, delta);
      case send_event.AssistantCompleted(:final requestId, :final messages):
        _flushDelta();
        _reduce(
          state_event.AssistantCompleted(
            requestId: requestId,
            messages: messages,
          ),
        );
        _cancelAfterTerminal();
        _recoveryInProgress = false;
        _retryNotBefore = null;
      case send_event.SendFailed(:final requestId, :final failure):
        _flushDelta();
        final normalized = failure.requestId == null && requestId != null
            ? ConversationFailure(
                category: failure.category,
                operation: failure.operation,
                resultCertainty: failure.resultCertainty,
                requestId: requestId,
                retryAfter: failure.retryAfter,
                terminalStreamFailure: failure.terminalStreamFailure,
              )
            : failure;
        _reduce(state_event.SendFailed(normalized));
        if (_state.status == ConversationScreenStatus.sendFailed) {
          _armRetryAfter(normalized.retryAfter);
        }
        if (_state.status == ConversationScreenStatus.sendFailed) {
          _cancelAfterTerminal();
        }
    }
  }

  void _handleDelta(String requestId, String delta) {
    if (_state.status != ConversationScreenStatus.streaming ||
        _state.activeRequestId != requestId) {
      _reduce(
        state_event.AssistantDeltaReceived(requestId: requestId, delta: delta),
      );
      return;
    }

    if (_state.temporaryAssistantText == '' && _pendingDelta.isEmpty) {
      _reduce(
        state_event.AssistantDeltaReceived(requestId: requestId, delta: delta),
      );
      return;
    }

    _pendingDelta += delta;
    _deltaTimer ??= Timer(deltaBatchWindow, _flushDelta);
  }

  void _flushDelta() {
    _deltaTimer?.cancel();
    _deltaTimer = null;
    if (_pendingDelta.isEmpty || _isDisposed) return;

    final delta = _pendingDelta;
    _pendingDelta = '';
    final requestId = _state.activeRequestId;
    if (requestId == null) return;
    _reduce(
      state_event.AssistantDeltaReceived(requestId: requestId, delta: delta),
    );
  }

  void _clearDeltaBatch() {
    _deltaTimer?.cancel();
    _deltaTimer = null;
    _pendingDelta = '';
  }

  void _cancelAfterTerminal() {
    _clearDeltaBatch();
    final subscription = _sendSubscription;
    if (subscription != null) unawaited(subscription.cancel());
  }

  void _applyFailure(ConversationFailure failure) {
    if (_isDisposed) return;
    _reduce(state_event.SendFailed(failure));
    if (_state.status == ConversationScreenStatus.sendFailed) {
      _armRetryAfter(failure.retryAfter);
    }
  }

  void _failInitialLoad(ConversationFailure failure) {
    _reduce(
      state_event.InitialLoadFailed(
        ConversationFailure(
          category: failure.category,
          operation: ConversationOperation.initialLoad,
          resultCertainty: failure.resultCertainty,
        ),
      ),
    );
  }

  bool _isCurrent(int generation) => !_isDisposed && generation == _generation;

  void _reduce(state_event.ConversationStateEvent event) {
    if (_isDisposed) return;

    final result = _reducer.reduce(_state, event);
    if (result is StateTransition) {
      _state = result.nextState;
      notifyListeners();
      return;
    }

    if (result is StateTransitionFailure &&
        (event is state_event.StreamStarted ||
            event is state_event.AssistantDeltaReceived ||
            event is state_event.AssistantCompleted ||
            event is state_event.SendFailed)) {
      final requestId = _state.activeRequestId;
      if (_state.status == ConversationScreenStatus.sending ||
          _state.status == ConversationScreenStatus.streaming) {
        final failure = ConversationFailure(
          category: result.category,
          operation: result.operation,
          resultCertainty: SendResultCertainty.resultUnknown,
          requestId: requestId,
        );
        final fallback = _reducer.reduce(
          _state,
          state_event.SendFailed(failure),
        );
        if (fallback is StateTransition) {
          _state = fallback.nextState;
          notifyListeners();
        }
      }
    }
  }

  @override
  void dispose() {
    _isDisposed = true;
    _generation++;
    _clearDeltaBatch();
    _retryTimer?.cancel();
    final subscription = _sendSubscription;
    _sendSubscription = null;
    if (subscription != null) unawaited(subscription.cancel());
    super.dispose();
  }
}
