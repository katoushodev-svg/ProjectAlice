import 'package:flutter/material.dart';

import '../../../../app/theme/alice_text_styles.dart';

final class InitialHistoryLoadingView extends StatelessWidget {
  const InitialHistoryLoadingView({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Semantics(
        liveRegion: true,
        label: '会話を読み込んでいます',
        child: const Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('会話を読み込んでいます', style: AliceTextStyles.supporting),
          ],
        ),
      ),
    );
  }
}
