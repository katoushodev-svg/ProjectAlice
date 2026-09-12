import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'alice_core_assets.dart';
import 'alice_core_glow_painter.dart';
import 'alice_core_ring_painter.dart';
import 'alice_core_visual_state.dart';

/// Presentation widget for Alice Core.
class AliceCoreView extends StatefulWidget {
  const AliceCoreView({
    super.key,
    required this.visualState,
    this.size = 180.0,
    this.reduceMotion,
  });

  final AliceCoreVisualState visualState;
  final double size;
  final bool? reduceMotion;

  @override
  State<AliceCoreView> createState() => _AliceCoreViewState();
}

class _AliceCoreViewState extends State<AliceCoreView>
    with TickerProviderStateMixin, WidgetsBindingObserver {
  late AnimationController _rotationController;
  late AnimationController _pulseController;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    _rotationController = AnimationController(
      vsync: this,
      duration: _getRotationDuration(widget.visualState),
    );

    // repeat(reverse: true) completes one forward+reverse cycle per two
    // durations, so a 1 sec duration yields the FIP-006 2 sec pulse cycle.
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 1),
    );
  }

  @override
  void didUpdateWidget(AliceCoreView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.visualState != widget.visualState ||
        oldWidget.reduceMotion != widget.reduceMotion) {
      _rotationController.duration = _getRotationDuration(widget.visualState);
      _updateAnimationState();
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _updateAnimationState();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.detached) {
      _stopAnimations();
    } else if (state == AppLifecycleState.resumed) {
      _updateAnimationState();
    }
  }

  Duration _getRotationDuration(AliceCoreVisualState state) {
    switch (state) {
      case AliceCoreVisualState.idle:
        return const Duration(seconds: 12);
      case AliceCoreVisualState.thinking:
        return const Duration(seconds: 6);
      case AliceCoreVisualState.streaming:
        return const Duration(seconds: 8);
      case AliceCoreVisualState.unavailable:
        return const Duration(seconds: 12);
    }
  }

  bool get _effectiveReduceMotion {
    if (widget.reduceMotion != null) {
      return widget.reduceMotion!;
    }
    return MediaQuery.maybeOf(context)?.disableAnimations ?? false;
  }

  void _updateAnimationState() {
    final tickerEnabled = TickerMode.valuesOf(context).enabled;
    final reduceMotion = _effectiveReduceMotion;
    final isUnavailable =
        widget.visualState == AliceCoreVisualState.unavailable;

    if (!tickerEnabled || reduceMotion || isUnavailable) {
      _stopAnimations();
      return;
    }

    if (!_rotationController.isAnimating) {
      _rotationController.repeat();
    }

    if (widget.visualState == AliceCoreVisualState.thinking) {
      if (!_pulseController.isAnimating) {
        _pulseController.repeat(reverse: true);
      }
    } else {
      if (_pulseController.isAnimating) {
        _pulseController.stop();
        _pulseController.value = 0.0;
      }
    }
  }

  void _stopAnimations() {
    if (_rotationController.isAnimating) {
      _rotationController.stop();
    }
    if (_pulseController.isAnimating) {
      _pulseController.stop();
      _pulseController.value = 0.0;
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _rotationController.dispose();
    _pulseController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final reduceMotion = _effectiveReduceMotion;
    final isUnavailable =
        widget.visualState == AliceCoreVisualState.unavailable;
    final coreSize = widget.size * 0.78;

    return Semantics(
      excludeSemantics: true,
      child: RepaintBoundary(
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeInOut,
          width: widget.size,
          height: widget.size,
          child: AnimatedBuilder(
            animation: Listenable.merge([
              _rotationController,
              _pulseController,
            ]),
            builder: (context, child) {
              final rotationValue = reduceMotion || isUnavailable
                  ? 0.0
                  : _rotationController.value * 2 * math.pi;
              final pulseValue = reduceMotion || isUnavailable
                  ? 0.0
                  : _pulseController.value;

              final pulseScale =
                  (widget.visualState == AliceCoreVisualState.thinking &&
                      !reduceMotion &&
                      !isUnavailable)
                  ? 0.98 + (0.04 * pulseValue)
                  : 1.0;

              return Stack(
                alignment: Alignment.center,
                children: [
                  // 1. Ring Painter
                  CustomPaint(
                    size: Size(widget.size, widget.size),
                    painter: AliceCoreRingPainter(
                      visualState: widget.visualState,
                      rotation: rotationValue,
                      reduceMotion: reduceMotion,
                    ),
                  ),

                  // 2. Core Image Asset
                  Transform.scale(
                    scale: pulseScale,
                    child: SizedBox(
                      width: coreSize,
                      height: coreSize,
                      child: Image.asset(
                        AliceCoreAssets.coreBase,
                        fit: BoxFit.contain,
                        errorBuilder: (context, error, stackTrace) {
                          assert(
                            false,
                            'Failed to load Alice Core base asset: $error',
                          );
                          return const SizedBox.shrink();
                        },
                      ),
                    ),
                  ),

                  // 3. Glow Painter
                  CustomPaint(
                    size: Size(widget.size, widget.size),
                    painter: AliceCoreGlowPainter(
                      visualState: widget.visualState,
                      pulseProgress: pulseValue,
                      reduceMotion: reduceMotion,
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}
