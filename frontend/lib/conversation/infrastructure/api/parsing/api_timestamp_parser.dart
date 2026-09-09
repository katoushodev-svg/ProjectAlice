import 'api_contract_exception.dart';

class ApiTimestampParser {
  ApiTimestampParser._();

  static final RegExp _timestampPattern = RegExp(
    r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}\+09:00$',
  );

  static DateTime parse(String rawValue) {
    if (rawValue.trim() != rawValue || !_timestampPattern.hasMatch(rawValue)) {
      throw ApiContractException(category: 'invalidTimestamp');
    }

    try {
      final parsed = DateTime.parse(rawValue);
      return parsed;
    } on FormatException {
      throw ApiContractException(category: 'invalidTimestamp');
    }
  }
}
