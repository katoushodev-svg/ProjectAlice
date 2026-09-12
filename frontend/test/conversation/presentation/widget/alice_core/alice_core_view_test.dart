import 'dart:io';

import 'package:alice/conversation/presentation/widget/alice_core/alice_core_assets.dart';
import 'package:alice/conversation/presentation/widget/alice_core/alice_core_view.dart';
import 'package:alice/conversation/presentation/widget/alice_core/alice_core_visual_state.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

class TestAssetBundle extends CachingAssetBundle {
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

Widget buildTestableWidget(Widget child) {
  return MaterialApp(
    home: Scaffold(
      body: Center(
        child: DefaultAssetBundle(bundle: TestAssetBundle(), child: child),
      ),
    ),
  );
}

void main() {
  group('AliceCoreView Widget Tests', () {
    testWidgets('renders at 96px, 180px, and 260px without layout errors', (
      tester,
    ) async {
      for (final size in [96.0, 180.0, 260.0]) {
        await tester.pumpWidget(
          buildTestableWidget(
            AliceCoreView(
              key: ValueKey(size),
              visualState: AliceCoreVisualState.idle,
              size: size,
            ),
          ),
        );

        expect(tester.takeException(), isNull);
        final finder = find.byKey(ValueKey(size));
        expect(finder, findsOneWidget);
        final renderBox = tester.renderObject<RenderBox>(finder);
        expect(renderBox.size, equals(Size(size, size)));
      }
    });

    testWidgets('renders all visual states', (tester) async {
      for (final state in AliceCoreVisualState.values) {
        await tester.pumpWidget(
          buildTestableWidget(AliceCoreView(visualState: state, size: 180.0)),
        );

        expect(tester.takeException(), isNull);
        expect(find.byType(AliceCoreView), findsOneWidget);
      }
    });

    testWidgets('size remains constant across state transitions', (
      tester,
    ) async {
      final stateNotifier = ValueNotifier<AliceCoreVisualState>(
        AliceCoreVisualState.idle,
      );

      await tester.pumpWidget(
        buildTestableWidget(
          ValueListenableBuilder<AliceCoreVisualState>(
            valueListenable: stateNotifier,
            builder: (context, state, child) {
              return AliceCoreView(visualState: state, size: 180.0);
            },
          ),
        ),
      );

      final initialSize = tester.getSize(find.byType(AliceCoreView));
      expect(initialSize, equals(const Size(180.0, 180.0)));

      for (final state in AliceCoreVisualState.values) {
        stateNotifier.value = state;
        await tester.pump(const Duration(milliseconds: 300));
        final currentSize = tester.getSize(find.byType(AliceCoreView));
        expect(currentSize, equals(const Size(180.0, 180.0)));
      }
    });

    testWidgets('control: continuous ticker is active without reduce motion', (
      tester,
    ) async {
      await tester.pumpWidget(
        buildTestableWidget(
          const AliceCoreView(
            visualState: AliceCoreVisualState.thinking,
            size: 180.0,
            reduceMotion: false,
          ),
        ),
      );
      await tester.pump();

      // Sanity check that the ticker-based measurement below is capable of
      // detecting a running Alice Core animation in this test harness.
      expect(tester.binding.transientCallbackCount, greaterThan(0));
    });

    testWidgets('respects reduce motion and disables continuous animation', (
      tester,
    ) async {
      await tester.pumpWidget(
        buildTestableWidget(
          const AliceCoreView(
            visualState: AliceCoreVisualState.thinking,
            size: 180.0,
            reduceMotion: true,
          ),
        ),
      );
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(tester.binding.transientCallbackCount, 0);

      await tester.pump(const Duration(seconds: 2));
      expect(tester.takeException(), isNull);
      expect(tester.binding.transientCallbackCount, 0);
    });

    testWidgets('stops continuous animation when unavailable', (tester) async {
      await tester.pumpWidget(
        buildTestableWidget(
          const AliceCoreView(
            visualState: AliceCoreVisualState.unavailable,
            size: 180.0,
          ),
        ),
      );
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(tester.binding.transientCallbackCount, 0);

      await tester.pump(const Duration(seconds: 2));
      expect(tester.takeException(), isNull);
      expect(tester.binding.transientCallbackCount, 0);
    });

    testWidgets('stops animation when TickerMode is disabled', (tester) async {
      await tester.pumpWidget(
        buildTestableWidget(
          const TickerMode(
            enabled: false,
            child: AliceCoreView(
              visualState: AliceCoreVisualState.idle,
              size: 180.0,
            ),
          ),
        ),
      );
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(tester.binding.transientCallbackCount, 0);

      await tester.pump(const Duration(seconds: 2));
      expect(tester.takeException(), isNull);
      expect(tester.binding.transientCallbackCount, 0);
    });

    testWidgets('is excluded from semantics tree (decorative semantics)', (
      tester,
    ) async {
      await tester.pumpWidget(
        buildTestableWidget(
          const AliceCoreView(
            visualState: AliceCoreVisualState.idle,
            size: 180.0,
          ),
        ),
      );

      final semanticsWidget = tester.widget<Semantics>(
        find
            .descendant(
              of: find.byType(AliceCoreView),
              matching: find.byType(Semantics),
            )
            .first,
      );
      expect(semanticsWidget.excludeSemantics, isTrue);
    });

    testWidgets('uses canonical core base asset path', (tester) async {
      await tester.pumpWidget(
        buildTestableWidget(
          const AliceCoreView(
            visualState: AliceCoreVisualState.idle,
            size: 180.0,
          ),
        ),
      );

      final imageFinder = find.byType(Image);
      expect(imageFinder, findsOneWidget);
      final imageWidget = tester.widget<Image>(imageFinder);
      expect(
        (imageWidget.image as AssetImage).assetName,
        equals(AliceCoreAssets.coreBase),
      );
    });
  });

  group('AliceCoreView App Lifecycle Tests', () {
    testWidgets('stops the continuous ticker when app is paused', (
      tester,
    ) async {
      await tester.pumpWidget(
        buildTestableWidget(
          const AliceCoreView(
            visualState: AliceCoreVisualState.idle,
            size: 180.0,
          ),
        ),
      );
      await tester.pump();
      expect(tester.binding.transientCallbackCount, greaterThan(0));

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(tester.binding.transientCallbackCount, 0);
    });

    testWidgets('stops the continuous ticker when app becomes inactive', (
      tester,
    ) async {
      await tester.pumpWidget(
        buildTestableWidget(
          const AliceCoreView(
            visualState: AliceCoreVisualState.idle,
            size: 180.0,
          ),
        ),
      );
      await tester.pump();
      expect(tester.binding.transientCallbackCount, greaterThan(0));

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(tester.binding.transientCallbackCount, 0);
    });

    testWidgets('resumes the continuous ticker safely after resumed', (
      tester,
    ) async {
      await tester.pumpWidget(
        buildTestableWidget(
          const AliceCoreView(
            visualState: AliceCoreVisualState.idle,
            size: 180.0,
          ),
        ),
      );
      await tester.pump();

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pump();
      expect(tester.binding.transientCallbackCount, 0);

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(tester.binding.transientCallbackCount, greaterThan(0));
    });

    testWidgets(
      'does not resume the ticker after resumed when reduce motion is on',
      (tester) async {
        await tester.pumpWidget(
          buildTestableWidget(
            const AliceCoreView(
              visualState: AliceCoreVisualState.idle,
              size: 180.0,
              reduceMotion: true,
            ),
          ),
        );
        await tester.pump();
        expect(tester.binding.transientCallbackCount, 0);

        tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
        await tester.pump();
        tester.binding.handleAppLifecycleStateChanged(
          AppLifecycleState.resumed,
        );
        await tester.pump();

        expect(tester.takeException(), isNull);
        expect(tester.binding.transientCallbackCount, 0);
      },
    );

    testWidgets(
      'safely disposes without reusing a disposed controller across pause/resume',
      (tester) async {
        await tester.pumpWidget(
          buildTestableWidget(
            const AliceCoreView(
              visualState: AliceCoreVisualState.idle,
              size: 180.0,
            ),
          ),
        );
        await tester.pump();

        tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
        await tester.pump();

        // Remove the widget entirely while paused to exercise dispose().
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();

        tester.binding.handleAppLifecycleStateChanged(
          AppLifecycleState.resumed,
        );
        await tester.pump();

        expect(tester.takeException(), isNull);
      },
    );
  });
}
