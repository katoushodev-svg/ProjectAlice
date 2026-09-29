import 'dart:convert';

import 'package:alice/conversation/infrastructure/api/parsing/api_contract_exception.dart';
import 'package:alice/conversation/infrastructure/api/parsing/sse_frame_parser.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('parses multiple SSE frames and preserves UTF-8 content', () {
    final bytes = utf8.encode(
      ': keep-alive\r\n'
      'event: assistant.delta\r\n'
      'data: {"delta":"こんにちは😀"}\r\n'
      '\r\n'
      'event: assistant.completed\r\n'
      'data: {"messages":[]}\r\n'
      '\r\n',
    );

    final frames = SseFrameParser.parseStream(bytes);

    expect(frames, hasLength(2));
    expect(frames.first.eventName, 'assistant.delta');
    expect(frames.first.data, '{"delta":"こんにちは😀"}');
    expect(frames.last.eventName, 'assistant.completed');
  });

  test('rejects malformed UTF-8', () {
    expect(
      () => SseFrameParser.parseStream(<int>[0xff, 0xfe]),
      throwsA(isA<ApiContractException>()),
    );
  });
}
