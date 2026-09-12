import 'package:flutter/material.dart';

/// Approved Typography Tokens for Project Alice.
abstract final class AliceTextStyles {
  static const TextStyle title = TextStyle(
    fontSize: 22.0,
    fontWeight: FontWeight.w400,
  );

  static const TextStyle body = TextStyle(
    fontSize: 17.0,
    fontWeight: FontWeight.w400,
    height: 1.45,
  );

  static const TextStyle bodyEmphasis = TextStyle(
    fontSize: 17.0,
    fontWeight: FontWeight.w600,
    height: 1.45,
  );

  static const TextStyle supporting = TextStyle(
    fontSize: 14.0,
    fontWeight: FontWeight.w400,
  );

  static const TextStyle caption = TextStyle(
    fontSize: 12.0,
    fontWeight: FontWeight.w400,
  );

  static const TextStyle code = TextStyle(
    fontSize: 14.0,
    fontWeight: FontWeight.w400,
    fontFamily: 'monospace',
  );
}
