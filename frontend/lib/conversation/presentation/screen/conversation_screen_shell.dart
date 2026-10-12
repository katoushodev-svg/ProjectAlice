import 'package:flutter/material.dart';

import '../layout/conversation_layout_metrics.dart';
import '../widget/alice_core/alice_core_visual_state.dart';
import '../widget/alice_core_region.dart';
import '../widget/alice_header.dart';
import '../widget/composer_panel.dart';
import '../widget/message_viewport.dart';

/// Pure Presentation shell composing Header, Conversation Body and Composer.
///
/// FIP-007 does not connect this to Application State, HTTP or SSE. All
/// Controllers are owned by the caller and passed in.
class ConversationScreenShell extends StatefulWidget {
  const ConversationScreenShell({
    super.key,
    required this.coreVisualState,
    required this.messageItems,
    required this.draftController,
    required this.focusNode,
    required this.scrollController,
    required this.onSend,
    this.onScrollNotification,
    this.messageViewportKey,
    this.showLatestButton = false,
    this.onLatestTap,
    this.composerEnabled = true,
    this.sendEnabled = true,
    this.validationMessage,
    this.onDraftChanged,
    this.characterCount = 0,
    this.showCharacterCount = false,
  });

  /// At this width, the conversation switches to the desktop layout.
  static const double desktopBreakpoint = 900;

  /// Initial width ratio of the Alice Core pane.
  static const double initialLeftPaneFraction = 0.55;

  /// Minimum and maximum width ratios of the Alice Core pane.
  static const double minLeftPaneFraction = 0.35;
  static const double maxLeftPaneFraction = 0.70;

  /// Width reserved for the draggable divider.
  static const double dividerHitAreaWidth = 7;

  final AliceCoreVisualState coreVisualState;
  final List<Widget> messageItems;
  final TextEditingController draftController;
  final FocusNode focusNode;
  final ScrollController scrollController;
  final VoidCallback onSend;
  final NotificationListenerCallback<ScrollNotification>? onScrollNotification;
  final GlobalKey? messageViewportKey;
  final bool showLatestButton;
  final VoidCallback? onLatestTap;
  final bool composerEnabled;
  final bool sendEnabled;
  final String? validationMessage;
  final ValueChanged<String>? onDraftChanged;
  final int characterCount;
  final bool showCharacterCount;

  @override
  State<ConversationScreenShell> createState() =>
      _ConversationScreenShellState();
}

class _ConversationScreenShellState extends State<ConversationScreenShell> {
  double _leftPaneFraction = ConversationScreenShell.initialLeftPaneFraction;

  Widget _buildComposer() {
    return ComposerPanel(
      draftController: widget.draftController,
      focusNode: widget.focusNode,
      enabled: widget.composerEnabled,
      sendEnabled: widget.sendEnabled,
      validationMessage: widget.validationMessage,
      onDraftChanged: widget.onDraftChanged,
      characterCount: widget.characterCount,
      showCharacterCount: widget.showCharacterCount,
      onSend: widget.onSend,
    );
  }

  Widget _buildConversationBody({
    required bool keyboardVisible,
    required bool showCore,
  }) {
    return _ConversationBody(
      coreVisualState: widget.coreVisualState,
      messageItems: widget.messageItems,
      scrollController: widget.scrollController,
      keyboardVisible: keyboardVisible,
      onScrollNotification: widget.onScrollNotification,
      messageViewportKey: widget.messageViewportKey,
      showLatestButton: widget.showLatestButton,
      onLatestTap: widget.onLatestTap,
      showCore: showCore,
    );
  }

  void _resizeLeftPane({
    required double delta,
    required double availableWidth,
  }) {
    if (availableWidth <= 0) {
      return;
    }

    setState(() {
      _leftPaneFraction = (_leftPaneFraction + delta / availableWidth)
          .clamp(
            ConversationScreenShell.minLeftPaneFraction,
            ConversationScreenShell.maxLeftPaneFraction,
          )
          .toDouble();
    });
  }

  Widget _buildDesktopLayout({
    required double width,
    required bool keyboardVisible,
  }) {
    const dividerWidth = ConversationScreenShell.dividerHitAreaWidth;

    final availableWidth = width - dividerWidth;

    final leftWidth = (availableWidth * _leftPaneFraction)
        .clamp(
          availableWidth * ConversationScreenShell.minLeftPaneFraction,
          availableWidth * ConversationScreenShell.maxLeftPaneFraction,
        )
        .toDouble();

    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          width: leftWidth,
          child: LayoutBuilder(
            builder: (context, paneConstraints) {
              final coreSize =
                  (paneConstraints.maxWidth < paneConstraints.maxHeight
                      ? paneConstraints.maxWidth
                      : paneConstraints.maxHeight) *
                  0.82;

              return Center(
                child: AliceCoreRegion(
                  visualState: widget.coreVisualState,
                  height: coreSize,
                ),
              );
            },
          ),
        ),

        // A wider hit area makes the divider easier to grab.
        SizedBox(
          width: dividerWidth,
          child: MouseRegion(
            cursor: SystemMouseCursors.resizeLeftRight,
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onHorizontalDragUpdate: (details) {
                _resizeLeftPane(
                  delta: details.delta.dx,
                  availableWidth: availableWidth,
                );
              },
              child: Center(
                child: Container(
                  width: 1,
                  height: double.infinity,
                  color: Theme.of(context).dividerColor,
                ),
              ),
            ),
          ),
        ),

        Expanded(
          child: Column(
            children: [
              Expanded(
                child: _buildConversationBody(
                  keyboardVisible: keyboardVisible,
                  showCore: false,
                ),
              ),

              // Keep the compact Composer and bottom spacing.
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: SizedBox(height: 68, child: _buildComposer()),
              ),
            ],
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final keyboardVisible = MediaQuery.of(context).viewInsets.bottom > 0;

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        child: Column(
          children: [
            const AliceHeader(),
            Expanded(
              child: LayoutBuilder(
                builder: (context, constraints) {
                  if (constraints.maxWidth <
                      ConversationScreenShell.desktopBreakpoint) {
                    // Preserve the existing mobile/tablet layout.
                    return Column(
                      children: [
                        Expanded(
                          child: _buildConversationBody(
                            keyboardVisible: keyboardVisible,
                            showCore: true,
                          ),
                        ),
                        _buildComposer(),
                      ],
                    );
                  }

                  return _buildDesktopLayout(
                    width: constraints.maxWidth,
                    keyboardVisible: keyboardVisible,
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Splits available body height between Alice Core and the message viewport.
///
/// On desktop, [showCore] is false because Alice Core is rendered in the
/// separate left pane.
class _ConversationBody extends StatelessWidget {
  const _ConversationBody({
    required this.coreVisualState,
    required this.messageItems,
    required this.scrollController,
    required this.keyboardVisible,
    required this.onScrollNotification,
    required this.messageViewportKey,
    required this.showLatestButton,
    required this.onLatestTap,
    required this.showCore,
  });

  final AliceCoreVisualState coreVisualState;
  final List<Widget> messageItems;
  final ScrollController scrollController;
  final bool keyboardVisible;
  final NotificationListenerCallback<ScrollNotification>? onScrollNotification;
  final GlobalKey? messageViewportKey;
  final bool showLatestButton;
  final VoidCallback? onLatestTap;
  final bool showCore;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final bodyHeight = constraints.maxHeight;

        final coreHeight = ConversationLayoutMetrics.coreHeight(
          bodyHeight,
          keyboardVisible: keyboardVisible,
        );

        final coreVisible =
            showCore &&
            ConversationLayoutMetrics.isCoreVisible(
              bodyHeight: bodyHeight,
              keyboardVisible: keyboardVisible,
            );

        return Stack(
          children: [
            Column(
              children: [
                if (showCore)
                  AnimatedContainer(
                    duration: ConversationLayoutMetrics.coreTransitionDuration,
                    curve: Curves.easeInOut,
                    height: coreVisible ? coreHeight : 0,
                    child: coreVisible
                        ? AliceCoreRegion(
                            visualState: coreVisualState,
                            height: coreHeight,
                          )
                        : null,
                  ),
                Expanded(
                  child: NotificationListener<ScrollNotification>(
                    onNotification: onScrollNotification,
                    child: MessageViewport(
                      key: messageViewportKey,
                      controller: scrollController,
                      items: messageItems,
                    ),
                  ),
                ),
              ],
            ),
            if (showLatestButton && onLatestTap != null)
              Positioned(
                right: 16,
                bottom: 12,
                child: Semantics(
                  label: '最新のメッセージへ移動',
                  button: true,
                  onTap: onLatestTap,
                  excludeSemantics: true,
                  child: SizedBox(
                    height: 48,
                    child: Material(
                      color: Theme.of(context).colorScheme.surface,
                      shape: const StadiumBorder(),
                      child: Tooltip(
                        message: '最新のメッセージへ移動',
                        child: TextButton.icon(
                          onPressed: onLatestTap,
                          icon: const Icon(Icons.south),
                          label: const Text('最新へ'),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}
