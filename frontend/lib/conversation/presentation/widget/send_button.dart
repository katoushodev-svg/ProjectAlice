import 'package:flutter/material.dart';

import '../../../app/theme/alice_color_tokens.dart';
import '../layout/conversation_layout_metrics.dart';

/// Circular Send trigger with a 44x44 minimum Hit Target.
///
/// Performs no Validation or Send logic itself; a single Tap while [enabled]
/// notifies [onPressed] exactly once.
class SendButton extends StatelessWidget {
  const SendButton({super.key, required this.enabled, required this.onPressed});

  final bool enabled;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: '送信',
      button: true,
      enabled: enabled,
      excludeSemantics: true,
      child: SizedBox(
        width: ConversationLayoutMetrics.sendButtonMinSize,
        height: ConversationLayoutMetrics.sendButtonMinSize,
        child: Material(
          color: enabled ? AliceColorTokens.primary : AliceColorTokens.surface,
          shape: const CircleBorder(),
          child: InkWell(
            customBorder: const CircleBorder(),
            onTap: enabled ? onPressed : null,
            child: Center(
              child: Icon(
                Icons.arrow_upward,
                color: enabled
                    ? AliceColorTokens.textPrimary
                    : AliceColorTokens.textSecondary,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
