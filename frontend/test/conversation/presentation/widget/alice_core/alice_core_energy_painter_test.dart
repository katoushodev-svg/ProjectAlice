import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:alice/conversation/presentation/widget/alice_core/alice_core_energy_painter.dart';
import 'package:alice/conversation/presentation/widget/alice_core/alice_core_visual_state.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Future<Uint8List> _paint(AliceCoreEnergyPainter painter) async {
  final recorder = ui.PictureRecorder();
  final canvas = Canvas(recorder);
  painter.paint(canvas, const Size(180, 180));
  final image = await recorder.endRecording().toImage(180, 180);
  final data = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
  image.dispose();
  return data!.buffer.asUint8List();
}

void main() {
  group('AliceCoreEnergyPainter', () {
    test('Idle phase changes the painted energy overlay', () async {
      final first = await _paint(
        const AliceCoreEnergyPainter(
          visualState: AliceCoreVisualState.idle,
          phase: 0,
          pulseProgress: 0,
        ),
      );
      final second = await _paint(
        const AliceCoreEnergyPainter(
          visualState: AliceCoreVisualState.idle,
          phase: 1.2,
          pulseProgress: 0,
        ),
      );

      expect(first, isNot(equals(second)));
    });

    test('Thinking pulse changes the painted energy overlay', () async {
      final low = await _paint(
        const AliceCoreEnergyPainter(
          visualState: AliceCoreVisualState.thinking,
          phase: 0.8,
          pulseProgress: 0,
        ),
      );
      final high = await _paint(
        const AliceCoreEnergyPainter(
          visualState: AliceCoreVisualState.thinking,
          phase: 0.8,
          pulseProgress: 1,
        ),
      );

      expect(low, isNot(equals(high)));
    });

    test('Streaming uses a distinct outward energy rendering', () async {
      final thinking = await _paint(
        const AliceCoreEnergyPainter(
          visualState: AliceCoreVisualState.thinking,
          phase: 0.8,
          pulseProgress: 0.5,
        ),
      );
      final streaming = await _paint(
        const AliceCoreEnergyPainter(
          visualState: AliceCoreVisualState.streaming,
          phase: 0.8,
          pulseProgress: 0,
        ),
      );

      expect(thinking, isNot(equals(streaming)));
    });

    test('Reduce Motion fixes output regardless of phase', () async {
      final first = await _paint(
        const AliceCoreEnergyPainter(
          visualState: AliceCoreVisualState.idle,
          phase: 0,
          pulseProgress: 0,
          reduceMotion: true,
        ),
      );
      final second = await _paint(
        const AliceCoreEnergyPainter(
          visualState: AliceCoreVisualState.idle,
          phase: 2.4,
          pulseProgress: 0.9,
          reduceMotion: true,
        ),
      );

      expect(first, equals(second));
    });

    test('unavailable output is static regardless of phase', () async {
      final first = await _paint(
        const AliceCoreEnergyPainter(
          visualState: AliceCoreVisualState.unavailable,
          phase: 0,
          pulseProgress: 0,
        ),
      );
      final second = await _paint(
        const AliceCoreEnergyPainter(
          visualState: AliceCoreVisualState.unavailable,
          phase: 2.4,
          pulseProgress: 0.9,
        ),
      );

      expect(first, equals(second));
    });

    test('shouldRepaint tracks changed inputs and skips identical inputs', () {
      const base = AliceCoreEnergyPainter(
        visualState: AliceCoreVisualState.idle,
        phase: 0.4,
        pulseProgress: 0,
      );

      expect(
        base.shouldRepaint(
          const AliceCoreEnergyPainter(
            visualState: AliceCoreVisualState.idle,
            phase: 0.5,
            pulseProgress: 0,
          ),
        ),
        isTrue,
      );
      expect(
        base.shouldRepaint(
          const AliceCoreEnergyPainter(
            visualState: AliceCoreVisualState.idle,
            phase: 0.4,
            pulseProgress: 0,
          ),
        ),
        isFalse,
      );
    });
  });
}
