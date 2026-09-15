import 'dart:io';

import 'package:alice/app/theme/alice_theme.dart';
import 'package:alice/conversation/presentation/widget/alice_core/alice_core_visual_state.dart';
import 'package:alice/conversation/presentation/widget/alice_core_region.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

class _TestAssetBundle extends CachingAssetBundle {
  @override
  Future<ByteData> load(String key) async {
    final file = File(key);
    if (file.existsSync()) {
      final bytes = await file.readAsBytes();
      return ByteData.sublistView(bytes);
    }
    return rootBundle.load(key);
  }
}

void main() {
  Widget buildTestable(double height) {
    return MaterialApp(
      theme: AliceTheme.darkTheme,
      home: Scaffold(
        body: DefaultAssetBundle(
          bundle: _TestAssetBundle(),
          // The Region fills whatever height its Parent gives it; in real
          // usage that is the animated Core height slot in
          // ConversationScreenShell.
          child: SizedBox(
            height: height,
            child: AliceCoreRegion(
              visualState: AliceCoreVisualState.idle,
              height: height,
            ),
          ),
        ),
      ),
    );
  }

  testWidgets('fits within the normal 180-260 Region height without overflow', (
    tester,
  ) async {
    await tester.pumpWidget(buildTestable(220));

    expect(tester.takeException(), isNull);
    final size = tester.getSize(find.byType(AliceCoreRegion));
    expect(size.height, 220.0);
  });

  testWidgets(
    'fits within the Keyboard 96-120 Region height without overflow',
    (tester) async {
      await tester.pumpWidget(buildTestable(96));

      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('renders no Scrollable inside the Region', (tester) async {
    await tester.pumpWidget(buildTestable(200));

    expect(find.byType(Scrollable), findsNothing);
  });
}
