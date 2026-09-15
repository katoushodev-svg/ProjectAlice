import 'package:flutter/material.dart';

import '../../../app/theme/alice_color_tokens.dart';
import '../../../app/theme/alice_text_styles.dart';
import '../layout/conversation_layout_metrics.dart';
import 'message_text_field.dart';
import 'send_button.dart';

/// Bottom Composer: Draft Field, optional Validation Slot and Send Button.
///
/// Pure Presentation only: no Validation, UUID generation, HTTP/SSE, Draft
/// Clear/Restore, or Application State judgement happens here.
class ComposerPanel extends StatelessWidget {
  const ComposerPanel({
    super.key,
    required this.draftController,
    required this.focusNode,
    required this.enabled,
    required this.sendEnabled,
    required this.onSend,
    this.validationMessage,
    this.onDraftChanged,
  });

  final TextEditingController draftController;
  final FocusNode focusNode;
  final bool enabled;
  final bool sendEnabled;
  final VoidCallback onSend;
  final String? validationMessage;
  final ValueChanged<String>? onDraftChanged;

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: const BoxConstraints(
        minHeight: ConversationLayoutMetrics.composerMinHeight,
        maxHeight: ConversationLayoutMetrics.composerMaxHeight,
      ),
      child: ColoredBox(
        color: AliceColorTokens.backgroundElevated,
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: ConversationLayoutMetrics.composerHorizontalPadding,
            vertical: ConversationLayoutMetrics.composerVerticalPadding,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Flexible(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Expanded(
                      child: MessageTextField(
                        controller: draftController,
                        focusNode: focusNode,
                        enabled: enabled,
                        onChanged: onDraftChanged,
                      ),
                    ),
                    const SizedBox(width: 8.0),
                    SendButton(
                      enabled: enabled && sendEnabled,
                      onPressed: onSend,
                    ),
                  ],
                ),
              ),
              if (validationMessage != null)
                Padding(
                  padding: const EdgeInsets.only(top: 4.0),
                  child: Text(
                    validationMessage!,
                    style: AliceTextStyles.caption.copyWith(
                      color: AliceColorTokens.error,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
