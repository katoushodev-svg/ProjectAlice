import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../../../../app/theme/alice_color_tokens.dart';
import 'alice_core_visual_state.dart';

/// Draws subtle internal energy over the canonical Alice Core image.
///
/// This painter is presentation-only. It does not own the base image, rings,
/// ticks, arcs, or the outer glow.
class AliceCoreEnergyPainter extends CustomPainter {
  const AliceCoreEnergyPainter({
    required this.visualState,
    required this.phase,
    required this.pulseProgress,
    this.reduceMotion = false,
  });

  // Local rendering choices, kept together so they do not become visual tokens.
  static const double _idleStrength = 0.30;
  static const double _thinkingBaseStrength = 0.25;
  static const double _thinkingPulseStrength = 0.12;
  static const double _streamingStrength = 0.30;
  static const double _unavailableStrength = 0.08;
  static const double _coreRadiusFactor = 0.39;
  static const double _flowWidthFactor = 0.028;
  static const double _segmentAlphaBoost = 1.28;
  static const double _segmentWhiteHighlight = 0.24;
  static const int _segmentFadeSteps = 12;

  // Fixed, deliberately asymmetric paths keep the motion organic and
  // reproducible without introducing particle simulation or per-frame random
  // geometry. Coordinates are relative to the clipped Core radius.
  static const List<_EnergyFlow> _idleFlows = [
    _EnergyFlow(
      startX: -0.72,
      startY: -0.12,
      control1X: -0.50,
      control1Y: -0.58,
      control2X: 0.15,
      control2Y: 0.58,
      endX: 0.72,
      endY: 0.08,
      phaseOffset: 0.28,
      // Preserve the previous segment's physical length relative to R.
      segmentLength: 0.325852,
      strengthFactor: 1.0,
    ),
    _EnergyFlow(
      startX: -0.58,
      startY: 0.42,
      control1X: -0.18,
      control1Y: 0.14,
      control2X: 0.23,
      control2Y: -0.48,
      endX: 0.64,
      endY: -0.34,
      phaseOffset: 0.69,
      segmentLength: 0.224179,
      strengthFactor: 0.66,
    ),
  ];

  static const List<_EnergyFlow> _thinkingFlows = [
    _EnergyFlow(
      startX: -0.58,
      startY: -0.13,
      control1X: -0.34,
      control1Y: -0.38,
      control2X: 0.08,
      control2Y: 0.30,
      endX: 0.40,
      endY: 0.10,
      phaseOffset: 0.20,
      segmentLength: 0.227290,
      strengthFactor: 1.0,
    ),
    _EnergyFlow(
      startX: -0.35,
      startY: 0.45,
      control1X: -0.12,
      control1Y: 0.13,
      control2X: 0.02,
      control2Y: -0.23,
      endX: 0.36,
      endY: -0.12,
      phaseOffset: 0.72,
      segmentLength: 0.148420,
      strengthFactor: 0.72,
    ),
  ];

  static const List<_EnergyFlow> _streamingFlows = [
    _EnergyFlow(
      startX: -0.04,
      startY: 0.02,
      control1X: 0.02,
      control1Y: -0.20,
      control2X: -0.22,
      control2Y: -0.43,
      endX: -0.40,
      endY: -0.61,
      phaseOffset: 0.52,
      segmentLength: 0.166692,
      strengthFactor: 0.86,
    ),
    _EnergyFlow(
      startX: 0.01,
      startY: 0.03,
      control1X: 0.19,
      control1Y: 0.10,
      control2X: 0.42,
      control2Y: 0.20,
      endX: 0.64,
      endY: 0.30,
      phaseOffset: 0.22,
      segmentLength: 0.130245,
      strengthFactor: 1.0,
    ),
    _EnergyFlow(
      startX: -0.03,
      startY: 0.02,
      control1X: -0.09,
      control1Y: 0.23,
      control2X: 0.12,
      control2Y: 0.43,
      endX: 0.30,
      endY: 0.63,
      phaseOffset: 0.82,
      segmentLength: 0.115766,
      strengthFactor: 0.68,
    ),
  ];

  final AliceCoreVisualState visualState;
  final double phase;
  final double pulseProgress;
  final bool reduceMotion;

  @override
  void paint(Canvas canvas, Size size) {
    final shortestSide = math.min(size.width, size.height);
    if (shortestSide <= 0) return;

    final center = Offset(size.width / 2, size.height / 2);
    final radius = shortestSide * _coreRadiusFactor;
    final isStatic =
        reduceMotion || visualState == AliceCoreVisualState.unavailable;
    final effectivePhase = isStatic ? 0.0 : phase;
    final normalizedPulse = reduceMotion ? 0.0 : pulseProgress.clamp(0.0, 1.0);

    canvas.save();
    canvas.translate(center.dx, center.dy);
    canvas.clipPath(
      Path()..addOval(Rect.fromCircle(center: Offset.zero, radius: radius)),
    );

    switch (visualState) {
      case AliceCoreVisualState.idle:
        _paintFlows(canvas, radius, effectivePhase, _idleStrength, _idleFlows);
      case AliceCoreVisualState.thinking:
        final strength =
            _thinkingBaseStrength + (_thinkingPulseStrength * normalizedPulse);
        _paintCenterLight(canvas, radius, strength);
        _paintFlows(canvas, radius, effectivePhase, strength, _thinkingFlows);
      case AliceCoreVisualState.streaming:
        _paintOutwardEnergy(canvas, radius, effectivePhase, _streamingStrength);
      case AliceCoreVisualState.unavailable:
        _paintCenterLight(canvas, radius, _unavailableStrength);
    }

    canvas.restore();
  }

  void _paintCenterLight(Canvas canvas, double radius, double strength) {
    final paint = Paint()
      ..shader =
          RadialGradient(
            colors: [
              AliceColorTokens.coreCyan.withValues(alpha: strength * 0.34),
              AliceColorTokens.coreBlue.withValues(alpha: strength * 0.14),
              Colors.transparent,
            ],
            stops: const [0.0, 0.52, 1.0],
          ).createShader(
            Rect.fromCircle(center: Offset.zero, radius: radius * 0.72),
          );
    canvas.drawCircle(Offset.zero, radius * 0.72, paint);
  }

  void _paintFlows(
    Canvas canvas,
    double radius,
    double phase,
    double strength,
    List<_EnergyFlow> flows,
  ) {
    final cycle = (phase / (2 * math.pi)) % 1.0;
    for (final flow in flows) {
      final path = _buildFlowPath(flow, radius);
      final metric = path.computeMetrics().first;
      final head = (flow.phaseOffset + cycle) % 1.0;
      final segmentLength = radius * flow.segmentLength;
      final centerDistance = head * metric.length;
      final start = centerDistance - segmentLength / 2;

      final flowStrength = strength * flow.strengthFactor;
      _paintFadingSegment(
        canvas,
        metric,
        start,
        segmentLength,
        radius,
        flowStrength,
      );

      final tangent = metric.getTangentForOffset(centerDistance);
      if (tangent != null) {
        _paintLocalGlow(canvas, tangent.position, radius, flowStrength);
      }
    }
  }

  void _paintOutwardEnergy(
    Canvas canvas,
    double radius,
    double phase,
    double strength,
  ) {
    _paintCenterLight(canvas, radius, strength * 0.72);
    _paintFlows(canvas, radius, phase, strength, _streamingFlows);
  }

  Path _buildFlowPath(_EnergyFlow flow, double radius) {
    final start = Offset(flow.startX * radius, flow.startY * radius);
    final control1 = Offset(flow.control1X * radius, flow.control1Y * radius);
    final control2 = Offset(flow.control2X * radius, flow.control2Y * radius);
    final end = Offset(flow.endX * radius, flow.endY * radius);

    // A tangent-aligned return curve makes the phase wrap spatially
    // continuous while retaining the established forward Bezier geometry.
    const returnHandleFactor = 0.12;
    final returnControl1 = end + ((end - control2) * returnHandleFactor);
    final returnControl2 = start - ((control1 - start) * returnHandleFactor);
    return Path()
      ..moveTo(start.dx, start.dy)
      ..cubicTo(
        control1.dx,
        control1.dy,
        control2.dx,
        control2.dy,
        end.dx,
        end.dy,
      )
      ..cubicTo(
        returnControl1.dx,
        returnControl1.dy,
        returnControl2.dx,
        returnControl2.dy,
        start.dx,
        start.dy,
      )
      ..close();
  }

  void _paintFadingSegment(
    Canvas canvas,
    ui.PathMetric metric,
    double start,
    double segmentLength,
    double radius,
    double strength,
  ) {
    final stepLength = segmentLength / _segmentFadeSteps;
    final pathLength = metric.length;
    for (var index = 0; index < _segmentFadeSteps; index++) {
      final t = (index + 0.5) / _segmentFadeSteps;
      // Smooth fade at both ends, with the brightest part in the middle.
      final envelope = math.pow(math.sin(math.pi * t), 1.6).toDouble();
      final flowColor = Color.lerp(
        AliceColorTokens.coreCyan,
        AliceColorTokens.coreBlue,
        t,
      )!;
      final whiteMix =
          math.max(0.0, (envelope - 0.55) / 0.45).toDouble() *
          _segmentWhiteHighlight;
      final color = Color.lerp(flowColor, Colors.white, whiteMix)!;
      final stepStart = _wrapDistance(start + (index * stepLength), pathLength);
      final stepEnd = stepStart + stepLength;
      final paint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round
        ..strokeWidth = radius * _flowWidthFactor
        ..color = color.withValues(
          alpha: (strength * envelope * _segmentAlphaBoost).clamp(0.0, 1.0),
        );
      if (stepEnd <= pathLength) {
        canvas.drawPath(metric.extractPath(stepStart, stepEnd), paint);
      } else {
        canvas.drawPath(metric.extractPath(stepStart, pathLength), paint);
        canvas.drawPath(metric.extractPath(0, stepEnd - pathLength), paint);
      }
    }
  }

  double _wrapDistance(double distance, double pathLength) =>
      ((distance % pathLength) + pathLength) % pathLength;

  void _paintLocalGlow(
    Canvas canvas,
    Offset position,
    double radius,
    double strength,
  ) {
    final glowRadius = radius * 0.075;
    final paint = Paint()
      ..shader = RadialGradient(
        colors: [
          AliceColorTokens.coreCyan.withValues(alpha: strength * 0.22),
          AliceColorTokens.coreBlue.withValues(alpha: strength * 0.08),
          Colors.transparent,
        ],
        stops: const [0, 0.48, 1],
      ).createShader(Rect.fromCircle(center: position, radius: glowRadius));
    canvas.drawCircle(position, glowRadius, paint);
  }

  @override
  bool shouldRepaint(covariant AliceCoreEnergyPainter oldDelegate) =>
      oldDelegate.visualState != visualState ||
      oldDelegate.phase != phase ||
      oldDelegate.pulseProgress != pulseProgress ||
      oldDelegate.reduceMotion != reduceMotion;
}

class _EnergyFlow {
  const _EnergyFlow({
    required this.startX,
    required this.startY,
    required this.control1X,
    required this.control1Y,
    required this.control2X,
    required this.control2Y,
    required this.endX,
    required this.endY,
    required this.phaseOffset,
    required this.segmentLength,
    required this.strengthFactor,
  });

  final double startX;
  final double startY;
  final double control1X;
  final double control1Y;
  final double control2X;
  final double control2Y;
  final double endX;
  final double endY;
  final double phaseOffset;
  final double segmentLength;
  final double strengthFactor;
}
