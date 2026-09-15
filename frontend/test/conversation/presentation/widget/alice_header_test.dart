import 'package:alice/app/theme/alice_theme.dart';
import 'package:alice/conversation/presentation/widget/alice_header.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Widget buildTestable() {
    return MaterialApp(
      theme: AliceTheme.darkTheme,
      home: const Scaffold(body: AliceHeader()),
    );
  }

  testWidgets('displays Alice centered exactly once', (tester) async {
    await tester.pumpWidget(buildTestable());

    expect(find.text('Alice'), findsOneWidget);
  });

  testWidgets('content height is 44 logical pixels', (tester) async {
    await tester.pumpWidget(buildTestable());

    final size = tester.getSize(find.byType(AliceHeader));
    expect(size.height, 44.0);
  });

  testWidgets('does not add Navigation, Menu or Status affordances', (
    tester,
  ) async {
    await tester.pumpWidget(buildTestable());

    expect(find.byType(BackButton), findsNothing);
    expect(find.byIcon(Icons.menu), findsNothing);
    expect(find.byIcon(Icons.settings), findsNothing);
    expect(find.byType(Icon), findsNothing);
  });
}
