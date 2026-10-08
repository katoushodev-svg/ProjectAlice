import 'package:flutter/material.dart';

import '../../../../app/theme/alice_color_tokens.dart';
import 'alice_markdown_body.dart';

final class AliceMessageBubble extends StatelessWidget {
  const AliceMessageBubble({super.key, required this.content});

  final String content;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Alice: $content',
      child: LayoutBuilder(
        builder: (context, constraints) => Align(
          alignment: Alignment.centerLeft,
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: constraints.maxWidth * .88),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: AliceColorTokens.surface,
                borderRadius: BorderRadius.circular(18),
              ),
              child: AliceMarkdownBody(content: content),
            ),
          ),
        ),
      ),
    );
  }
}
