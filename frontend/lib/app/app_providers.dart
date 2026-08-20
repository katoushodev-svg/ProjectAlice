import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

import 'app_configuration.dart';

/// Type alias for a function that creates HTTP clients.
typedef HttpClientFactory = http.Client Function();

/// Provider for the application configuration.
///
/// Must be overridden with a valid [AppConfiguration] before use.
/// Throws [UnimplementedError] if accessed without being overridden.
final appConfigurationProvider = Provider<AppConfiguration>((ref) {
  throw UnimplementedError(
    'appConfigurationProvider must be overridden with a valid AppConfiguration',
  );
});

/// Provider for the HTTP client factory function.
///
/// Returns a function that creates [http.Client] instances.
/// Can be overridden in tests to provide a tracking factory.
final httpClientFactoryProvider = Provider<HttpClientFactory>((ref) {
  return http.Client.new;
});

/// Provider for a shared HTTP client instance.
///
/// Creates a single [http.Client] instance per [ProviderContainer].
/// The client is automatically closed when the container is disposed.
///
/// This is a Factory provider that ensures:
/// - Exactly one client instance per container
/// - Automatic resource cleanup via `ref.onDispose`
/// - Injectable factory for testing
final httpClientProvider = Provider<http.Client>((ref) {
  final factory = ref.watch(httpClientFactoryProvider);
  final client = factory();

  ref.onDispose(() {
    client.close();
  });

  return client;
});
