import 'package:alice/app/theme/alice_theme.dart';
import 'package:alice/conversation/presentation/widget/composer_panel.dart';
import 'package:alice/conversation/presentation/widget/message_text_field.dart';
import 'package:alice/conversation/presentation/widget/send_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Widget buildTestable({
    bool enabled = true,
    bool sendEnabled = true,
    String? validationMessage,
    ValueChanged<String>? onDraftChanged,
    required VoidCallback onSend,
    required TextEditingController controller,
    required FocusNode focusNode,
  }) {
    return MaterialApp(
      theme: AliceTheme.darkTheme,
      home: Scaffold(
        body: ComposerPanel(
          draftController: controller,
          focusNode: focusNode,
          enabled: enabled,
          sendEnabled: sendEnabled,
          validationMessage: validationMessage,
          onDraftChanged: onDraftChanged,
          onSend: onSend,
        ),
      ),
    );
  }

  testWidgets(
    'height stays within the 56-136 logical pixel bounds for one line',
    (tester) async {
      final controller = TextEditingController();
      final focusNode = FocusNode();
      addTearDown(controller.dispose);
      addTearDown(focusNode.dispose);

      await tester.pumpWidget(
        buildTestable(
          controller: controller,
          focusNode: focusNode,
          onSend: () {},
        ),
      );

      final size = tester.getSize(find.byType(ComposerPanel));
      expect(size.height, greaterThanOrEqualTo(56.0));
      expect(size.height, lessThanOrEqualTo(136.0));
    },
  );

  testWidgets(
    'height stays within the 56-136 logical pixel bounds for five lines',
    (tester) async {
      final controller = TextEditingController(
        text: 'line1\nline2\nline3\nline4\nline5',
      );
      final focusNode = FocusNode();
      addTearDown(controller.dispose);
      addTearDown(focusNode.dispose);

      await tester.pumpWidget(
        buildTestable(
          controller: controller,
          focusNode: focusNode,
          onSend: () {},
        ),
      );

      expect(tester.takeException(), isNull);
      final size = tester.getSize(find.byType(ComposerPanel));
      expect(size.height, lessThanOrEqualTo(136.0));
    },
  );

  testWidgets('five-line draft with validation message does not overflow', (
    tester,
  ) async {
    final controller = TextEditingController(
      text: 'line1\nline2\nline3\nline4\nline5',
    );
    final focusNode = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);

    await tester.pumpWidget(
      buildTestable(
        controller: controller,
        focusNode: focusNode,
        onSend: () {},
        validationMessage: '入力してください',
      ),
    );

    expect(tester.takeException(), isNull);

    final size = tester.getSize(find.byType(ComposerPanel));
    expect(size.height, lessThanOrEqualTo(136.0));
  });

  testWidgets('shows the Validation Message slot only when provided', (
    tester,
  ) async {
    final controller = TextEditingController();
    final focusNode = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);

    await tester.pumpWidget(
      buildTestable(
        controller: controller,
        focusNode: focusNode,
        onSend: () {},
        validationMessage: '入力してください',
      ),
    );

    expect(find.text('入力してください'), findsOneWidget);
  });

  testWidgets(
    'disables Send when sendEnabled is false without invoking onSend',
    (tester) async {
      final controller = TextEditingController();
      final focusNode = FocusNode();
      addTearDown(controller.dispose);
      addTearDown(focusNode.dispose);
      var callCount = 0;

      await tester.pumpWidget(
        buildTestable(
          controller: controller,
          focusNode: focusNode,
          sendEnabled: false,
          onSend: () => callCount++,
        ),
      );
      await tester.tap(find.byType(SendButton), warnIfMissed: false);

      expect(callCount, 0);
    },
  );

  testWidgets('forwards raw onChanged text without Trim or Normalize', (
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
        onSend: () {},
        onDraftChanged: (value) => received = value,
      ),
    );
    await tester.enterText(find.byType(MessageTextField), '  draft  ');

    expect(received, '  draft  ');
  });
}
