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
    return TextField(
      controller: controller,
      focusNode: focusNode,
      enabled: enabled,
      onChanged: onChanged,
      minLines: ConversationLayoutMetrics.composerMinLines,
      maxLines: ConversationLayoutMetrics.composerMaxLines,
      keyboardType: TextInputType.multiline,
      textInputAction: TextInputAction.newline,
      // A tighter line height than AliceTextStyles.body keeps a 5 line Draft
      // within the Composer's 136 logical pixel Maximum Height budget.
      style: AliceTextStyles.body.copyWith(
        height: 1.3,
        color: AliceColorTokens.textPrimary,
      ),
      cursorColor: AliceColorTokens.primary,
      decoration: InputDecoration(
        border: InputBorder.none,
        isDense: true,
        contentPadding: EdgeInsets.zero,
        hintText: 'メッセージを入力',
        hintStyle: AliceTextStyles.body.copyWith(
          color: AliceColorTokens.textSecondary,
        ),
      ),
    );
  }
}
