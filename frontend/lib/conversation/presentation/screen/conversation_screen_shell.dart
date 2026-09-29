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
class ConversationScreenShell extends StatelessWidget {
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
  Widget build(BuildContext context) {
    // Read Keyboard Insets before Scaffold's resizeToAvoidBottomInset
    // consumes/zeroes viewInsets for its body's descendants.
    final keyboardVisible = MediaQuery.of(context).viewInsets.bottom > 0;

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        child: Column(
          children: [
            const AliceHeader(),
            Expanded(
              child: _ConversationBody(
                coreVisualState: coreVisualState,
                messageItems: messageItems,
                scrollController: scrollController,
                keyboardVisible: keyboardVisible,
                onScrollNotification: onScrollNotification,
                messageViewportKey: messageViewportKey,
                showLatestButton: showLatestButton,
                onLatestTap: onLatestTap,
              ),
            ),
            ComposerPanel(
              draftController: draftController,
              focusNode: focusNode,
              enabled: composerEnabled,
              sendEnabled: sendEnabled,
              validationMessage: validationMessage,
              onDraftChanged: onDraftChanged,
              characterCount: characterCount,
              showCharacterCount: showCharacterCount,
              onSend: onSend,
            ),
          ],
        ),
      ),
    );
  }
}

/// Splits available Body height between the non-scrolling Alice Core Region
/// and the single scrollable Message Viewport (FIP-007 Section 12).
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
  });

  final AliceCoreVisualState coreVisualState;
  final List<Widget> messageItems;
  final ScrollController scrollController;
  final bool keyboardVisible;
  final NotificationListenerCallback<ScrollNotification>? onScrollNotification;
  final GlobalKey? messageViewportKey;
  final bool showLatestButton;
  final VoidCallback? onLatestTap;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final bodyHeight = constraints.maxHeight;
        final coreHeight = ConversationLayoutMetrics.coreHeight(
          bodyHeight,
          keyboardVisible: keyboardVisible,
        );
        final coreVisible = ConversationLayoutMetrics.isCoreVisible(
          bodyHeight: bodyHeight,
          keyboardVisible: keyboardVisible,
        );

        return Stack(
          children: [
            Column(
              children: [
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
