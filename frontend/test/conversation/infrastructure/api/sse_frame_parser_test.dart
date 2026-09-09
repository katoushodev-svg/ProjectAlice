import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import 'package:alice/conversation/infrastructure/api/parsing/sse_frame.dart';
import 'package:alice/conversation/infrastructure/api/parsing/sse_frame_parser.dart';

void main() {
  group('SseFrameParser', () {
    test('parses a standard event with multiple data lines', () {
      final bytes = utf8.encode(
        'event: stream.started\n'
        'data: {"requestId":"abc"}\n'
        'data: second\n'
        '\n',
      );

      final frames = SseFrameParser.parseStream(bytes);

      expect(frames, [
        const SseFrame(
          eventName: 'stream.started',
          data: '{"requestId":"abc"}\nsecond',
        ),
      ]);
    });

    test('toString does not expose raw payload data', () {
      const frame = SseFrame(
        eventName: 'assistant.delta',
        data: 'secret-delta-content',
      );

      final output = frame.toString();

      expect(output, contains('assistant.delta'));
      expect(output, isNot(contains('secret-delta-content')));
    });
  });
}
