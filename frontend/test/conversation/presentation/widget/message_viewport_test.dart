import 'package:alice/app/theme/alice_theme.dart';
import 'package:alice/conversation/presentation/widget/message_viewport.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Widget buildTestable(ScrollController controller, List<Widget> items) {
    return MaterialApp(
      theme: AliceTheme.darkTheme,
      home: Scaffold(
        body: MessageViewport(controller: controller, items: items),
      ),
    );
  }

  testWidgets('is the only primary vertical Scrollable', (tester) async {
    final controller = ScrollController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      buildTestable(controller, const [Text('Hello'), Text('World')]),
    );

    expect(find.byType(Scrollable), findsOneWidget);
  });

  testWidgets('displays Fixture Items in the given oldest-to-newest order', (
    tester,
  ) async {
    final controller = ScrollController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      buildTestable(controller, const [
        Text('first'),
        Text('second'),
        Text('third'),
      ]),
    );

    final texts = tester
        .widgetList<Text>(find.byType(Text))
        .map((widget) => widget.data)
        .toList();
    expect(texts, ['first', 'second', 'third']);
  });

  testWidgets('does not replace the Parent-owned ScrollController', (
    tester,
  ) async {
    final controller = ScrollController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(buildTestable(controller, const [Text('only')]));

    final scrollable = tester.widget<Scrollable>(find.byType(Scrollable));
    expect(scrollable.controller, same(controller));
  });
}
