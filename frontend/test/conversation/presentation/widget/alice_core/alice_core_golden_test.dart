import 'dart:io';

import 'package:alice/app/theme/alice_theme.dart';
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

void main() {
  Widget buildTestableWidget({
    required AliceCoreVisualState visualState,
    double size = 180.0,
    bool reduceMotion = false,
  }) {
    return MaterialApp(
      theme: AliceTheme.darkTheme,
      home: Scaffold(
        backgroundColor: AliceTheme.darkTheme.scaffoldBackgroundColor,
        body: Center(
          child: DefaultAssetBundle(
            bundle: TestAssetBundle(),
            child: AliceCoreView(
              visualState: visualState,
              size: size,
              reduceMotion: reduceMotion,
            ),
          ),
        ),
      ),
    );
  }

  group('AliceCoreView Golden Tests', () {
    testWidgets('golden - idle 180', (tester) async {
      await tester.pumpWidget(
        buildTestableWidget(
          visualState: AliceCoreVisualState.idle,
          size: 180.0,
        ),
      );
      await tester.pump();

      await expectLater(
        find.byType(AliceCoreView),
        matchesGoldenFile('goldens/alice_core_idle_180.png'),
      );
    });

    testWidgets('golden - thinking 180', (tester) async {
      await tester.pumpWidget(
        buildTestableWidget(
          visualState: AliceCoreVisualState.thinking,
          size: 180.0,
        ),
      );
      // Advance a fixed, deterministic elapsed time so the Thinking Pulse
      // and Ring rotation are captured mid-motion instead of frame zero.
      await tester.pump(const Duration(milliseconds: 500));

      await expectLater(
        find.byType(AliceCoreView),
        matchesGoldenFile('goldens/alice_core_thinking_180.png'),
      );
    });

    testWidgets('golden - streaming 180', (tester) async {
      await tester.pumpWidget(
        buildTestableWidget(
          visualState: AliceCoreVisualState.streaming,
          size: 180.0,
        ),
      );
      await tester.pump(const Duration(milliseconds: 500));

      await expectLater(
        find.byType(AliceCoreView),
        matchesGoldenFile('goldens/alice_core_streaming_180.png'),
      );
    });

    testWidgets('golden - unavailable 180', (tester) async {
      await tester.pumpWidget(
        buildTestableWidget(
          visualState: AliceCoreVisualState.unavailable,
          size: 180.0,
        ),
      );
      await tester.pump();

      await expectLater(
        find.byType(AliceCoreView),
        matchesGoldenFile('goldens/alice_core_unavailable_180.png'),
      );
    });

    testWidgets('golden - reduce motion 180', (tester) async {
      await tester.pumpWidget(
        buildTestableWidget(
          visualState: AliceCoreVisualState.idle,
          size: 180.0,
          reduceMotion: true,
        ),
      );
      // Advance elapsed time to prove the frame stays static under Reduce
      // Motion instead of only coincidentally matching frame zero.
      await tester.pump(const Duration(milliseconds: 500));

      await expectLater(
        find.byType(AliceCoreView),
        matchesGoldenFile('goldens/alice_core_reduce_motion_180.png'),
      );
    });

    testWidgets('golden - boundary 96', (tester) async {
      await tester.pumpWidget(
        buildTestableWidget(visualState: AliceCoreVisualState.idle, size: 96.0),
      );
      await tester.pump();

      await expectLater(
        find.byType(AliceCoreView),
        matchesGoldenFile('goldens/alice_core_boundary_96.png'),
      );
    });

    testWidgets('golden - boundary 260', (tester) async {
      await tester.pumpWidget(
        buildTestableWidget(
          visualState: AliceCoreVisualState.idle,
          size: 260.0,
        ),
      );
      await tester.pump();

      await expectLater(
        find.byType(AliceCoreView),
        matchesGoldenFile('goldens/alice_core_boundary_260.png'),
      );
    });
  });
}
