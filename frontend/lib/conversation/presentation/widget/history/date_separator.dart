import 'package:flutter/material.dart';

import '../../../../app/theme/alice_color_tokens.dart';
import '../../../../app/theme/alice_text_styles.dart';

final class DateSeparator extends StatelessWidget {
  const DateSeparator({super.key, required this.date});

  final DateTime date;

  @override
  Widget build(BuildContext context) {
    final label = '${date.year}年${date.month}月${date.day}日';
    return Semantics(
      header: true,
      label: label,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Row(
          children: [
            const Expanded(child: Divider(color: AliceColorTokens.border)),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Text(label, style: AliceTextStyles.caption),
            ),
            const Expanded(child: Divider(color: AliceColorTokens.border)),
          ],
        ),
      ),
    );
  }
}
