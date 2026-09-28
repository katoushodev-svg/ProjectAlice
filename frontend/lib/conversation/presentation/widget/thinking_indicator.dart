import 'package:flutter/material.dart';

import '../../../app/theme/alice_text_styles.dart';

final class ThinkingIndicator extends StatelessWidget {
  const ThinkingIndicator({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.symmetric(vertical: 8, horizontal: 4),
      child: Semantics(
        liveRegion: true,
        label: 'Aliceが回答を作成中です',
        child: Text('考えています…', style: AliceTextStyles.supporting),
      ),
    );
  }
}
