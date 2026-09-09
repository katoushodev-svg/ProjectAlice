import 'package:flutter_test/flutter_test.dart';

import 'package:alice/conversation/infrastructure/api/parsing/api_contract_exception.dart';
import 'package:alice/conversation/infrastructure/api/parsing/api_timestamp_parser.dart';

void main() {
  group('ApiTimestampParser', () {
    test('accepts valid JST timestamp with milliseconds', () {
      final parsed = ApiTimestampParser.parse('2026-08-14T15:00:02.000+09:00');

      expect(parsed, DateTime.parse('2026-08-14T06:00:02.000Z'));
    });

    test('rejects non-JST offset', () {
      expect(
        () => ApiTimestampParser.parse('2026-08-14T06:00:02.000Z'),
        throwsA(isA<Exception>()),
      );
    });
  });

  group('ApiContractException', () {
    test('toString does not expose raw field or message values', () {
      const exception = ApiContractException(
        category: 'invalidSseFrame',
        field: 'data',
        message: 'secret payload',
      );

      final output = exception.toString();

      expect(output, contains('invalidSseFrame'));
      expect(output, isNot(contains('data')));
      expect(output, isNot(contains('secret payload')));
    });
  });
}
