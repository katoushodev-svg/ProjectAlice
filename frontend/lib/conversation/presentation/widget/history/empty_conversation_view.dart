import 'package:flutter/material.dart';

import '../../../../app/theme/alice_text_styles.dart';

final class EmptyConversationView extends StatelessWidget {
  const EmptyConversationView({super.key});

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('何から始めましょうか？', style: AliceTextStyles.bodyEmphasis),
            SizedBox(height: 12),
            Text(
              '相談したいことや、聞きたいことを入力してください。',
              textAlign: TextAlign.center,
              style: AliceTextStyles.supporting,
            ),
          ],
        ),
      ),
    );
  }
}
