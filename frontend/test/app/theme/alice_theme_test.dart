import 'package:alice/app/theme/alice_color_tokens.dart';
import 'package:alice/app/theme/alice_design_tokens.dart';
import 'package:alice/app/theme/alice_text_styles.dart';
import 'package:alice/app/theme/alice_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Alice Color Tokens', () {
    test('contains expected approved color hex values', () {
      expect(AliceColorTokens.background, equals(const Color(0xFF020812)));
      expect(
        AliceColorTokens.backgroundElevated,
        equals(const Color(0xFF07111F)),
      );
      expect(AliceColorTokens.surface, equals(const Color(0xFF0D1828)));
      expect(AliceColorTokens.surfaceStrong, equals(const Color(0xFF0D3A73)));
      expect(AliceColorTokens.textPrimary, equals(const Color(0xFFF5F8FF)));
      expect(AliceColorTokens.textSecondary, equals(const Color(0xFFAEB9CA)));
      expect(AliceColorTokens.border, equals(const Color(0xFF1B304A)));
      expect(AliceColorTokens.primary, equals(const Color(0xFF1677E8)));
      expect(AliceColorTokens.coreCyan, equals(const Color(0xFF00D9FF)));
      expect(AliceColorTokens.coreBlue, equals(const Color(0xFF176BFF)));
      expect(AliceColorTokens.coreViolet, equals(const Color(0xFF6C45FF)));
      expect(AliceColorTokens.coreAmber, equals(const Color(0xFFF6A623)));
      expect(AliceColorTokens.error, equals(const Color(0xFFFFB4AB)));
      expect(AliceColorTokens.errorSurface, equals(const Color(0xFF3B1D1C)));
    });
  });

  group('Alice Design Tokens', () {
    test('spacing tokens match specification', () {
      expect(AliceSpacingTokens.space1, equals(4.0));
      expect(AliceSpacingTokens.space2, equals(8.0));
      expect(AliceSpacingTokens.space3, equals(12.0));
      expect(AliceSpacingTokens.space4, equals(16.0));
      expect(AliceSpacingTokens.space6, equals(24.0));
      expect(AliceSpacingTokens.space8, equals(32.0));
    });

    test('radius tokens match specification', () {
      expect(AliceRadiusTokens.radiusSmall, equals(4.0));
      expect(AliceRadiusTokens.radiusMedium, equals(12.0));
      expect(AliceRadiusTokens.radiusLarge, equals(18.0));
      expect(AliceRadiusTokens.radiusPill, equals(999.0));
    });
  });

  group('Alice Text Styles', () {
    test('typography tokens match specification and line height', () {
      expect(AliceTextStyles.title.fontSize, equals(22.0));
      expect(AliceTextStyles.title.fontWeight, equals(FontWeight.w400));

      expect(AliceTextStyles.body.fontSize, equals(17.0));
      expect(AliceTextStyles.body.fontWeight, equals(FontWeight.w400));
      expect(AliceTextStyles.body.height, equals(1.45));

      expect(AliceTextStyles.bodyEmphasis.fontSize, equals(17.0));
      expect(AliceTextStyles.bodyEmphasis.fontWeight, equals(FontWeight.w600));
      expect(AliceTextStyles.bodyEmphasis.height, equals(1.45));

      expect(AliceTextStyles.supporting.fontSize, equals(14.0));
      expect(AliceTextStyles.supporting.fontWeight, equals(FontWeight.w400));

      expect(AliceTextStyles.caption.fontSize, equals(12.0));
      expect(AliceTextStyles.caption.fontWeight, equals(FontWeight.w400));

      expect(AliceTextStyles.code.fontSize, equals(14.0));
      expect(AliceTextStyles.code.fontWeight, equals(FontWeight.w400));
      expect(AliceTextStyles.code.fontFamily, equals('monospace'));
    });

    test('does not introduce custom font family assets', () {
      expect(AliceTextStyles.title.fontFamily, isNull);
      expect(AliceTextStyles.body.fontFamily, isNull);
      expect(AliceTextStyles.supporting.fontFamily, isNull);
    });
  });

  group('Alice Theme', () {
    test('is configured for dark brightness only', () {
      final theme = AliceTheme.darkTheme;
      expect(theme.brightness, equals(Brightness.dark));
      expect(
        theme.scaffoldBackgroundColor,
        equals(AliceColorTokens.background),
      );
      expect(theme.colorScheme.primary, equals(AliceColorTokens.primary));
      expect(theme.colorScheme.surface, equals(AliceColorTokens.surface));
      expect(theme.colorScheme.error, equals(AliceColorTokens.error));
    });
  });
}
