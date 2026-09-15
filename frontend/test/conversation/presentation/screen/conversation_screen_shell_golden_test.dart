import 'dart:io';

import 'package:alice/app/theme/alice_theme.dart';
import 'package:alice/conversation/presentation/screen/conversation_screen_shell.dart';
import 'package:alice/conversation/presentation/widget/alice_core/alice_core_visual_state.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

class _TestAssetBundle extends CachingAssetBundle {
  @override
  Future<ByteData> load(String key) async {
    final file = File(key);
    if (file.existsSync()) {
      final bytes = await file.readAsBytes();
      return ByteData.sublistView(bytes);
    }
    return rootBundle.load(key);
  }
}

void main() {
  Widget buildTestable({
    double keyboardInset = 0,
    List<Widget> messageItems = const [],
    TextScaler textScaler = TextScaler.noScaling,
    String draftText = '',
  }) {
    final controller = TextEditingController(text: draftText);
    final focusNode = FocusNode();
    final scrollController = ScrollController();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);
    addTearDown(scrollController.dispose);

    return MaterialApp(
      theme: AliceTheme.darkTheme,
      home: MediaQuery(
        data: MediaQueryData(
          viewInsets: EdgeInsets.only(bottom: keyboardInset),
          textScaler: textScaler,
        ),
        child: DefaultAssetBundle(
          bundle: _TestAssetBundle(),
          child: ConversationScreenShell(
            coreVisualState: AliceCoreVisualState.idle,
            messageItems: messageItems,
            draftController: controller,
            focusNode: focusNode,
            scrollController: scrollController,
            onSend: () {},
          ),
        ),
      ),
    );
  }

  group('ConversationScreenShell Golden Tests', () {
    testWidgets('golden - 375 empty', (tester) async {
      await tester.binding.setSurfaceSize(const Size(375, 667));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(buildTestable());
      await tester.pump(const Duration(milliseconds: 500));

      await expectLater(
        find.byType(ConversationScreenShell),
        matchesGoldenFile('goldens/screen_shell_375_empty.png'),
      );
    });

    testWidgets('golden - 390 empty', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(buildTestable());
      await tester.pump(const Duration(milliseconds: 500));

      await expectLater(
        find.byType(ConversationScreenShell),
        matchesGoldenFile('goldens/screen_shell_390_empty.png'),
      );
    });

    testWidgets('golden - 430 empty', (tester) async {
      await tester.binding.setSurfaceSize(const Size(430, 932));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(buildTestable());
      await tester.pump(const Duration(milliseconds: 500));

      await expectLater(
        find.byType(ConversationScreenShell),
        matchesGoldenFile('goldens/screen_shell_430_empty.png'),
      );
    });

    testWidgets('golden - 390 keyboard', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(buildTestable(keyboardInset: 300));
      await tester.pump(const Duration(milliseconds: 500));

      await expectLater(
        find.byType(ConversationScreenShell),
        matchesGoldenFile('goldens/screen_shell_390_keyboard.png'),
      );
    });

    testWidgets('golden - 390 core hidden', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 320));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(buildTestable());
      await tester.pump(const Duration(milliseconds: 500));

      await expectLater(
        find.byType(ConversationScreenShell),
        matchesGoldenFile('goldens/screen_shell_390_core_hidden.png'),
      );
    });

    testWidgets('golden - 390 composer five lines', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        buildTestable(draftText: 'line1\nline2\nline3\nline4\nline5'),
      );
      await tester.pump(const Duration(milliseconds: 500));

      await expectLater(
        find.byType(ConversationScreenShell),
        matchesGoldenFile('goldens/screen_shell_390_composer_five_lines.png'),
      );
    });

    testWidgets('golden - 390 large text', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        buildTestable(textScaler: const TextScaler.linear(1.6)),
      );
      await tester.pump(const Duration(milliseconds: 500));

      await expectLater(
        find.byType(ConversationScreenShell),
        matchesGoldenFile('goldens/screen_shell_390_large_text.png'),
      );
    });
  });
}
