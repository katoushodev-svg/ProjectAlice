import 'dart:io';

import 'package:alice/app/theme/alice_theme.dart';
import 'package:alice/conversation/presentation/screen/conversation_screen_shell.dart';
import 'package:alice/conversation/presentation/widget/alice_core/alice_core_visual_state.dart';
import 'package:alice/conversation/presentation/widget/alice_core_region.dart';
import 'package:alice/conversation/presentation/widget/alice_header.dart';
import 'package:alice/conversation/presentation/widget/composer_panel.dart';
import 'package:alice/conversation/presentation/widget/message_viewport.dart';
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
    Size viewInsets = Size.zero,
    List<Widget> messageItems = const [],
    TextScaler textScaler = TextScaler.noScaling,
  }) {
    final controller = TextEditingController();
    final focusNode = FocusNode();
    final scrollController = ScrollController();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);
    addTearDown(scrollController.dispose);

    return MaterialApp(
      theme: AliceTheme.darkTheme,
      home: MediaQuery(
        data: MediaQueryData(
          viewInsets: EdgeInsets.only(bottom: viewInsets.height),
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

  testWidgets('composes Header, Body and Composer in order without overflow', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(buildTestable());

    expect(tester.takeException(), isNull);
    expect(find.byType(AliceHeader), findsOneWidget);
    expect(find.byType(AliceCoreRegion), findsOneWidget);
    expect(find.byType(MessageViewport), findsOneWidget);
    expect(find.byType(ComposerPanel), findsOneWidget);
  });

  for (final width in [375.0, 390.0, 430.0]) {
    testWidgets('has no Horizontal Overflow at width $width', (tester) async {
      await tester.binding.setSurfaceSize(Size(width, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(buildTestable());

      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('shrinks Core Region when the Keyboard is shown', (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(buildTestable(viewInsets: const Size(0, 300)));
    // AliceCoreView animates indefinitely (idle rotation), so advance a
    // fixed duration covering the 300ms Core size transition instead of
    // pumpAndSettle.
    await tester.pump(const Duration(milliseconds: 350));

    final size = tester.getSize(find.byType(AliceCoreRegion));
    expect(size.height, greaterThanOrEqualTo(96.0));
    expect(size.height, lessThanOrEqualTo(120.0));
  });

  testWidgets('hides Core when the available Body height is too small', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(390, 320));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(buildTestable());

    expect(find.byType(AliceCoreRegion), findsNothing);
  });

  testWidgets('remains usable at a large Dynamic Type scale', (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      buildTestable(textScaler: const TextScaler.linear(1.6)),
    );

    expect(tester.takeException(), isNull);
    expect(find.byType(ComposerPanel), findsOneWidget);
  });
}
