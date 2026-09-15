import 'dart:math' as math;

/// Approved Layout Metrics for FIP-007 Screen Shell.
///
/// Values here are Layout Contract constants and pure calculations only.
/// This file must not depend on Application State, Domain or Infrastructure.
abstract final class ConversationLayoutMetrics {
  static const double headerHeight = 44.0;

  static const double normalCoreHeightMin = 180.0;
  static const double normalCoreHeightMax = 260.0;
  static const double normalCoreHeightFactor = 0.34;

  static const double keyboardCoreHeightMin = 96.0;
  static const double keyboardCoreHeightMax = 120.0;
  static const double keyboardCoreHeightFactor = 0.22;

  static const double coreVisibilityMinMessageHeight = 160.0;
  static const Duration coreTransitionDuration = Duration(milliseconds: 300);

  static const double messageViewportHorizontalPadding = 16.0;

  static const double composerMinHeight = 56.0;
  static const double composerMaxHeight = 136.0;
  static const double composerHorizontalPadding = 16.0;
  static const double composerVerticalPadding = 6.0;
  static const int composerMinLines = 1;
  static const int composerMaxLines = 5;

  static const double sendButtonMinSize = 44.0;

  /// Core Region height while the Software Keyboard is not shown.
  static double normalCoreHeight(double bodyHeight) {
    final raw = bodyHeight * normalCoreHeightFactor;
    return math.min(math.max(raw, normalCoreHeightMin), normalCoreHeightMax);
  }

  /// Core Region height while the Software Keyboard is shown.
  static double keyboardCoreHeight(double bodyHeight) {
    final raw = bodyHeight * keyboardCoreHeightFactor;
    return math.min(
      math.max(raw, keyboardCoreHeightMin),
      keyboardCoreHeightMax,
    );
  }

  /// Resolves the Core Region height for the current keyboard state.
  static double coreHeight(double bodyHeight, {required bool keyboardVisible}) {
    return keyboardVisible
        ? keyboardCoreHeight(bodyHeight)
        : normalCoreHeight(bodyHeight);
  }

  /// Whether Core should remain visible given the resulting Message
  /// Viewport height. Message, Composer and Focus are always prioritized
  /// over Core; exact Dynamic Type behavior is finalized in FIP-012.
  static bool isCoreVisible({
    required double bodyHeight,
    required bool keyboardVisible,
  }) {
    final resolvedCoreHeight = coreHeight(
      bodyHeight,
      keyboardVisible: keyboardVisible,
    );
    return (bodyHeight - resolvedCoreHeight) >= coreVisibilityMinMessageHeight;
  }
}
