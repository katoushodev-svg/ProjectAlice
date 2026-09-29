import 'dart:convert';

import 'api_contract_exception.dart';
import 'sse_frame.dart';

class SseFrameParser {
  SseFrameParser._();

  static const int defaultMaxFrameBytes = 1 * 1024 * 1024;
  static const int defaultMaxStreamBytes = 8 * 1024 * 1024;
  static const int defaultMaxNonCommentEvents = 10000;

  static List<SseFrame> parseStream(List<int> bytes) {
    try {
      final rawText = utf8.decode(bytes, allowMalformed: false);
      final lines = const LineSplitter().convert(rawText);
      String? currentEventName;
      final dataLines = <String>[];
      final frames = <SseFrame>[];

      void flushCurrentFrame() {
        if (currentEventName == null || dataLines.isEmpty) {
          throw const ApiContractException(category: 'invalidSseFrame');
        }
        frames.add(
          SseFrame(eventName: currentEventName!, data: dataLines.join('\n')),
        );
        currentEventName = null;
        dataLines.clear();
      }

      for (final line in lines) {
        if (line.isEmpty) {
          if (currentEventName == null && dataLines.isEmpty) {
            continue;
          }
          flushCurrentFrame();
          continue;
        }
        if (line.startsWith(':')) continue;
        if (line.startsWith('event:')) {
          currentEventName = line.substring('event:'.length).trim();
          continue;
        }
        if (line.startsWith('data:')) {
          dataLines.add(line.substring('data:'.length).trimLeft());
          continue;
        }
        throw const ApiContractException(category: 'invalidSseFrame');
      }

      if (currentEventName != null || dataLines.isNotEmpty) {
        throw const ApiContractException(category: 'incompleteSseFrame');
      }
      return frames;
    } on ApiContractException {
      rethrow;
    } on FormatException {
      throw const ApiContractException(category: 'invalidUtf8');
    }
  }

  static Stream<SseFrame> parseIncrementally(
    Stream<List<int>> chunks, {
    int maxFrameBytes = defaultMaxFrameBytes,
    int maxStreamBytes = defaultMaxStreamBytes,
    int maxNonCommentEvents = defaultMaxNonCommentEvents,
  }) async* {
    if (maxFrameBytes < 1 || maxStreamBytes < 1 || maxNonCommentEvents < 1) {
      throw ArgumentError('SSE parser limits must be positive.');
    }

    var totalBytes = 0;
    var nonCommentEvents = 0;
    var textBuffer = '';
    String? eventName;
    final dataLines = <String>[];
    final completedFrames = <SseFrame>[];
    var frameBytes = 0;
    var frameHasContent = false;

    void processLine(String rawLine, int delimiterBytes) {
      frameBytes += utf8.encode(rawLine).length + delimiterBytes;
      if (frameBytes > maxFrameBytes) {
        throw const ApiContractException(category: 'sseFrameTooLarge');
      }

      if (rawLine.isEmpty) {
        if (!frameHasContent) {
          frameBytes = 0;
          return;
        }
        if (eventName == null || dataLines.isEmpty) {
          throw const ApiContractException(category: 'invalidSseFrame');
        }

        completedFrames.add(
          SseFrame(eventName: eventName!, data: dataLines.join('\n')),
        );
        nonCommentEvents++;
        if (nonCommentEvents > maxNonCommentEvents) {
          throw const ApiContractException(category: 'tooManySseEvents');
        }

        eventName = null;
        dataLines.clear();
        frameBytes = 0;
        frameHasContent = false;
        return;
      }

      if (rawLine.startsWith(':')) return;

      frameHasContent = true;

      if (rawLine.startsWith('event:')) {
        eventName = rawLine.substring('event:'.length).trim();
        return;
      }

      if (rawLine.startsWith('data:')) {
        dataLines.add(rawLine.substring('data:'.length).trimLeft());
        return;
      }

      throw const ApiContractException(category: 'invalidSseFrame');
    }

    try {
      Stream<List<int>> countedChunks() async* {
        await for (final chunk in chunks) {
          totalBytes += chunk.length;
          if (totalBytes > maxStreamBytes) {
            throw const ApiContractException(category: 'sseStreamTooLarge');
          }
          yield chunk;
        }
      }

      await for (final decodedChunk in utf8.decoder.bind(countedChunks())) {
        textBuffer += decodedChunk;

        var newlineIndex = textBuffer.indexOf('\n');
        while (newlineIndex >= 0) {
          var rawLine = textBuffer.substring(0, newlineIndex);
          textBuffer = textBuffer.substring(newlineIndex + 1);

          var delimiterBytes = 1;
          if (rawLine.endsWith('\r')) {
            rawLine = rawLine.substring(0, rawLine.length - 1);
            delimiterBytes = 2;
          }

          processLine(rawLine, delimiterBytes);

          while (completedFrames.isNotEmpty) {
            yield completedFrames.removeAt(0);
          }

          newlineIndex = textBuffer.indexOf('\n');
        }

        if (frameBytes + utf8.encode(textBuffer).length > maxFrameBytes) {
          throw const ApiContractException(category: 'sseFrameTooLarge');
        }
      }
    } on ApiContractException {
      rethrow;
    } on FormatException {
      throw const ApiContractException(category: 'invalidUtf8');
    }

    if (textBuffer.isNotEmpty || frameHasContent) {
      throw const ApiContractException(category: 'incompleteSseFrame');
    }
  }
}
