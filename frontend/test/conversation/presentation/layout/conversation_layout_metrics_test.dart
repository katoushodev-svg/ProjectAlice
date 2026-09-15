import 'package:alice/conversation/presentation/layout/conversation_layout_metrics.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('ConversationLayoutMetrics.normalCoreHeight', () {
    test('clamps to 180 minimum for small Body height', () {
      expect(ConversationLayoutMetrics.normalCoreHeight(100), 180.0);
    });

    test('clamps to 260 maximum for large Body height', () {
      expect(ConversationLayoutMetrics.normalCoreHeight(2000), 260.0);
    });

    test('applies the 0.34 factor within the clamped range', () {
      expect(
        ConversationLayoutMetrics.normalCoreHeight(700),
        closeTo(238, 0.01),
      );
    });
  });

  group('ConversationLayoutMetrics.keyboardCoreHeight', () {
    test('clamps to 96 minimum for small Body height', () {
      expect(ConversationLayoutMetrics.keyboardCoreHeight(100), 96.0);
    });

    test('clamps to 120 maximum for large Body height', () {
      expect(ConversationLayoutMetrics.keyboardCoreHeight(2000), 120.0);
    });

    test('applies the 0.22 factor within the clamped range', () {
      expect(
        ConversationLayoutMetrics.keyboardCoreHeight(500),
        closeTo(110, 0.01),
      );
    });
  });

  group('ConversationLayoutMetrics.isCoreVisible', () {
    test('hides Core when the resulting Message Viewport is below 160', () {
      expect(
        ConversationLayoutMetrics.isCoreVisible(
          bodyHeight: 300,
          keyboardVisible: false,
        ),
        isFalse,
      );
    });

    test('keeps Core visible when Message Viewport meets 160 or more', () {
      expect(
        ConversationLayoutMetrics.isCoreVisible(
          bodyHeight: 700,
          keyboardVisible: false,
        ),
        isTrue,
      );
    });

    test(
      're-evaluates using the Keyboard Core height while Keyboard shows',
      () {
        expect(
          ConversationLayoutMetrics.isCoreVisible(
            bodyHeight: 260,
            keyboardVisible: true,
          ),
          isTrue,
        );
      },
    );
  });
}
