import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../application/state/conversation_screen_state.dart';
import '../mapper/conversation_message_view_mapper.dart';
import '../model/conversation_message_view_data.dart';
import '../provider/conversation_providers.dart';
import '../widget/alice_core/alice_core_visual_state.dart';
import '../widget/history/conversation_message_item.dart';
import '../widget/history/date_separator.dart';
import '../widget/history/empty_conversation_view.dart';
import '../widget/history/initial_history_loading_view.dart';
import '../widget/history/initial_load_error_view.dart';
import 'conversation_screen_shell.dart';

final class ConversationScreen extends ConsumerStatefulWidget {
  const ConversationScreen({super.key});

  @override
  ConsumerState<ConversationScreen> createState() => _ConversationScreenState();
}

final class _ConversationScreenState extends ConsumerState<ConversationScreen> {
  final _draftController = TextEditingController();
  final _focusNode = FocusNode();
  final _scrollController = ScrollController();
  Timer? _loadingTimer;
  bool _showLoading = false;
  bool _didPositionInitialPage = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      ref.read(conversationControllerProvider).loadInitial();
      _startLoadingDelay();
    });
  }

  void _startLoadingDelay() {
    _loadingTimer?.cancel();
    _showLoading = false;
    _loadingTimer = Timer(const Duration(milliseconds: 300), () {
      if (mounted) setState(() => _showLoading = true);
    });
  }

  void _reload() {
    ref.read(conversationControllerProvider).loadInitial();
    _startLoadingDelay();
  }

  @override
  void dispose() {
    _loadingTimer?.cancel();
    _draftController.dispose();
    _focusNode.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = ref.watch(conversationControllerProvider);
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        final state = controller.state;
        if (state.status != ConversationScreenStatus.initialLoading) {
          _loadingTimer?.cancel();
        }
        final messageItems = _messageItems(state);
        _positionInitialPage(state);
        return ConversationScreenShell(
          coreVisualState:
              state.status == ConversationScreenStatus.initialLoadFailed
              ? AliceCoreVisualState.unavailable
              : AliceCoreVisualState.idle,
          messageItems: messageItems,
          draftController: _draftController,
          focusNode: _focusNode,
          scrollController: _scrollController,
          composerEnabled: state.status == ConversationScreenStatus.ready,
          sendEnabled: false,
          onSend: () {},
        );
      },
    );
  }

  List<Widget> _messageItems(ConversationScreenState state) {
    if (state.status == ConversationScreenStatus.initialLoading) {
      return _showLoading ? const [InitialHistoryLoadingView()] : const [];
    }
    if (state.status == ConversationScreenStatus.initialLoadFailed) {
      return [InitialLoadErrorView(onReload: _reload, loading: false)];
    }
    if (state.status == ConversationScreenStatus.ready) {
      if (state.messages.isEmpty) return const [EmptyConversationView()];
      final messages = const ConversationMessageViewMapper().mapAll(
        state.messages,
      );
      return _historyItems(messages);
    }
    throw StateError('Unsupported FIP-008 screen state: ${state.status}');
  }

  List<Widget> _historyItems(List<ConversationMessageViewData> messages) {
    final items = <Widget>[];
    DateTime? previousDate;
    for (final message in messages) {
      if (previousDate != message.jstCalendarDate) {
        items.add(DateSeparator(date: message.jstCalendarDate));
        previousDate = message.jstCalendarDate;
      }
      items.add(ConversationMessageItem(message: message));
    }
    return items;
  }

  void _positionInitialPage(ConversationScreenState state) {
    if (_didPositionInitialPage ||
        state.status != ConversationScreenStatus.ready) {
      return;
    }
    _didPositionInitialPage = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _scrollController.hasClients) {
        _scrollController.jumpTo(_scrollController.position.maxScrollExtent);
      }
    });
  }
}
