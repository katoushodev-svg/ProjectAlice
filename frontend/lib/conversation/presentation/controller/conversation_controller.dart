import 'package:flutter/foundation.dart';

import '../../application/error/conversation_failure.dart';
import '../../application/model/message_page.dart';
import '../../application/port/conversation_gateway.dart';
import '../../application/state/conversation_screen_state.dart';
import '../../application/state/conversation_state_event.dart';
import '../../application/state/conversation_state_reducer.dart';
import '../../domain/conversation.dart';

final class ConversationController extends ChangeNotifier {
  ConversationController({
    required this._gateway,
    this._reducer = const ConversationStateReducer(),
  }) : _state = ConversationScreenState.initial();

  final ConversationGateway _gateway;
  final ConversationStateReducer _reducer;
  ConversationScreenState _state;
  bool _isLoading = false;
  bool _isDisposed = false;
  int _generation = 0;

  ConversationScreenState get state => _state;

  Future<void> loadInitial() async {
    if (_isLoading || _isDisposed) {
      return;
    }

    _isLoading = true;
    final generation = ++_generation;
    _reduce(const InitialLoadStarted());

    try {
      final conversationResult = await _gateway.getConversation();
      if (!_isCurrent(generation)) {
        return;
      }
      if (conversationResult is GatewayFailure<Conversation?>) {
        _fail(conversationResult.failure);
        return;
      }

      final conversation =
          (conversationResult as GatewaySuccess<Conversation?>).value;
      final messagesResult = await _gateway.getMessages(
        limit: 50,
        cursor: null,
      );
      if (!_isCurrent(generation)) {
        return;
      }
      if (messagesResult is GatewayFailure<MessagePage>) {
        _fail(messagesResult.failure);
        return;
      }

      final page = (messagesResult as GatewaySuccess<MessagePage>).value;
      _reduce(InitialLoadSucceeded(conversation: conversation, page: page));
    } finally {
      if (_isCurrent(generation)) {
        _isLoading = false;
      }
    }
  }

  void _fail(ConversationFailure failure) {
    _reduce(
      InitialLoadFailed(
        ConversationFailure(
          category: failure.category,
          operation: ConversationOperation.initialLoad,
          resultCertainty: failure.resultCertainty,
        ),
      ),
    );
  }

  bool _isCurrent(int generation) => !_isDisposed && generation == _generation;

  void _reduce(ConversationStateEvent event) {
    final result = _reducer.reduce(_state, event);
    if (result is StateTransition) {
      _state = result.nextState;
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _isDisposed = true;
    _generation++;
    super.dispose();
  }
}
