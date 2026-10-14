import 'package:flutter/material.dart';

import '../../../../app/theme/alice_color_tokens.dart';
import '../../../../app/theme/alice_text_styles.dart';

final class UserMessageBubble extends StatelessWidget {
  const UserMessageBubble({super.key, required this.content});

  final String content;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'あなた: $content',
      child: LayoutBuilder(
        builder: (context, constraints) => Align(
          alignment: Alignment.centerRight,
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: constraints.maxWidth * .78),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: AliceColorTokens.surfaceStrong,
                borderRadius: BorderRadius.circular(18),
              ),
              child: SelectableText(content, style: AliceTextStyles.body),
            ),
          ),
        ),
      ),
    );
  }
}
