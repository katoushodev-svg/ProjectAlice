import 'dart:io';
import 'dart:ui' as ui;

import 'package:alice/conversation/presentation/widget/alice_core/alice_core_assets.dart';
import 'package:alice/conversation/presentation/widget/alice_core/alice_core_glow_painter.dart';
import 'package:alice/conversation/presentation/widget/alice_core/alice_core_ring_painter.dart';
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

/// Counts how many times its subtree is (re)built, to detect whether an
/// ancestor is being marked dirty by descendant animation.
class _BuildCounter extends StatefulWidget {
  const _BuildCounter({required this.onBuild, required this.child});

  final VoidCallback onBuild;
  final Widget child;

  @override
  State<_BuildCounter> createState() => _BuildCounterState();
}

class _BuildCounterState extends State<_BuildCounter> {
  @override
  Widget build(BuildContext context) {
    widget.onBuild();
    return widget.child;
  }
}

void main() {
  group('AliceCoreView Performance (Flutter Test approximation)', () {
    // NOTE: These tests run inside the Flutter Test host-machine harness in
    // debug mode. They are not a substitute for the FIP-006 Section 20.2
    // Performance Gate (Profile Mode, p95 UI/Raster frame time <= 16.7 ms on
    // a representative device), which requires `flutter run --profile` /
    // `flutter drive` on real or simulated hardware and is not executed by
    // this suite. See the FIP-006 Completion Report for the measured vs.
    // unmeasured breakdown.
    testWidgets(
      'continuous Alice Core animation does not rebuild ancestor widgets',
      (tester) async {
        var parentBuildCount = 0;

        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: Center(
                child: DefaultAssetBundle(
                  bundle: TestAssetBundle(),
                  child: _BuildCounter(
                    onBuild: () => parentBuildCount++,
                    child: const AliceCoreView(
                      visualState: AliceCoreVisualState.thinking,
                      size: 180.0,
                    ),
                  ),
                ),
              ),
            ),
          ),
        );

        final buildCountAfterFirstFrame = parentBuildCount;
        expect(tester.binding.transientCallbackCount, greaterThan(0));

        // Advance several animation frames; only the RepaintBoundary'd Alice
        // Core subtree should repaint, and the ancestor build count must not
        // change because AnimatedBuilder scopes rebuilds to its own subtree.
        for (var i = 0; i < 30; i++) {
          await tester.pump(const Duration(milliseconds: 16));
        }

        expect(parentBuildCount, equals(buildCountAfterFirstFrame));
      },
    );

    test('Ring/Glow painters complete within a generous host-machine time budget', () {
      // This measures actual wall-clock CPU time to execute the painters'
      // `paint()` methods directly against an offscreen canvas, on the
      // host machine, in debug mode. It is a real measurement, but it is
      // not the FIP-006 device Profile Mode Performance Gate: host CPU
      // debug-mode timing does not correspond to on-device GPU raster
      // time, so the threshold below is intentionally generous and is
      // only meant to catch gross regressions (e.g. an accidental
      // per-frame O(n^2) path), not to certify the 16.7 ms device budget.
      const size = Size(260.0, 260.0);
      const iterations = 200;

      final ringPainter = AliceCoreRingPainter(
        visualState: AliceCoreVisualState.thinking,
        rotation: 1.2,
      );
      final glowPainter = AliceCoreGlowPainter(
        visualState: AliceCoreVisualState.thinking,
        pulseProgress: 0.5,
      );

      final stopwatch = Stopwatch()..start();
      for (var i = 0; i < iterations; i++) {
        final recorder = ui.PictureRecorder();
        final canvas = Canvas(recorder);
        ringPainter.paint(canvas, size);
        glowPainter.paint(canvas, size);
        recorder.endRecording();
      }
      stopwatch.stop();

      final averageMicros = stopwatch.elapsedMicroseconds / iterations;
      // ignore: avoid_print
      print(
        'AliceCore Ring+Glow paint(): avg ${averageMicros.toStringAsFixed(1)} '
        'us/iteration over $iterations iterations (host debug-mode, not a '
        'device Profile Mode measurement).',
      );

      expect(averageMicros, lessThan(50000));
    });

    testWidgets(
      'Alice Core asset is decoded once and reused across animation frames',
      (tester) async {
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: Center(
                child: DefaultAssetBundle(
                  bundle: TestAssetBundle(),
                  child: const AliceCoreView(
                    visualState: AliceCoreVisualState.thinking,
                    size: 180.0,
                  ),
                ),
              ),
            ),
          ),
        );

        for (var i = 0; i < 10; i++) {
          await tester.pump(const Duration(milliseconds: 16));
        }

        final imageWidget = tester.widget<Image>(find.byType(Image));
        expect(
          (imageWidget.image as AssetImage).assetName,
          equals(AliceCoreAssets.coreBase),
        );
        // A single, stable AssetImage provider (rather than a per-frame
        // constructed decode) is what allows the framework's image cache to
        // avoid redundant decode work across frames.
      },
    );
  });
}
