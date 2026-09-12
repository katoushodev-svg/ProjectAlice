import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../../app/theme/alice_color_tokens.dart';
import 'alice_core_visual_state.dart';

/// Painter for Alice Core Interface Rings and Ticks.
class AliceCoreRingPainter extends CustomPainter {
  AliceCoreRingPainter({
    required this.visualState,
    required this.rotation,
    this.reduceMotion = false,
  });

  final AliceCoreVisualState visualState;
  final double rotation;
  final bool reduceMotion;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final minDim = math.min(size.width, size.height);

    // Core PNG asset is ~78% of bounds; rings expand to 106%, 116%, 126% of Core diameter.
    final coreDiameter = minDim * 0.78;
    final r1 = (coreDiameter * 1.06) / 2;
    final r2 = (coreDiameter * 1.16) / 2;
    final r3 = (coreDiameter * 1.26) / 2;

    final isUnavailable = visualState == AliceCoreVisualState.unavailable;
    final opacityMultiplier = isUnavailable ? 0.3 : 1.0;
    final effectiveRotation = (reduceMotion || isUnavailable) ? 0.0 : rotation;

    canvas.save();
    canvas.translate(center.dx, center.dy);
    canvas.rotate(effectiveRotation);

    // Ring 1 (Diameter ~106%, Width 1.0px)
    final ring1Paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0
      ..color = AliceColorTokens.coreBlue.withValues(
        alpha: 0.4 * opacityMultiplier,
      );
    canvas.drawCircle(Offset.zero, r1, ring1Paint);

    // Ring 2 (Diameter ~116%, Width 0.75px)
    final ring2Paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 0.75
      ..color = AliceColorTokens.coreCyan.withValues(
        alpha: 0.5 * opacityMultiplier,
      );
    canvas.drawCircle(Offset.zero, r2, ring2Paint);

    // Amber Arcs on Ring 2 (2 arcs, ~15 degrees each)
    if (!isUnavailable) {
      final amberPaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.2
        ..strokeCap = StrokeCap.round
        ..color = AliceColorTokens.coreAmber.withValues(
          alpha: 0.85 * opacityMultiplier,
        );
      const arcLength = 15 * (math.pi / 180);
      canvas.drawArc(
        Rect.fromCircle(center: Offset.zero, radius: r2),
        0.0,
        arcLength,
        false,
        amberPaint,
      );
      canvas.drawArc(
        Rect.fromCircle(center: Offset.zero, radius: r2),
        math.pi,
        arcLength,
        false,
        amberPaint,
      );
    }

    // Ring 3 (Diameter ~126%, Width 0.75px)
    final ring3Paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 0.75
      ..color = AliceColorTokens.coreCyan.withValues(
        alpha: 0.35 * opacityMultiplier,
      );
    canvas.drawCircle(Offset.zero, r3, ring3Paint);

    // Outer Ticks: 48 total, 4th is long
    final tickPaintShort = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 0.75
      ..color = AliceColorTokens.coreCyan.withValues(
        alpha: 0.4 * opacityMultiplier,
      );
    final tickPaintLong = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0
      ..color = AliceColorTokens.coreCyan.withValues(
        alpha: 0.7 * opacityMultiplier,
      );

    const tickCount = 48;
    const angleStep = (2 * math.pi) / tickCount;

    for (int i = 0; i < tickCount; i++) {
      final angle = i * angleStep;
      final isLong = (i % 4 == 0);
      final tickLength = isLong ? 6.0 : 3.0;

      final startR = r3;
      final endR = r3 + tickLength;

      final p1 = Offset(startR * math.cos(angle), startR * math.sin(angle));
      final p2 = Offset(endR * math.cos(angle), endR * math.sin(angle));

      canvas.drawLine(p1, p2, isLong ? tickPaintLong : tickPaintShort);
    }

    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant AliceCoreRingPainter oldDelegate) {
    return oldDelegate.visualState != visualState ||
        oldDelegate.rotation != rotation ||
        oldDelegate.reduceMotion != reduceMotion;
  }
}
