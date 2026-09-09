import 'dart:convert';

import 'api_contract_exception.dart';
import 'sse_frame.dart';

class SseFrameParser {
  SseFrameParser._();

  static List<SseFrame> parseStream(List<int> bytes) {
    final rawText = _decodeUtf8(bytes);
    final lines = const LineSplitter().convert(rawText);

    String? currentEventName;
    final List<String> currentDataLines = [];
    final List<SseFrame> frames = [];

    void flushCurrentFrame() {
      if (currentEventName == null || currentDataLines.isEmpty) {
        throw ApiContractException(category: 'invalidSseFrame');
      }

      frames.add(
        SseFrame(
          eventName: currentEventName!,
          data: currentDataLines.join('\n'),
        ),
      );
      currentEventName = null;
      currentDataLines.clear();
    }

    for (final line in lines) {
      if (line.isEmpty) {
        if (currentEventName == null && currentDataLines.isEmpty) {
          continue;
        }
        flushCurrentFrame();
        continue;
      }

      if (line.startsWith(':')) {
        continue;
      }

      if (line.startsWith('event:')) {
        currentEventName = line.substring('event:'.length).trim();
        continue;
      }

      if (line.startsWith('data:')) {
        currentDataLines.add(line.substring('data:'.length).trimLeft());
        continue;
      }
    }

    if (currentEventName != null || currentDataLines.isNotEmpty) {
      throw ApiContractException(category: 'incompleteSseFrame');
    }

    return frames;
  }

  static String _decodeUtf8(List<int> bytes) {
    try {
      return utf8.decode(bytes, allowMalformed: false);
    } on FormatException {
      throw ApiContractException(category: 'invalidUtf8');
    }
  }
}
