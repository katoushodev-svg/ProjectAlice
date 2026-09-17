import 'package:alice/app/theme/alice_theme.dart';
import 'package:alice/conversation/presentation/widget/history/alice_markdown_body.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Widget buildTestable(String content) => MaterialApp(
    theme: AliceTheme.darkTheme,
    home: Scaffold(body: AliceMarkdownBody(content: content)),
  );

  testWidgets('renders supported Markdown as selectable content', (
    tester,
  ) async {
    await tester.pumpWidget(buildTestable('## Heading\n\n- item\n\n`code`'));

    expect(find.text('Heading'), findsOneWidget);
    expect(find.text('item'), findsOneWidget);
    expect(find.text('code'), findsOneWidget);
  });

  testWidgets('does not build image widgets or navigate unsafe links', (
    tester,
  ) async {
    await tester.pumpWidget(
      buildTestable(
        '<script>unsafe</script>\n\n![image](https://example.test/image.png)\n\n[link](javascript:alert(1))',
      ),
    );

    expect(find.byType(Image), findsNothing);
    await tester.tap(find.text('link'));
    expect(tester.takeException(), isNull);
  });
}
