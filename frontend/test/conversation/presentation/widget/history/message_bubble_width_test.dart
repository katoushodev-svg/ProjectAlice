import 'package:alice/app/theme/alice_theme.dart';
import 'package:alice/conversation/presentation/widget/history/alice_markdown_body.dart';
import 'package:alice/conversation/presentation/widget/history/alice_message_bubble.dart';
import 'package:alice/conversation/presentation/widget/history/user_message_bubble.dart';
import 'package:alice/conversation/presentation/widget/streaming_assistant_message.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

const _viewportHorizontalPadding = 16.0;
const _widths = [375.0, 390.0, 430.0];

Finder _bubbleContainer(Type bubbleType) => find.descendant(
  of: find.byType(bubbleType),
  matching: find.byWidgetPredicate(
    (widget) =>
        widget is Container &&
        widget.decoration is BoxDecoration &&
        widget.padding ==
            const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
  ),
);

Widget _testApp({
  required double width,
  required Widget child,
  TextScaler textScaler = TextScaler.noScaling,
}) {
  return MaterialApp(
    theme: AliceTheme.darkTheme,
    home: MediaQuery(
      data: MediaQueryData(size: Size(width, 844), textScaler: textScaler),
      child: Scaffold(
        body: SingleChildScrollView(
          child: Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: _viewportHorizontalPadding,
            ),
            child: child,
          ),
        ),
      ),
    ),
  );
}

double _maxBubbleWidth(double screenWidth, double factor) =>
    (screenWidth - (2 * _viewportHorizontalPadding)) * factor;

void main() {
  testWidgets('short User and Alice messages fit their content', (
    tester,
  ) async {
    const width = 390.0;
    await tester.binding.setSurfaceSize(const Size(width, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      _testApp(
        width: width,
        child: const Column(
          children: [
            UserMessageBubble(content: 'こんにちは'),
            AliceMessageBubble(content: 'こんにちは'),
          ],
        ),
      ),
    );

    final userWidth = tester.getSize(_bubbleContainer(UserMessageBubble)).width;
    final aliceWidth = tester
        .getSize(_bubbleContainer(AliceMessageBubble))
        .width;
    expect(userWidth, greaterThan(28));
    expect(userWidth, lessThan(_maxBubbleWidth(width, .78)));
    expect(aliceWidth, greaterThan(28));
    expect(aliceWidth, lessThan(_maxBubbleWidth(width, .88)));
    expect(
      tester.getRect(_bubbleContainer(UserMessageBubble)).right,
      closeTo(width - _viewportHorizontalPadding, 0.1),
    );
    expect(
      tester.getRect(_bubbleContainer(AliceMessageBubble)).left,
      closeTo(_viewportHorizontalPadding, 0.1),
    );
  });

  testWidgets(
    'long User and Alice messages wrap within their role max widths',
    (tester) async {
      const width = 390.0;
      await tester.binding.setSurfaceSize(const Size(width, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final longText = List.filled(30, '長いメッセージ').join(' ');

      await tester.pumpWidget(
        _testApp(
          width: width,
          child: Column(
            children: [
              UserMessageBubble(content: longText),
              AliceMessageBubble(content: longText),
            ],
          ),
        ),
      );

      final userRect = tester.getRect(_bubbleContainer(UserMessageBubble));
      final aliceRect = tester.getRect(_bubbleContainer(AliceMessageBubble));
      expect(userRect.width, closeTo(_maxBubbleWidth(width, .78), 0.1));
      expect(aliceRect.width, closeTo(_maxBubbleWidth(width, .88), 0.1));
      expect(userRect.height, greaterThan(48));
      expect(aliceRect.height, greaterThan(48));
      expect(tester.takeException(), isNull);
    },
  );

  for (final width in _widths) {
    testWidgets('long unbroken text fits at $width logical pixels', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(Size(width, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final longText = List.filled(400, 'x').join();

      await tester.pumpWidget(
        _testApp(
          width: width,
          child: Column(
            children: [
              UserMessageBubble(content: longText),
              AliceMessageBubble(content: longText),
            ],
          ),
        ),
      );

      expect(
        tester.getSize(_bubbleContainer(UserMessageBubble)).width,
        lessThanOrEqualTo(_maxBubbleWidth(width, .78) + 0.1),
      );
      expect(
        tester.getSize(_bubbleContainer(AliceMessageBubble)).width,
        lessThanOrEqualTo(_maxBubbleWidth(width, .88) + 0.1),
      );
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('explicit User line breaks remain in selectable text', (
    tester,
  ) async {
    await tester.pumpWidget(
      _testApp(
        width: 390,
        child: const Column(
          children: [
            UserMessageBubble(content: 'first line\nsecond line'),
            AliceMessageBubble(content: 'first line  \nsecond line'),
          ],
        ),
      ),
    );

    final selectable = tester.widget<SelectableText>(
      find.descendant(
        of: find.byType(UserMessageBubble),
        matching: find.byType(SelectableText),
      ),
    );
    expect(selectable.data, 'first line\nsecond line');
    final markdown = tester.widget<AliceMarkdownBody>(
      find.byType(AliceMarkdownBody),
    );
    expect(markdown.content, 'first line  \nsecond line');
  });

  testWidgets('Markdown code and table remain within the bubble max width', (
    tester,
  ) async {
    const width = 375.0;
    await tester.binding.setSurfaceSize(const Size(width, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      _testApp(
        width: width,
        child: const AliceMessageBubble(
          content:
              '```text\nconst veryLongVariableName = "a long code value";\n```\n\n'
              '| Column One | Column Two |\n| --- | --- |\n| Value A | Value B |',
        ),
      ),
    );

    expect(
      tester.getSize(_bubbleContainer(AliceMessageBubble)).width,
      lessThanOrEqualTo(_maxBubbleWidth(width, .88) + 0.1),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('expanded text scale does not overflow either bubble', (
    tester,
  ) async {
    const width = 375.0;
    await tester.binding.setSurfaceSize(const Size(width, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      _testApp(
        width: width,
        textScaler: const TextScaler.linear(1.6),
        child: const Column(
          children: [
            UserMessageBubble(content: '拡大文字で表示する少し長めのUserメッセージです'),
            AliceMessageBubble(content: '拡大文字で表示する少し長めのAliceメッセージです'),
          ],
        ),
      ),
    );

    expect(
      tester.getSize(_bubbleContainer(UserMessageBubble)).width,
      lessThanOrEqualTo(_maxBubbleWidth(width, .78) + 0.1),
    );
    expect(
      tester.getSize(_bubbleContainer(AliceMessageBubble)).width,
      lessThanOrEqualTo(_maxBubbleWidth(width, .88) + 0.1),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('Alice streaming bubble grows as its text arrives', (
    tester,
  ) async {
    const width = 390.0;
    await tester.binding.setSurfaceSize(const Size(width, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var text = 'OK';

    await tester.pumpWidget(
      _testApp(
        width: width,
        child: StatefulBuilder(
          builder: (context, setState) => Column(
            children: [
              TextButton(
                onPressed: () => setState(
                  () => text = List.filled(25, 'streaming response').join(' '),
                ),
                child: const Text('append'),
              ),
              StreamingAssistantMessage(text: text),
            ],
          ),
        ),
      ),
    );

    final before = tester
        .getSize(_bubbleContainer(StreamingAssistantMessage))
        .width;
    await tester.tap(find.text('append'));
    await tester.pump();
    final after = tester
        .getSize(_bubbleContainer(StreamingAssistantMessage))
        .width;

    expect(after, greaterThan(before));
    expect(after, lessThanOrEqualTo(_maxBubbleWidth(width, .88) + 0.1));
    expect(tester.takeException(), isNull);
  });
}
