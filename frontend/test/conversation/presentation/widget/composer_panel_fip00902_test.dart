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
    int characterCount = 0,
    bool showCharacterCount = false,
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
          characterCount: characterCount,
          showCharacterCount: showCharacterCount,
          onDraftChanged: onDraftChanged,
          onSend: onSend,
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
      buildTestable(
        controller: controller,
        focusNode: focusNode,
        onSend: () {},
      ),
    );

    expect(find.text('メッセージを入力'), findsOneWidget);
  });

  testWidgets('shows the 9000 character counter', (tester) async {
    final controller = TextEditingController();
    final focusNode = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);

    await tester.pumpWidget(
      buildTestable(
        controller: controller,
        focusNode: focusNode,
        characterCount: 9000,
        showCharacterCount: true,
        onSend: () {},
      ),
    );

    expect(find.text('9000 / 10,000'), findsOneWidget);
  });

  testWidgets('shows an inline validation message', (tester) async {
    final controller = TextEditingController();
    final focusNode = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);

    await tester.pumpWidget(
      buildTestable(
        controller: controller,
        focusNode: focusNode,
        validationMessage: '10,000文字以内で入力してください',
        onSend: () {},
      ),
    );

    expect(find.text('10,000文字以内で入力してください'), findsOneWidget);
  });

  testWidgets('disables Send when sendEnabled is false', (tester) async {
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
  });

  testWidgets('forwards raw onChanged text', (tester) async {
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
