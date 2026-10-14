import 'package:flutter/material.dart';

import '../../../app/theme/alice_color_tokens.dart';
import '../../../app/theme/alice_text_styles.dart';
import '../layout/conversation_layout_metrics.dart';

/// 1-5 line Draft input. Beyond 5 lines the Field scrolls internally.
///
/// Owns no Controller/FocusNode lifecycle; the Parent creates and disposes
/// them. Input text is passed through as-is: no Trim, Normalize, Persist
/// or Log.
class MessageTextField extends StatelessWidget {
  const MessageTextField({
    super.key,
    required this.controller,
    required this.focusNode,
    required this.enabled,
    this.onChanged,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final bool enabled;
  final ValueChanged<String>? onChanged;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: focusNode,
      builder: (context, child) {
        final isFocused = enabled && focusNode.hasFocus;

        return Container(
          constraints: const BoxConstraints(
            minHeight: ConversationLayoutMetrics.sendButtonMinSize,
          ),
          decoration: BoxDecoration(
            color: AliceColorTokens.surface,
            border: Border.all(
              color: isFocused
                  ? AliceColorTokens.primary
                  : AliceColorTokens.border,
            ),
            borderRadius: BorderRadius.circular(16.0),
          ),
          child: TextField(
            controller: controller,
            focusNode: focusNode,
            textAlignVertical: TextAlignVertical.center,
            enabled: enabled,
            onChanged: onChanged,
            minLines: ConversationLayoutMetrics.composerMinLines,
            maxLines: ConversationLayoutMetrics.composerMaxLines,
            keyboardType: TextInputType.multiline,
            textInputAction: TextInputAction.newline,
            // Keep five lines within the Composer's 136 logical pixel budget,
            // including its optional validation/counter row.
            style: AliceTextStyles.body.copyWith(
              height: 1.2,
              color: AliceColorTokens.textPrimary,
            ),
            cursorColor: AliceColorTokens.primary,
            decoration: InputDecoration(
              border: InputBorder.none,
              enabledBorder: InputBorder.none,
              focusedBorder: InputBorder.none,
              disabledBorder: InputBorder.none,
              isDense: true,
              contentPadding: const EdgeInsets.symmetric(horizontal: 12.0),
              hintText: 'Message...',
              hintStyle: AliceTextStyles.body.copyWith(
                color: AliceColorTokens.textSecondary,
              ),
            ),
          ),
        );
      },
    );
  }
}
