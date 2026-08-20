import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

import 'package:alice/app/app_configuration.dart';
import 'package:alice/app/app_providers.dart';

/// Mock HTTP client that tracks close() calls for testing.
class TrackingHttpClient extends http.BaseClient {
  TrackingHttpClient() : closeCallCount = 0;

  /// Number of times close() was called.
  int closeCallCount = 0;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    throw UnsupportedError('TrackingHttpClient does not support send()');
  }

  @override
  void close() {
    closeCallCount++;
    super.close();
  }
}

void main() {
  group('AppProviders', () {
    test('appConfigurationProvider throws when not overridden', () {
      final container = ProviderContainer();
      addTearDown(container.dispose);
      expect(() => container.read(appConfigurationProvider), throwsA(anything));
    });

    test('httpClientFactory returns http.Client.new by default', () {
      final container = ProviderContainer();
      addTearDown(container.dispose);
      final factory = container.read(httpClientFactoryProvider);
      final client = factory();
      expect(client, isA<http.Client>());
      client.close();
    });

    test(
      'httpClientProvider returns same client instance in same container',
      () {
        final container = ProviderContainer();
        addTearDown(container.dispose);
        final client1 = container.read(httpClientProvider);
        final client2 = container.read(httpClientProvider);
        expect(client1, same(client2));
      },
    );

    test('httpClientProvider closes client on container dispose', () {
      final trackingClient = TrackingHttpClient();
      final container = ProviderContainer(
        overrides: [
          httpClientFactoryProvider.overrideWithValue(() => trackingClient),
        ],
      );
      var disposed = false;
      addTearDown(() {
        if (!disposed) {
          container.dispose();
          disposed = true;
        }
      });

      final client = container.read(httpClientProvider);
      expect(identical(client, trackingClient), true);
      expect(trackingClient.closeCallCount, 0);

      container.dispose();
      disposed = true;

      expect(trackingClient.closeCallCount, 1);
    });

    test('httpClientProvider creates fresh client in new container', () {
      final container1 = ProviderContainer();
      addTearDown(container1.dispose);
      final client1 = container1.read(httpClientProvider);

      final container2 = ProviderContainer();
      addTearDown(container2.dispose);
      final client2 = container2.read(httpClientProvider);

      expect(client1, isNot(same(client2)));
    });

    test('httpClientProvider with overridden factory in tests', () {
      int factoryCallCount = 0;
      http.Client mockFactory() {
        factoryCallCount++;
        return TrackingHttpClient();
      }

      final container = ProviderContainer(
        overrides: [httpClientFactoryProvider.overrideWithValue(mockFactory)],
      );
      addTearDown(container.dispose);

      // First access creates the client
      final client = container.read(httpClientProvider);
      expect(factoryCallCount, 1);

      // Second access reuses the same client without calling factory again
      final client2 = container.read(httpClientProvider);
      expect(factoryCallCount, 1);
      expect(identical(client, client2), true);
    });

    test('configuration can be overridden for testing', () {
      final testConfig = AppConfiguration.fromRaw(
        rawBaseUrl: 'https://test.local',
        allowLocalHttp: false,
      );

      final container = ProviderContainer(
        overrides: [appConfigurationProvider.overrideWithValue(testConfig)],
      );
      addTearDown(container.dispose);

      final config = container.read(appConfigurationProvider);
      expect(config.baseUrl.host, 'test.local');
    });
  });
}
