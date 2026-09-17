import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';

import '../../../../app/theme/alice_color_tokens.dart';
import '../../../../app/theme/alice_text_styles.dart';

final class AliceMarkdownBody extends StatelessWidget {
  const AliceMarkdownBody({super.key, required this.content});

  final String content;

  @override
  Widget build(BuildContext context) {
    try {
      return MarkdownBody(
        data: content,
        selectable: true,
        onTapLink: (_, _, _) {},
        imageBuilder: (_, _, _) => const SizedBox.shrink(),
        styleSheet: MarkdownStyleSheet(
          p: AliceTextStyles.body,
          h1: AliceTextStyles.title,
          h2: AliceTextStyles.bodyEmphasis,
          h3: AliceTextStyles.bodyEmphasis,
          code: AliceTextStyles.code,
          codeblockDecoration: const BoxDecoration(
            color: AliceColorTokens.backgroundElevated,
          ),
        ),
      );
    } catch (_) {
      return SelectableText(content, style: AliceTextStyles.body);
    }
  }
}
