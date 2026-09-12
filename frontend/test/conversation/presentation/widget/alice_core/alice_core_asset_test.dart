import 'dart:io';

import 'package:alice/conversation/presentation/widget/alice_core/alice_core_assets.dart';
import 'package:flutter_test/flutter_test.dart';

/// Reads top-level PNG chunk types from a PNG byte buffer.
///
/// This only walks chunk headers (type + length); it does not decompress or
/// interpret chunk payloads.
List<String> _pngChunkTypes(List<int> bytes) {
  final types = <String>[];
  var i = 8; // skip 8-byte PNG signature
  while (i + 8 <= bytes.length) {
    final length =
        (bytes[i] << 24) |
        (bytes[i + 1] << 16) |
        (bytes[i + 2] << 8) |
        bytes[i + 3];
    final type = String.fromCharCodes(bytes.sublist(i + 4, i + 8));
    types.add(type);
    i += 8 + length + 4; // header + data + CRC
    if (type == 'IEND') {
      break;
    }
  }
  return types;
}

/// Returns the byte offset of the data section of the first chunk matching
/// [chunkType], or `null` if no such chunk exists.
int? _findChunkDataOffset(List<int> bytes, String chunkType) {
  var i = 8;
  while (i + 8 <= bytes.length) {
    final length =
        (bytes[i] << 24) |
        (bytes[i + 1] << 16) |
        (bytes[i + 2] << 8) |
        bytes[i + 3];
    final type = String.fromCharCodes(bytes.sublist(i + 4, i + 8));
    if (type == chunkType) {
      return i + 8;
    }
    i += 8 + length + 4;
    if (type == 'IEND') {
      break;
    }
  }
  return null;
}

void main() {
  group('Alice Core Asset Validation', () {
    final assetFile = File(AliceCoreAssets.coreBase);

    test('asset file exists at canonical path', () {
      expect(assetFile.existsSync(), isTrue);
    });

    test('asset file size is within 2 MB limit', () {
      final bytes = assetFile.readAsBytesSync();
      expect(bytes.length, lessThanOrEqualTo(2 * 1024 * 1024));
    });

    test('asset file is valid 1024x1024 PNG with RGBA alpha channel', () {
      final bytes = assetFile.readAsBytesSync();

      // PNG signature
      const pngSignature = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];
      expect(
        bytes.sublist(0, 8),
        equals(pngSignature),
        reason: 'Asset must be a valid PNG file',
      );

      // IHDR chunk: width at offset 16..19, height at offset 20..23
      final width =
          (bytes[16] << 24) | (bytes[17] << 16) | (bytes[18] << 8) | bytes[19];
      final height =
          (bytes[20] << 24) | (bytes[21] << 16) | (bytes[22] << 8) | bytes[23];

      expect(width, equals(1024));
      expect(height, equals(1024));

      // Color type at offset 25: 6 indicates Truecolor with Alpha
      final colorType = bytes[25];
      expect(
        colorType,
        equals(6),
        reason: 'Asset must have RGBA alpha channel',
      );
    });

    test(
      'asset does not declare a non-sRGB color profile (chunk-level check)',
      () {
        // Standard `dart:io` / `dart:ui` cannot verify an image's rendered
        // color space (pixel-level colorimetry) without a full ICC-aware
        // color pipeline, which is out of scope for a fast, deterministic
        // Flutter Test. Instead this test performs a metadata-level check
        // of the PNG ancillary chunks defined by the PNG spec:
        //
        // - If an `sRGB` chunk is present, the asset explicitly declares
        //   sRGB and this test passes.
        // - If a `gAMA` chunk is present, its value must match the sRGB
        //   gamma (45455, i.e. 1/2.2 scaled by 100000); any other value
        //   indicates a non-sRGB gamma and fails this test.
        // - If an `iCCP` chunk is present, an embedded (possibly non-sRGB)
        //   ICC profile exists; this test flags that for manual review
        //   rather than guessing the profile identity from raw bytes.
        // - If none of `sRGB` / `gAMA` / `iCCP` are present, the asset
        //   relies on the PNG spec's implicit sRGB assumption. This is the
        //   common, expected case for this asset and is accepted here, but
        //   it is not a colorimetric proof. See the FIP-006 Completion
        //   Report for this limitation.
        final bytes = assetFile.readAsBytesSync();
        final chunkTypes = _pngChunkTypes(bytes);

        expect(
          chunkTypes.contains('iCCP'),
          isFalse,
          reason:
              'Embedded ICC profile chunk found; cannot confirm sRGB from '
              'chunk type alone. Re-export the asset without an ICC '
              'profile, or explicitly review the embedded profile.',
        );

        if (chunkTypes.contains('gAMA')) {
          final gamaIndex = _findChunkDataOffset(bytes, 'gAMA');
          expect(gamaIndex, isNotNull);
          final gamma =
              (bytes[gamaIndex!] << 24) |
              (bytes[gamaIndex + 1] << 16) |
              (bytes[gamaIndex + 2] << 8) |
              bytes[gamaIndex + 3];
          expect(
            gamma,
            equals(45455),
            reason: 'gAMA chunk gamma does not match the sRGB gamma value',
          );
        }
      },
    );
  });
}
