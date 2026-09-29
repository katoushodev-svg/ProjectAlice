import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../application/state/conversation_screen_state.dart';
import '../../application/error/conversation_failure.dart';
import '../controller/conversation_controller.dart';
import '../mapper/conversation_message_view_mapper.dart';
import '../model/conversation_message_view_data.dart';
import '../provider/conversation_providers.dart';
import '../widget/alice_core/alice_core_visual_state.dart';
import '../widget/history/conversation_message_item.dart';
import '../widget/history/date_separator.dart';
import '../widget/history/empty_conversation_view.dart';
import '../widget/history/initial_history_loading_view.dart';
import '../widget/history/initial_load_error_view.dart';
import '../widget/pending_user_message.dart';
import '../widget/streaming_assistant_message.dart';
import '../widget/thinking_indicator.dart';
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
  final _messageViewportKey = GlobalKey();
  final _loadOlderControlKey = GlobalKey();
  final Map<String, GlobalKey> _messageKeys = <String, GlobalKey>{};

  Timer? _loadingTimer;
  bool _showLoading = false;
  bool _didPositionInitialPage = false;
  String? _validationMessage;
  String? _restoredFailureKey;
  bool _insideOlderThreshold = false;
  _MessageScrollAnchor? _pendingAnchor;
  _MessageScrollAnchor? _resizeAnchor;
  int _previousMessageCount = 0;
  int _anchorRestoreGeneration = 0;
  EdgeInsets? _lastViewInsets;
  Size? _lastViewportSize;
  double? _lastTextScale;
  bool _followingLatest = true;
  bool _hasUnseenLatestUpdate = false;
  bool _programmaticScrollInProgress = false;
  String? _latestSignature;
  bool _showManualOlderControl = false;
  bool _olderControlMeasurementPending = false;
  String? _lastOlderControlMeasurementKey;

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
    _validationMessage = null;
    ref.read(conversationControllerProvider).loadInitial();
    _startLoadingDelay();
  }

  void _onDraftChanged(String value) {
    final validation = ConversationController.validateContent(value);
    final message = switch (validation) {
      ConversationSendValidation.valid => null,
      ConversationSendValidation.empty => null,
      ConversationSendValidation.whitespaceOnly => null,
      ConversationSendValidation.tooLong => '10,000文字以内で入力してください',
    };

    setState(() => _validationMessage = message);
  }

  void _send() {
    final content = _draftController.text;
    final validation = ConversationController.validateContent(content);

    if (validation != ConversationSendValidation.valid) {
      if (validation == ConversationSendValidation.tooLong) {
        setState(() {
          _validationMessage = '10,000文字以内で入力してください';
        });
      }
      return;
    }

    final controller = ref.read(conversationControllerProvider);
    if (controller.send(content)) {
      _followingLatest = true;
      _hasUnseenLatestUpdate = false;
      _draftController.clear();
      setState(() => _validationMessage = null);
    }
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
  void didChangeDependencies() {
    super.didChangeDependencies();
    final media = MediaQuery.of(context);
    final layoutChanged =
        (_lastViewInsets != null && _lastViewInsets != media.viewInsets) ||
        (_lastViewportSize != null && _lastViewportSize != media.size) ||
        (_lastTextScale != null && _lastTextScale != media.textScaler.scale(1));
    _lastViewInsets = media.viewInsets;
    _lastViewportSize = media.size;
    _lastTextScale = media.textScaler.scale(1);
    if (!layoutChanged) return;

    _resizeAnchor = _followingLatest ? null : _captureVisibleAnchor();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scrollController.hasClients) return;
      _programmaticScrollInProgress = true;
      if (_followingLatest) {
        _scrollController.jumpTo(_scrollController.position.maxScrollExtent);
      } else {
        _restoreAnchorFromRenderBox(_resizeAnchor);
      }
      _programmaticScrollInProgress = false;
      _resizeAnchor = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final controller = ref.watch(conversationControllerProvider);

    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        final state = controller.state;
        _trackLatestUpdate(state);
        _restoreFailedInput(state);

        if (state.status != ConversationScreenStatus.initialLoading) {
          _loadingTimer?.cancel();
        }

        final content = _draftController.text;
        final validation = ConversationController.validateContent(content);
        final sendEnabled =
            (state.status == ConversationScreenStatus.ready ||
                _allowsInputCorrection(state)) &&
            validation == ConversationSendValidation.valid;
        final showCounter = content.runes.length >= 9000;

        return ConversationScreenShell(
          coreVisualState: _coreVisualState(state),
          messageItems: _messageItems(state),
          draftController: _draftController,
          focusNode: _focusNode,
          scrollController: _scrollController,
          composerEnabled:
              state.status == ConversationScreenStatus.ready ||
              _allowsInputCorrection(state),
          sendEnabled: sendEnabled,
          validationMessage: _validationMessage,
          characterCount: content.runes.length,
          showCharacterCount: showCounter,
          onDraftChanged: _onDraftChanged,
          onSend: _send,
          onScrollNotification: _onScrollNotification,
          messageViewportKey: _messageViewportKey,
          showLatestButton: !_followingLatest && _hasUnseenLatestUpdate,
          onLatestTap: _jumpToLatest,
        );
      },
    );
  }

  bool _allowsInputCorrection(ConversationScreenState state) =>
      state.status == ConversationScreenStatus.sendFailed &&
      (state.failure?.category == ConversationFailureCategory.validation ||
          state.failure?.category ==
              ConversationFailureCategory.requestBodyTooLarge);

  AliceCoreVisualState _coreVisualState(ConversationScreenState state) {
    return switch (state.status) {
      ConversationScreenStatus.sending => AliceCoreVisualState.thinking,
      ConversationScreenStatus.streaming => AliceCoreVisualState.streaming,
      ConversationScreenStatus.initialLoadFailed =>
        AliceCoreVisualState.unavailable,
      _ => AliceCoreVisualState.idle,
    };
  }

  void _restoreFailedInput(ConversationScreenState state) {
    if (!_allowsInputCorrection(state) || state.pendingSend == null) return;
    final failureKey = state.pendingSend!.idempotencyKey;
    if (_restoredFailureKey == failureKey || _draftController.text.isNotEmpty) {
      return;
    }
    _restoredFailureKey = failureKey;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || _draftController.text.isNotEmpty) return;
      _draftController.text = state.pendingSend!.content;
      _draftController.selection = TextSelection.collapsed(
        offset: _draftController.text.length,
      );
      setState(() {});
    });
  }

  bool _onScrollNotification(ScrollNotification notification) {
    if (notification is ScrollUpdateNotification &&
        notification.metrics.axis == Axis.vertical) {
      final metrics = notification.metrics;
      final wasFollowingLatest = _followingLatest;
      if (notification.dragDetails != null && !_programmaticScrollInProgress) {
        if (metrics.extentAfter <= 120) {
          _followingLatest = true;
          _hasUnseenLatestUpdate = false;
        } else {
          _followingLatest = false;
        }
      }

      if (wasFollowingLatest != _followingLatest) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) setState(() {});
        });
      }

      if (metrics.extentAfter > 120) {
        _insideOlderThreshold = false;
      }

      if (metrics.extentBefore > 200) {
        _insideOlderThreshold = false;
      } else if (!_insideOlderThreshold &&
          notification.dragDetails != null &&
          metrics.extentBefore <= 200) {
        _insideOlderThreshold = true;
        final controller = ref.read(conversationControllerProvider);
        final state = controller.state;
        if (state.status == ConversationScreenStatus.ready &&
            state.pagination.hasMore &&
            state.failure == null) {
          _captureAnchor();
          unawaited(controller.loadOlder());
        }
      }
    }
    return false;
  }

  void _trackLatestUpdate(ConversationScreenState state) {
    final lastId = state.messages.isEmpty ? '' : state.messages.last.id;
    final signature = Object.hash(
      lastId,
      state.temporaryAssistantText?.hashCode,
      state.pendingSend != null,
    ).toString();
    final previous = _latestSignature;
    _latestSignature = signature;
    if (previous == null ||
        previous == signature ||
        state.status == ConversationScreenStatus.initialLoading) {
      return;
    }

    if (_followingLatest) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || !_scrollController.hasClients) return;
        _programmaticScrollInProgress = true;
        _scrollController.jumpTo(_scrollController.position.maxScrollExtent);
        _programmaticScrollInProgress = false;
      });
    } else if (!_hasUnseenLatestUpdate) {
      _hasUnseenLatestUpdate = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) setState(() {});
      });
    }
  }

  void _jumpToLatest() {
    _followingLatest = true;
    _hasUnseenLatestUpdate = false;
    if (!_scrollController.hasClients) {
      setState(() {});
      return;
    }
    _programmaticScrollInProgress = true;
    final reduceMotion = MediaQuery.of(context).disableAnimations;
    if (reduceMotion) {
      _scrollController.jumpTo(_scrollController.position.maxScrollExtent);
      _programmaticScrollInProgress = false;
    } else {
      unawaited(
        _scrollController
            .animateTo(
              _scrollController.position.maxScrollExtent,
              duration: const Duration(milliseconds: 200),
              curve: Curves.easeOut,
            )
            .whenComplete(() => _programmaticScrollInProgress = false),
      );
    }
    setState(() {});
  }

  void _captureAnchor() {
    final state = ref.read(conversationControllerProvider).state;
    final visibleAnchor = _captureVisibleAnchor();
    _previousMessageCount = state.messages.length;
    _anchorRestoreGeneration++;
    if (visibleAnchor == null) {
      _pendingAnchor = null;
    } else {
      _pendingAnchor = visibleAnchor.withPaginationContext(
        messageIndex: state.messages.indexWhere(
          (message) => message.id == visibleAnchor.messageId,
        ),
        messageCount: state.messages.length,
      );
    }

    debugPrint(
      '[ANCHOR] CAPTURE '
      'messageId=${_pendingAnchor?.messageId ?? "null"} '
      'viewportOffset=${_pendingAnchor?.viewportOffset ?? "null"} '
      'previousMessageCount=$_previousMessageCount',
    );
  }

  _MessageScrollAnchor? _captureVisibleAnchor() {
    if (!_scrollController.hasClients) {
      debugPrint('[ANCHOR] CAPTURE_VISIBLE skipped: no scroll clients');
      return null;
    }

    final viewport = _messageViewportKey.currentContext?.findRenderObject();

    if (viewport is! RenderBox) {
      debugPrint('[ANCHOR] CAPTURE_VISIBLE skipped: viewport is not RenderBox');
      return null;
    }

    final viewportTop = viewport.localToGlobal(Offset.zero).dy;

    for (final entry in _messageKeys.entries) {
      final renderObject = entry.value.currentContext?.findRenderObject();

      if (renderObject is! RenderBox || !renderObject.attached) {
        continue;
      }

      final top = renderObject.localToGlobal(Offset.zero).dy - viewportTop;

      if (top + renderObject.size.height > 0) {
        final anchor = _MessageScrollAnchor(
          entry.key,
          top,
          scrollPixels: _scrollController.position.pixels,
          maxScrollExtent: _scrollController.position.maxScrollExtent,
        );

        debugPrint(
          '[ANCHOR] CAPTURE_VISIBLE found '
          'messageId=${anchor.messageId} '
          'viewportOffset=${anchor.viewportOffset} '
          'scrollPixels=${_scrollController.position.pixels}',
        );

        return anchor;
      }
    }

    debugPrint('[ANCHOR] CAPTURE_VISIBLE skipped: no visible message');
    return null;
  }

  void _restoreAnchorIfNeeded(ConversationScreenState state) {
    final anchor = _pendingAnchor;

    debugPrint(
      '[ANCHOR] RESTORE_REQUEST '
      'anchor=${anchor?.messageId ?? "null"} '
      'anchorOffset=${anchor?.viewportOffset ?? "null"} '
      'currentMessageCount=${state.messages.length} '
      'previousMessageCount=$_previousMessageCount '
      'status=${state.status}',
    );

    if (anchor == null || state.messages.length <= _previousMessageCount) {
      debugPrint(
        '[ANCHOR] RESTORE_REQUEST skipped '
        'reason=${anchor == null ? "no_anchor" : "message_count_not_increased"}',
      );
      return;
    }

    _pendingAnchor = null;
    final currentAnchorIndex = state.messages.indexWhere(
      (message) => message.id == anchor.messageId,
    );
    final addedMessageCount = state.messages.length - anchor.messageCount;
    final expectedAnchorIndex = anchor.messageIndex + addedMessageCount;
    if (currentAnchorIndex != expectedAnchorIndex) {
      debugPrint(
        '[ANCHOR] RESTORE_EXTENT skipped: '
        'messageId=${anchor.messageId} '
        'expectedIndex=$expectedAnchorIndex '
        'currentIndex=$currentAnchorIndex '
        'addedMessageCount=$addedMessageCount',
      );
      return;
    }

    final generation = _anchorRestoreGeneration;

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted ||
          !_scrollController.hasClients ||
          generation != _anchorRestoreGeneration) {
        debugPrint(
          '[ANCHOR] RESTORE skipped: '
          'mounted=$mounted '
          'hasClients=${_scrollController.hasClients} '
          'generation=$generation '
          'currentGeneration=$_anchorRestoreGeneration',
        );
        return;
      }

      _programmaticScrollInProgress = true;

      debugPrint(
        '[ANCHOR] RESTORE_START '
        'messageId=${anchor.messageId} '
        'anchorOffset=${anchor.viewportOffset} '
        'scrollPixels=${_scrollController.position.pixels} '
        'maxExtent=${_scrollController.position.maxScrollExtent}',
      );

      _restorePaginationAnchor(anchor);

      debugPrint(
        '[ANCHOR] RESTORE_END '
        'scrollPixels=${_scrollController.position.pixels} '
        'maxExtent=${_scrollController.position.maxScrollExtent}',
      );

      _programmaticScrollInProgress = false;
    });
  }

  void _restorePaginationAnchor(_MessageScrollAnchor anchor) {
    if (!_scrollController.hasClients) {
      debugPrint(
        '[ANCHOR] RESTORE_CORE skipped: '
        'anchor=${anchor.messageId} '
        'hasClients=${_scrollController.hasClients}',
      );
      return;
    }

    final position = _scrollController.position;
    final extentGrowth = position.maxScrollExtent - anchor.maxScrollExtent;
    final rawTarget = anchor.scrollPixels + extentGrowth;
    final target = rawTarget
        .clamp(position.minScrollExtent, position.maxScrollExtent)
        .toDouble();

    debugPrint(
      '[ANCHOR] RESTORE_EXTENT '
      'messageId=${anchor.messageId} '
      'anchorPixels=${anchor.scrollPixels} '
      'anchorMaxExtent=${anchor.maxScrollExtent} '
      'currentPixels=${position.pixels} '
      'currentMaxExtent=${position.maxScrollExtent} '
      'extentGrowth=$extentGrowth '
      'rawTarget=$rawTarget '
      'target=$target '
      'minExtent=${position.minScrollExtent} '
      'maxExtent=${position.maxScrollExtent}',
    );

    if (rawTarget != target) {
      debugPrint(
        '[ANCHOR] RESTORE_CLAMPED '
        'rawTarget=$rawTarget '
        'target=$target '
        'maxExtent=${position.maxScrollExtent}',
      );
    }

    position.jumpTo(target);

    debugPrint(
      '[ANCHOR] RESTORE_JUMPED '
      'messageId=${anchor.messageId} '
      'finalPixels=${position.pixels}',
    );
  }

  void _restoreAnchorFromRenderBox(_MessageScrollAnchor? anchor) {
    if (anchor == null || !_scrollController.hasClients) return;

    final viewport = _messageViewportKey.currentContext?.findRenderObject();
    final message = _messageKeys[anchor.messageId]?.currentContext
        ?.findRenderObject();
    if (viewport is! RenderBox || message is! RenderBox || !message.attached) {
      debugPrint(
        '[ANCHOR] RESIZE_RESTORE skipped: '
        'messageId=${anchor.messageId} '
        'messageAttached=${message is RenderBox && message.attached}',
      );
      return;
    }

    final viewportTop = viewport.localToGlobal(Offset.zero).dy;
    final currentOffset = message.localToGlobal(Offset.zero).dy - viewportTop;
    final position = _scrollController.position;
    final target = (position.pixels + currentOffset - anchor.viewportOffset)
        .clamp(position.minScrollExtent, position.maxScrollExtent)
        .toDouble();
    position.jumpTo(target);
  }

  List<Widget> _messageItems(ConversationScreenState state) {
    final items = <Widget>[];

    _scheduleOlderControlVisibilityMeasurement(state);

    if (state.status == ConversationScreenStatus.reconcilingHistory) {
      _pendingAnchor = null;
      items.add(const _LatestHistoryLoadingRow());
    } else if (state.pagination.hasMore && state.messages.isNotEmpty) {
      if (state.status == ConversationScreenStatus.loadingOlder) {
        items.add(const _OlderLoadingRow());
      } else if (state.failure?.operation == ConversationOperation.pagination) {
        final needsLatestReconciliation =
            state.failure?.category ==
            ConversationFailureCategory.invalidCursor;
        items.add(
          _OlderFailureRow(
            actionLabel: needsLatestReconciliation ? '会話を再読み込み' : '再試行',
            onRetry: () {
              _captureAnchor();
              final controller = ref.read(conversationControllerProvider);
              unawaited(
                needsLatestReconciliation
                    ? controller.reloadLatestHistory()
                    : controller.loadOlder(),
              );
            },
          ),
        );
      } else if (_showManualOlderControl) {
        items.add(
          _LoadOlderRow(
            key: _loadOlderControlKey,
            onPressed: () {
              _captureAnchor();
              unawaited(ref.read(conversationControllerProvider).loadOlder());
            },
          ),
        );
      }
    }

    if (state.status == ConversationScreenStatus.initialLoading &&
        state.messages.isEmpty) {
      if (_showLoading) {
        items.add(const InitialHistoryLoadingView());
      }
      return items;
    }

    if (state.status == ConversationScreenStatus.initialLoading &&
        state.messages.isNotEmpty) {
      items.add(const _SyncingHistoryRow());
    }

    if (state.status == ConversationScreenStatus.initialLoadFailed) {
      items.add(InitialLoadErrorView(onReload: _reload, loading: false));
      return items;
    }

    if (state.messages.isNotEmpty) {
      if (!state.pagination.hasMore) {
        items.add(const _ConversationBeginningRow());
      }
      final messages = const ConversationMessageViewMapper().mapAll(
        state.messages,
      );
      items.addAll(_historyItems(messages));
    } else if (state.status == ConversationScreenStatus.ready &&
        state.pendingSend == null) {
      items.add(const EmptyConversationView());
    }

    final pending = state.pendingSend;
    final pendingAlreadyCanonical =
        pending != null &&
        state.canonicalPendingUserId != null &&
        state.messages.any(
          (message) => message.id == state.canonicalPendingUserId,
        );
    if (pending != null &&
        !pendingAlreadyCanonical &&
        (state.status == ConversationScreenStatus.sending ||
            state.status == ConversationScreenStatus.streaming ||
            state.status == ConversationScreenStatus.sendFailed)) {
      items.add(PendingUserMessage(message: pending));
    }

    if (state.status == ConversationScreenStatus.sending) {
      items.add(const ThinkingIndicator());
    } else if ((state.status == ConversationScreenStatus.streaming ||
            state.status == ConversationScreenStatus.sendFailed) &&
        state.temporaryAssistantText != null &&
        state.temporaryAssistantText!.isNotEmpty) {
      items.add(
        state.status == ConversationScreenStatus.sendFailed
            ? _PartialFailureMessage(text: state.temporaryAssistantText!)
            : StreamingAssistantMessage(text: state.temporaryAssistantText!),
      );
    }

    if (state.status == ConversationScreenStatus.sendFailed &&
        state.failure != null) {
      items.add(
        _SendFailureView(
          failure: state.failure!,
          recoveryInProgress: controllerRecoveryInProgress,
          canCheckResult: controllerCanCheckResult,
          onRetry: () =>
              ref.read(conversationControllerProvider).retrySameSend(),
          onCheckResult: () =>
              ref.read(conversationControllerProvider).checkResult(),
          onReload: () => unawaited(
            ref.read(conversationControllerProvider).reloadConversation(),
          ),
          onSendNew: () =>
              ref.read(conversationControllerProvider).sendSameContentAsNew(),
        ),
      );
    }

    if (state.status == ConversationScreenStatus.ready) {
      debugPrint(
        '[ANCHOR] MESSAGE_ITEMS_READY '
        'messageCount=${state.messages.length} '
        'hasMore=${state.pagination.hasMore} '
        'pendingAnchor=${_pendingAnchor?.messageId ?? "null"} '
        'previousMessageCount=$_previousMessageCount '
        'scrollPixels=${_scrollController.hasClients ? _scrollController.position.pixels : "no-client"} '
        'maxExtent=${_scrollController.hasClients ? _scrollController.position.maxScrollExtent : "no-client"}',
      );
      _restoreAnchorIfNeeded(state);
      _positionInitialPage(state);
    }

    return items;
  }

  void _scheduleOlderControlVisibilityMeasurement(
    ConversationScreenState state,
  ) {
    final measurementKey =
        '${state.status}:${state.messages.length}:${state.pagination.hasMore}:${state.failure?.category}';
    if (_lastOlderControlMeasurementKey == measurementKey ||
        _olderControlMeasurementPending) {
      return;
    }
    if (state.status != ConversationScreenStatus.ready ||
        !state.pagination.hasMore ||
        state.messages.isEmpty ||
        state.failure != null) {
      _lastOlderControlMeasurementKey = measurementKey;
      if (!state.pagination.hasMore && _showManualOlderControl) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) setState(() => _showManualOlderControl = false);
        });
      }
      return;
    }

    _olderControlMeasurementPending = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _olderControlMeasurementPending = false;
      if (!mounted || !_scrollController.hasClients) return;
      _lastOlderControlMeasurementKey = measurementKey;
      final row = _loadOlderControlKey.currentContext?.findRenderObject();
      final rowHeight = row is RenderBox && row.attached
          ? row.size.height
          : 0.0;
      final extentWithoutControl =
          _scrollController.position.maxScrollExtent - rowHeight;
      final shouldShow = extentWithoutControl <= 1;
      if (shouldShow == _showManualOlderControl) return;
      setState(() => _showManualOlderControl = shouldShow);
      if (_followingLatest) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!mounted || !_scrollController.hasClients) return;
          _programmaticScrollInProgress = true;
          _scrollController.jumpTo(_scrollController.position.maxScrollExtent);
          _programmaticScrollInProgress = false;
        });
      }
    });
  }

  bool get controllerRecoveryInProgress =>
      ref.read(conversationControllerProvider).recoveryInProgress;

  bool get controllerCanCheckResult =>
      ref.read(conversationControllerProvider).canCheckResult;

  List<Widget> _historyItems(List<ConversationMessageViewData> messages) {
    final currentIds = messages.map((message) => message.messageId).toSet();
    _messageKeys.removeWhere((id, _) => !currentIds.contains(id));
    final items = <Widget>[];
    DateTime? previousDate;

    for (final message in messages) {
      if (previousDate != message.jstCalendarDate) {
        items.add(DateSeparator(date: message.jstCalendarDate));
        previousDate = message.jstCalendarDate;
      }

      final key = _messageKeys.putIfAbsent(message.messageId, GlobalKey.new);

      items.add(
        KeyedSubtree(
          key: key,
          child: ConversationMessageItem(message: message),
        ),
      );
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

final class _MessageScrollAnchor {
  const _MessageScrollAnchor(
    this.messageId,
    this.viewportOffset, {
    required this.scrollPixels,
    required this.maxScrollExtent,
    this.messageIndex = 0,
    this.messageCount = 0,
  });

  final String messageId;
  final double viewportOffset;
  final double scrollPixels;
  final double maxScrollExtent;
  final int messageIndex;
  final int messageCount;

  _MessageScrollAnchor withPaginationContext({
    required int messageIndex,
    required int messageCount,
  }) => _MessageScrollAnchor(
    messageId,
    viewportOffset,
    scrollPixels: scrollPixels,
    maxScrollExtent: maxScrollExtent,
    messageIndex: messageIndex,
    messageCount: messageCount,
  );
}

class _SyncingHistoryRow extends StatelessWidget {
  const _SyncingHistoryRow();

  @override
  Widget build(BuildContext context) => Semantics(
    liveRegion: true,
    label: '会話を再読み込みしています',
    child: Padding(
      padding: EdgeInsets.all(12),
      child: Center(child: CircularProgressIndicator()),
    ),
  );
}

class _PartialFailureMessage extends StatelessWidget {
  const _PartialFailureMessage({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Semantics(
      label: 'Alice、途中までの回答: $text',
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          border: Border.all(color: Theme.of(context).colorScheme.error),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [const Text('途中までの回答'), SelectableText(text)],
        ),
      ),
    ),
  );
}

class _SendFailureView extends StatelessWidget {
  const _SendFailureView({
    required this.failure,
    required this.recoveryInProgress,
    required this.canCheckResult,
    required this.onRetry,
    required this.onCheckResult,
    required this.onReload,
    required this.onSendNew,
  });

  final ConversationFailure failure;
  final bool recoveryInProgress;
  final bool canCheckResult;
  final VoidCallback onRetry;
  final VoidCallback onCheckResult;
  final VoidCallback onReload;
  final VoidCallback onSendNew;

  @override
  Widget build(BuildContext context) {
    final action =
        failure.category == ConversationFailureCategory.validation ||
            failure.category == ConversationFailureCategory.requestBodyTooLarge
        ? null
        : failure.terminalStreamFailure
        ? ('同じ内容でもう一度送る', onSendNew)
        : switch (failure.category) {
            ConversationFailureCategory.resultUnknown ||
            ConversationFailureCategory.requestInProgress => (
              '結果を確認',
              onCheckResult,
            ),
            ConversationFailureCategory.conversationBusy ||
            ConversationFailureCategory.serverFailure ||
            ConversationFailureCategory.networkUnavailable => (
              'もう一度試す',
              onRetry,
            ),
            _ => ('会話を再読み込み', onReload),
          };

    final enabled =
        action != null &&
        !recoveryInProgress &&
        (action.$1 != '結果を確認' || canCheckResult);

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.errorContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Semantics(
            liveRegion: true,
            label: _failureTitle(failure),
            child: Text(
              _failureTitle(failure),
              style: Theme.of(context).textTheme.titleSmall,
            ),
          ),
          if (failure.terminalStreamFailure &&
              failure.resultCertainty == SendResultCertainty.knownFailed)
            const Text('Alice、途中までの回答'),
          const SizedBox(height: 8),
          if (action != null)
            OutlinedButton(
              onPressed: enabled ? action.$2 : null,
              child: Text(recoveryInProgress ? '処理しています' : action.$1),
            ),
        ],
      ),
    );
  }

  String _failureTitle(ConversationFailure failure) {
    if (failure.terminalStreamFailure) {
      return switch (failure.category) {
        ConversationFailureCategory.generationFailed => 'Aliceの回答を生成できませんでした。',
        ConversationFailureCategory.responseTimeout => 'Aliceの回答がタイムアウトしました。',
        ConversationFailureCategory.messageSaveFailed => '回答を会話履歴へ保存できませんでした。',
        ConversationFailureCategory.requestInterrupted => '処理を最後まで確認できませんでした。',
        _ => '予期しない問題が発生しました。',
      };
    }

    return switch (failure.category) {
      ConversationFailureCategory.validation => 'メッセージは10,000文字以内で入力してください。',
      ConversationFailureCategory.requestBodyTooLarge =>
        'メッセージのデータ量が大きすぎます。内容を短くしてください。',
      ConversationFailureCategory.resultUnknown => '送信結果を確認できませんでした。',
      ConversationFailureCategory.requestInProgress => 'メッセージはまだ処理中です。',
      ConversationFailureCategory.conversationBusy =>
        'Aliceは別の回答を作成中です。完了後にもう一度お試しください。',
      ConversationFailureCategory.serverFailure => 'Aliceを一時的に利用できません。',
      ConversationFailureCategory.idempotencyConflict =>
        '送信状態に矛盾が見つかりました。会話を再読み込みしてください。',
      ConversationFailureCategory.protocolViolation ||
      ConversationFailureCategory.responseTooLarge ||
      ConversationFailureCategory.sseFrameTooLarge ||
      ConversationFailureCategory.sseStreamTooLarge ||
      ConversationFailureCategory.tooManySseEvents => '表示を同期できませんでした。',
      ConversationFailureCategory.networkUnavailable => 'メッセージを送信できませんでした。',
      _ => 'メッセージを送信できませんでした。',
    };
  }
}

class _LoadOlderRow extends StatelessWidget {
  const _LoadOlderRow({super.key, required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => Center(
    child: TextButton(onPressed: onPressed, child: const Text('以前のメッセージを読み込む')),
  );
}

class _LatestHistoryLoadingRow extends StatelessWidget {
  const _LatestHistoryLoadingRow();

  @override
  Widget build(BuildContext context) => Semantics(
    liveRegion: true,
    label: '会話を再読み込みしています',
    child: const Padding(
      padding: EdgeInsets.all(16),
      child: Center(child: CircularProgressIndicator()),
    ),
  );
}

class _ConversationBeginningRow extends StatelessWidget {
  const _ConversationBeginningRow();

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 12),
    child: Center(child: Semantics(header: true, child: Text('会話の始まり'))),
  );
}

class _OlderLoadingRow extends StatelessWidget {
  const _OlderLoadingRow();

  @override
  Widget build(BuildContext context) => Semantics(
    liveRegion: true,
    label: '以前のメッセージを読み込んでいます',
    child: Padding(
      padding: EdgeInsets.all(16),
      child: Center(child: CircularProgressIndicator()),
    ),
  );
}

class _OlderFailureRow extends StatelessWidget {
  const _OlderFailureRow({required this.onRetry, required this.actionLabel});

  final VoidCallback onRetry;
  final String actionLabel;

  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Text('以前のメッセージを読み込めませんでした'),
        TextButton(onPressed: onRetry, child: Text(actionLabel)),
      ],
    ),
  );
}
