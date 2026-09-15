import 'package:flutter/material.dart';

import '../../../app/theme/alice_color_tokens.dart';
import '../../../app/theme/alice_text_styles.dart';
import '../layout/conversation_layout_metrics.dart';

/// Fixed Header for the Conversation Screen Shell.
///
/// Displays only the centered `Alice` title. No Navigation, Menu, Settings
/// or Conversation State affordance is added here (FIP-007 Layout Contract).
class AliceHeader extends StatelessWidget {
  const AliceHeader({super.key});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: ConversationLayoutMetrics.headerHeight,
      width: double.infinity,
      child: ColoredBox(
        color: AliceColorTokens.backgroundElevated,
        child: Center(
          child: Text(
            'Alice',
            style: AliceTextStyles.title.copyWith(
              color: AliceColorTokens.textPrimary,
            ),
          ),
        ),
      ),
    );
  }
}
