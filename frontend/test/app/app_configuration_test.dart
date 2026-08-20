import 'package:flutter_test/flutter_test.dart';

import 'package:alice/app/app_configuration.dart';

void main() {
  group('AppConfiguration Validation', () {
    test('accepts valid HTTPS URL', () {
      final config = AppConfiguration.fromRaw(
        rawBaseUrl: 'https://api.example.com',
        allowLocalHttp: false,
      );
      expect(config.baseUrl.scheme, 'https');
      expect(config.baseUrl.host, 'api.example.com');
      expect(config.baseUrl.path, '');
    });

    test('accepts valid loopback HTTP with allowLocalHttp=true', () {
      final config = AppConfiguration.fromRaw(
        rawBaseUrl: 'http://127.0.0.1:8080',
        allowLocalHttp: true,
      );
      expect(config.baseUrl.scheme, 'http');
      expect(config.baseUrl.host, '127.0.0.1');
      expect(config.baseUrl.port, 8080);
    });

    test('normalizes URL without trailing slash', () {
      final config = AppConfiguration.fromRaw(
        rawBaseUrl: 'https://api.example.com/',
        allowLocalHttp: false,
      );
      expect(config.baseUrl.path, '');
      expect(config.baseUrl.toString(), 'https://api.example.com');
    });

    test('rejects empty URL', () {
      expect(
        () => AppConfiguration.fromRaw(rawBaseUrl: '', allowLocalHttp: false),
        throwsA(isA<AppConfigurationException>()),
      );
    });

    test('rejects whitespace-only URL', () {
      expect(
        () =>
            AppConfiguration.fromRaw(rawBaseUrl: '   ', allowLocalHttp: false),
        throwsA(isA<AppConfigurationException>()),
      );
    });

    test('rejects URL with leading whitespace', () {
      expect(
        () => AppConfiguration.fromRaw(
          rawBaseUrl: ' https://api.example.com',
          allowLocalHttp: false,
        ),
        throwsA(isA<AppConfigurationException>()),
      );
    });

    test('rejects URL with trailing whitespace', () {
      expect(
        () => AppConfiguration.fromRaw(
          rawBaseUrl: 'https://api.example.com ',
          allowLocalHttp: false,
        ),
        throwsA(isA<AppConfigurationException>()),
      );
    });

    test('rejects relative URI', () {
      expect(
        () => AppConfiguration.fromRaw(
          rawBaseUrl: '/api/v1',
          allowLocalHttp: false,
        ),
        throwsA(isA<AppConfigurationException>()),
      );
    });

    test('rejects unsupported scheme', () {
      expect(
        () => AppConfiguration.fromRaw(
          rawBaseUrl: 'ftp://example.com',
          allowLocalHttp: false,
        ),
        throwsA(isA<AppConfigurationException>()),
      );

      try {
        AppConfiguration.fromRaw(
          rawBaseUrl: 'ftp://example.com',
          allowLocalHttp: false,
        );
        fail('Should throw AppConfigurationException');
      } on AppConfigurationException catch (e) {
        expect(e.message, equals('Base URL scheme must be http or https'));
        expect(e.message, isNot(contains('ftp')));
        expect(e.message, isNot(contains('example.com')));
      }
    });

    test('rejects HTTP for non-loopback host', () {
      expect(
        () => AppConfiguration.fromRaw(
          rawBaseUrl: 'http://api.example.com',
          allowLocalHttp: true,
        ),
        throwsA(isA<AppConfigurationException>()),
      );
    });

    test('rejects loopback HTTP when allowLocalHttp=false', () {
      expect(
        () => AppConfiguration.fromRaw(
          rawBaseUrl: 'http://127.0.0.1:8080',
          allowLocalHttp: false,
        ),
        throwsA(isA<AppConfigurationException>()),
      );
    });

    test('rejects URL with user info', () {
      expect(
        () => AppConfiguration.fromRaw(
          rawBaseUrl: 'https://user:pass@api.example.com',
          allowLocalHttp: false,
        ),
        throwsA(isA<AppConfigurationException>()),
      );
    });

    test('rejects URL with query parameters', () {
      expect(
        () => AppConfiguration.fromRaw(
          rawBaseUrl: 'https://api.example.com?key=value',
          allowLocalHttp: false,
        ),
        throwsA(isA<AppConfigurationException>()),
      );
    });

    test('rejects URL with fragment', () {
      expect(
        () => AppConfiguration.fromRaw(
          rawBaseUrl: 'https://api.example.com#section',
          allowLocalHttp: false,
        ),
        throwsA(isA<AppConfigurationException>()),
      );
    });

    test('rejects URL with nested path', () {
      expect(
        () => AppConfiguration.fromRaw(
          rawBaseUrl: 'https://api.example.com/api/v1',
          allowLocalHttp: false,
        ),
        throwsA(isA<AppConfigurationException>()),
      );
    });

    test('exception does not expose sanitization details', () {
      final exception = AppConfigurationException('Safe message');
      expect(exception.toString(), contains('Safe message'));
      expect(exception.message, 'Safe message');
    });

    test('exception message is safe from URL exposure', () {
      try {
        AppConfiguration.fromRaw(
          rawBaseUrl: 'http://api.example.com:1234/api/v1',
          allowLocalHttp: false,
        );
        fail('Should throw AppConfigurationException');
      } on AppConfigurationException catch (e) {
        expect(e.message, isNot(contains('http://')));
        expect(e.message, isNot(contains('api.example.com')));
        expect(e.message, isNot(contains(':1234')));
      }
    });

    test('accepts root path URL', () {
      final config = AppConfiguration.fromRaw(
        rawBaseUrl: 'https://api.example.com',
        allowLocalHttp: false,
      );
      expect(config.baseUrl.path, isEmpty);
    });
  });
}
