import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../../app/theme/alice_color_tokens.dart';
import 'alice_core_visual_state.dart';

/// Inner and Outer Glow Painter for Alice Core.
class AliceCoreGlowPainter extends CustomPainter {
  AliceCoreGlowPainter({
    required this.visualState,
    required this.pulseProgress,
    this.reduceMotion = false,
  });

  final AliceCoreVisualState visualState;
  final double pulseProgress;
  final bool reduceMotion;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final minDim = math.min(size.width, size.height);

    final isUnavailable = visualState == AliceCoreVisualState.unavailable;
    final isThinking = visualState == AliceCoreVisualState.thinking;

    // Outer Glow Max Opacity 0.22, Thinking Pulse Max Opacity 0.35
    final baseOpacity = isUnavailable
        ? 0.05
        : (isThinking
              ? (0.22 + (0.13 * (reduceMotion ? 0.0 : pulseProgress)))
              : 0.22);

    final coreRadius = (minDim * 0.78) / 2;

    // Outer Glow layer 1 (Violet)
    final violetPaint = Paint()
      ..color = AliceColorTokens.coreViolet.withValues(alpha: baseOpacity * 0.5)
      ..maskFilter = MaskFilter.blur(BlurStyle.normal, coreRadius * 0.4);
    canvas.drawCircle(center, coreRadius * 1.05, violetPaint);

    // Outer Glow layer 2 (Blue)
    final bluePaint = Paint()
      ..color = AliceColorTokens.coreBlue.withValues(alpha: baseOpacity * 0.7)
      ..maskFilter = MaskFilter.blur(BlurStyle.normal, coreRadius * 0.3);
    canvas.drawCircle(center, coreRadius * 0.95, bluePaint);

    // Inner Glow layer 3 (Cyan)
    final cyanPaint = Paint()
      ..color = AliceColorTokens.coreCyan.withValues(alpha: baseOpacity)
      ..maskFilter = MaskFilter.blur(BlurStyle.normal, coreRadius * 0.2);
    canvas.drawCircle(center, coreRadius * 0.75, cyanPaint);
  }

  @override
  bool shouldRepaint(covariant AliceCoreGlowPainter oldDelegate) {
    return oldDelegate.visualState != visualState ||
        oldDelegate.pulseProgress != pulseProgress ||
        oldDelegate.reduceMotion != reduceMotion;
  }
}
