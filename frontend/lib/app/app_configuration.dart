/// Exception thrown when AppConfiguration validation fails.
class AppConfigurationException implements Exception {
  /// Creates an AppConfigurationException.
  ///
  /// The message must not contain actual URLs, hosts, credentials, or secrets.
  AppConfigurationException(this.message);

  /// The error message, safe from exposing sensitive data.
  final String message;

  @override
  String toString() => 'AppConfigurationException: $message';
}

/// Immutable application configuration holding the backend base URL.
class AppConfiguration {
  static const String _defaultHttpRequestTimeoutSeconds = '10';
  static const String _defaultSseClientTimeoutSeconds = '190';

  static const int _minHttpRequestTimeoutSeconds = 1;
  static const int _maxHttpRequestTimeoutSeconds = 60;
  static const int _minSseClientTimeoutSeconds = 180;
  static const int _maxSseClientTimeoutSeconds = 600;

  /// Creates an AppConfiguration with a validated base URL.
  ///
  /// The baseUrl is stored as a normalized origin (scheme + host, no trailing slash).
  AppConfiguration._(
    this.baseUrl,
    this.httpRequestTimeout,
    this.sseClientTimeout,
  );

  /// The backend base URL as a Uri, normalized without trailing slash.
  final Uri baseUrl;

  /// Timeout for receiving HTTP response headers.
  final Duration httpRequestTimeout;

  /// Maximum client-side duration from POST dispatch until terminal SSE event.
  final Duration sseClientTimeout;

  /// Creates AppConfiguration by reading ALICE_API_BASE_URL from the build environment.
  ///
  /// Throws [AppConfigurationException] if the URL is invalid or constraints are violated.
  ///
  /// - [allowLocalHttp]: Whether HTTP scheme is allowed (typically true in Debug builds)
  factory AppConfiguration.fromEnvironment({required bool allowLocalHttp}) {
    const rawUrl = String.fromEnvironment('ALICE_API_BASE_URL');
    const rawHttpRequestTimeout = String.fromEnvironment(
      'ALICE_HTTP_REQUEST_TIMEOUT_SECONDS',
      defaultValue: _defaultHttpRequestTimeoutSeconds,
    );
    const rawSseClientTimeout = String.fromEnvironment(
      'ALICE_SSE_CLIENT_TIMEOUT_SECONDS',
      defaultValue: _defaultSseClientTimeoutSeconds,
    );

    return AppConfiguration.fromRaw(
      rawBaseUrl: rawUrl,
      allowLocalHttp: allowLocalHttp,
      rawHttpRequestTimeoutSeconds: rawHttpRequestTimeout,
      rawSseClientTimeoutSeconds: rawSseClientTimeout,
    );
  }

  /// Creates AppConfiguration from a raw URL string with full validation.
  ///
  /// Throws [AppConfigurationException] if the URL is invalid or constraints are violated.
  ///
  /// - [rawBaseUrl]: The raw URL string
  /// - [allowLocalHttp]: Whether HTTP scheme is allowed (typically true in Debug builds)
  factory AppConfiguration.fromRaw({
    required String rawBaseUrl,
    required bool allowLocalHttp,
    String? rawHttpRequestTimeoutSeconds,
    String? rawSseClientTimeoutSeconds,
  }) {
    // Check for missing, empty, or whitespace-only
    if (rawBaseUrl.isEmpty || rawBaseUrl.trim().isEmpty) {
      throw AppConfigurationException(
        'Base URL must not be empty or whitespace-only',
      );
    }

    // Check for surrounding whitespace (no implicit trim)
    if (rawBaseUrl != rawBaseUrl.trim()) {
      throw AppConfigurationException(
        'Base URL must not have leading or trailing whitespace',
      );
    }

    // Parse the URI
    Uri uri;
    try {
      uri = Uri.parse(rawBaseUrl);
    } catch (e) {
      throw AppConfigurationException('Base URL is not a valid URI');
    }

    // Check for absolute URI with scheme and host
    if (!uri.hasScheme || uri.host.isEmpty) {
      throw AppConfigurationException(
        'Base URL must be an absolute URI with scheme and host',
      );
    }

    // Check scheme is http or https
    final scheme = uri.scheme.toLowerCase();
    if (scheme != 'http' && scheme != 'https') {
      throw AppConfigurationException('Base URL scheme must be http or https');
    }

    // Check http constraint: only allowed for 127.0.0.1 with allowLocalHttp=true
    if (scheme == 'http') {
      if (!allowLocalHttp || uri.host != '127.0.0.1') {
        throw AppConfigurationException(
          'HTTP scheme is not allowed for this configuration',
        );
      }
    }

    // Check for user info
    if (uri.userInfo.isNotEmpty) {
      throw AppConfigurationException('Base URL must not contain user info');
    }

    // Check for query
    if (uri.query.isNotEmpty) {
      throw AppConfigurationException(
        'Base URL must not contain query parameters',
      );
    }

    // Check for fragment
    if (uri.fragment.isNotEmpty) {
      throw AppConfigurationException('Base URL must not contain fragment');
    }

    // Check path: must be empty or /
    final path = uri.path;
    if (path.isNotEmpty && path != '/') {
      throw AppConfigurationException(
        'Base URL path must be empty or /, not a nested path',
      );
    }

    // Normalize to origin without trailing slash
    final normalizedUri = Uri(scheme: scheme, host: uri.host, port: uri.port);

    final httpRequestTimeoutSeconds = _parseTimeoutSeconds(
      rawHttpRequestTimeoutSeconds ?? _defaultHttpRequestTimeoutSeconds,
      name: 'ALICE_HTTP_REQUEST_TIMEOUT_SECONDS',
      minSeconds: _minHttpRequestTimeoutSeconds,
      maxSeconds: _maxHttpRequestTimeoutSeconds,
    );

    final sseClientTimeoutSeconds = _parseTimeoutSeconds(
      rawSseClientTimeoutSeconds ?? _defaultSseClientTimeoutSeconds,
      name: 'ALICE_SSE_CLIENT_TIMEOUT_SECONDS',
      minSeconds: _minSseClientTimeoutSeconds,
      maxSeconds: _maxSseClientTimeoutSeconds,
    );

    return AppConfiguration._(
      normalizedUri,
      Duration(seconds: httpRequestTimeoutSeconds),
      Duration(seconds: sseClientTimeoutSeconds),
    );
  }

  static int _parseTimeoutSeconds(
    String raw, {
    required String name,
    required int minSeconds,
    required int maxSeconds,
  }) {
    if (!RegExp(r'^[0-9]+$').hasMatch(raw)) {
      throw AppConfigurationException(
        '$name must be an ASCII decimal integer.',
      );
    }

    final seconds = int.tryParse(raw);
    if (seconds == null || seconds < minSeconds || seconds > maxSeconds) {
      throw AppConfigurationException(
        '$name must be between $minSeconds and $maxSeconds seconds.',
      );
    }

    return seconds;
  }
}
