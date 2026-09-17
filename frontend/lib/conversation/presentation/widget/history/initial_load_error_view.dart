import 'package:flutter/material.dart';

import '../../../../app/theme/alice_text_styles.dart';

final class InitialLoadErrorView extends StatelessWidget {
  const InitialLoadErrorView({
    super.key,
    required this.onReload,
    required this.loading,
  });

  final VoidCallback onReload;
  final bool loading;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('会話を読み込めませんでした', style: AliceTextStyles.bodyEmphasis),
            const SizedBox(height: 12),
            const Text(
              '接続を確認して、もう一度お試しください。',
              textAlign: TextAlign.center,
              style: AliceTextStyles.supporting,
            ),
            const SizedBox(height: 20),
            Semantics(
              label: '会話を再読み込み',
              button: true,
              child: FilledButton(
                onPressed: loading ? null : onReload,
                child: const Text('再読み込み'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
