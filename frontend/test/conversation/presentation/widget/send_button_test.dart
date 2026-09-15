import 'package:alice/app/theme/alice_theme.dart';
import 'package:alice/conversation/presentation/widget/send_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Widget buildTestable({
    required bool enabled,
    required VoidCallback onPressed,
  }) {
    return MaterialApp(
      theme: AliceTheme.darkTheme,
      home: Scaffold(
        body: SendButton(enabled: enabled, onPressed: onPressed),
      ),
    );
  }

  testWidgets('exposes a 44x44 or larger Hit Target', (tester) async {
    await tester.pumpWidget(buildTestable(enabled: true, onPressed: () {}));

    final size = tester.getSize(find.byType(SendButton));
    expect(size.width, greaterThanOrEqualTo(44.0));
    expect(size.height, greaterThanOrEqualTo(44.0));
  });

  testWidgets('exposes the 送信 Semantics label', (tester) async {
    await tester.pumpWidget(buildTestable(enabled: true, onPressed: () {}));

    expect(find.bySemanticsLabel('送信'), findsOneWidget);
  });

  testWidgets('notifies onPressed exactly once per Tap while enabled', (
    tester,
  ) async {
    var callCount = 0;
    await tester.pumpWidget(
      buildTestable(enabled: true, onPressed: () => callCount++),
    );

    await tester.tap(find.byType(SendButton));
    expect(callCount, 1);
  });

  testWidgets('does not notify onPressed while disabled', (tester) async {
    var callCount = 0;
    await tester.pumpWidget(
      buildTestable(enabled: false, onPressed: () => callCount++),
    );

    await tester.tap(find.byType(SendButton), warnIfMissed: false);
    expect(callCount, 0);
  });
}
