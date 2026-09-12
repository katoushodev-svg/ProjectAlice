import 'package:flutter/material.dart';

import 'alice_color_tokens.dart';
import 'alice_text_styles.dart';

/// Phase 1 Dark-only ThemeData for Project Alice.
abstract final class AliceTheme {
  static ThemeData get darkTheme {
    const colorScheme = ColorScheme.dark(
      brightness: Brightness.dark,
      primary: AliceColorTokens.primary,
      surface: AliceColorTokens.surface,
      error: AliceColorTokens.error,
      onError: AliceColorTokens.errorSurface,
      onPrimary: AliceColorTokens.textPrimary,
      onSurface: AliceColorTokens.textPrimary,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: AliceColorTokens.background,
      colorScheme: colorScheme,
      canvasColor: AliceColorTokens.background,
      cardColor: AliceColorTokens.surface,
      dividerColor: AliceColorTokens.border,
      textTheme:
          const TextTheme(
            titleLarge: AliceTextStyles.title,
            bodyLarge: AliceTextStyles.body,
            bodyMedium: AliceTextStyles.body,
            bodySmall: AliceTextStyles.supporting,
            labelSmall: AliceTextStyles.caption,
          ).apply(
            bodyColor: AliceColorTokens.textPrimary,
            displayColor: AliceColorTokens.textPrimary,
          ),
    );
  }
}
