import 'package:flutter/material.dart';

import 'alice_core/alice_core_view.dart';
import 'alice_core/alice_core_visual_state.dart';

/// Places FIP-006's [AliceCoreView] centered within a given Region height.
///
/// Owns only centering, bounding and clipping. Tap, Long Press, Drag, Voice
/// activation, Loading/Error text and Application State are out of scope.
class AliceCoreRegion extends StatelessWidget {
  const AliceCoreRegion({
    super.key,
    required this.visualState,
    required this.height,
    this.reduceMotion,
  });

  final AliceCoreVisualState visualState;

  /// Current Region height, already resolved by
  /// `ConversationLayoutMetrics.coreHeight`.
  final double height;

  final bool? reduceMotion;

  @override
  Widget build(BuildContext context) {
    final coreSize = height <= 0 ? 0.0 : height;
    return ClipRect(
      child: Center(
        child: SizedBox(
          width: coreSize,
          height: coreSize,
          child: coreSize <= 0
              ? null
              : AliceCoreView(
                  visualState: visualState,
                  size: coreSize,
                  reduceMotion: reduceMotion,
                ),
        ),
      ),
    );
  }
}
