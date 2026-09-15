import 'package:alice/app/theme/alice_theme.dart';
import 'package:alice/conversation/presentation/widget/message_text_field.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Widget buildTestable({
    required TextEditingController controller,
    required FocusNode focusNode,
    bool enabled = true,
    ValueChanged<String>? onChanged,
  }) {
    return MaterialApp(
      theme: AliceTheme.darkTheme,
      home: Scaffold(
        body: MessageTextField(
          controller: controller,
          focusNode: focusNode,
          enabled: enabled,
          onChanged: onChanged,
        ),
      ),
    );
  }

  testWidgets('shows the approved placeholder text', (tester) async {
    final controller = TextEditingController();
    final focusNode = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);

    await tester.pumpWidget(
      buildTestable(controller: controller, focusNode: focusNode),
    );

    expect(find.text('メッセージを入力'), findsOneWidget);
  });

  testWidgets('does not autofocus on first build', (tester) async {
    final controller = TextEditingController();
    final focusNode = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);

    await tester.pumpWidget(
      buildTestable(controller: controller, focusNode: focusNode),
    );

    expect(focusNode.hasFocus, isFalse);
  });

  testWidgets('notifies onChanged with the raw, unmodified text', (
    tester,
  ) async {
    final controller = TextEditingController();
    final focusNode = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);
    String? received;

    await tester.pumpWidget(
      buildTestable(
        controller: controller,
        focusNode: focusNode,
        onChanged: (value) => received = value,
      ),
    );
    await tester.enterText(find.byType(MessageTextField), '  hello  ');

    expect(received, '  hello  ');
  });

  testWidgets('allows a newline instead of submitting on Return', (
    tester,
  ) async {
    final controller = TextEditingController();
    final focusNode = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);

    await tester.pumpWidget(
      buildTestable(controller: controller, focusNode: focusNode),
    );
    final field = tester.widget<TextField>(find.byType(TextField));

    expect(field.textInputAction, TextInputAction.newline);
    expect(field.maxLines, 5);
    expect(field.minLines, 1);
  });
}
