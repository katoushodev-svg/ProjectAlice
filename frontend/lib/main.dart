import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/alice_app.dart';
import 'app/app_configuration.dart';
import 'app/app_providers.dart';

void main() {
  final appConfig = AppConfiguration.fromEnvironment(
    allowLocalHttp: kDebugMode,
  );

  runApp(
    ProviderScope(
      overrides: [appConfigurationProvider.overrideWithValue(appConfig)],
      child: const AliceApp(),
    ),
  );
}
