import 'dart:async';

import 'package:alice/app/theme/alice_theme.dart';
import 'package:alice/conversation/application/error/conversation_failure.dart';
import 'package:alice/conversation/application/model/conversation_send_event.dart';
import 'package:alice/conversation/application/model/message_page.dart';
import 'package:alice/conversation/application/port/conversation_gateway.dart';
import 'package:alice/conversation/domain/conversation.dart';
import 'package:alice/conversation/domain/message.dart';
import 'package:alice/conversation/domain/message_role.dart';
import 'package:alice/conversation/domain/outgoing_message.dart';
import 'package:alice/conversation/presentation/provider/conversation_providers.dart';
import 'package:alice/conversation/presentation/screen/conversation_screen.dart';
import 'package:alice/conversation/presentation/widget/composer_panel.dart';
import 'package:alice/conversation/presentation/widget/thinking_indicator.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Widget buildTestable(ConversationGateway gateway) => ProviderScope(
    overrides: [conversationGatewayProvider.overrideWithValue(gateway)],
    child: MaterialApp(
      theme: AliceTheme.darkTheme,
      home: const ConversationScreen(),
    ),
  );

  testWidgets('keeps the viewport blank before the 300ms loading delay', (
    tester,
  ) async {
    final gateway = _Gateway(delayedConversation: Completer());
    await tester.pumpWidget(buildTestable(gateway));
    await tester.pump(const Duration(milliseconds: 299));

    expect(find.text('会話を読み込んでいます'), findsNothing);
    expect(_composer(tester).enabled, isFalse);
  });

  testWidgets('shows loading indicator after 300ms', (tester) async {
    final gateway = _Gateway(delayedConversation: Completer());
    await tester.pumpWidget(buildTestable(gateway));
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('会話を読み込んでいます'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('shows empty state only after a successful empty history', (
    tester,
  ) async {
    await tester.pumpWidget(buildTestable(_Gateway()));
    await tester.pump();

    expect(find.text('何から始めましょうか？'), findsOneWidget);
    expect(_composer(tester).enabled, isTrue);
  });

  testWidgets('does not show loading after an immediate successful load', (
    tester,
  ) async {
    await tester.pumpWidget(buildTestable(_Gateway()));
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('会話を読み込んでいます'), findsNothing);
    expect(find.text('何から始めましょうか？'), findsOneWidget);
  });

  testWidgets('shows history instead of empty state', (tester) async {
    await tester.pumpWidget(buildTestable(_Gateway(messages: [_message])));
    await tester.pump();

    expect(find.text('history fixture'), findsOneWidget);
    expect(find.text('何から始めましょうか？'), findsNothing);
    expect(_composer(tester).enabled, isTrue);
  });

  testWidgets('shows manual older loading only when history cannot scroll', (
    tester,
  ) async {
    final shortHistory = _Gateway(
      messageResults: [
        GatewaySuccess(
          _page([_message], nextCursor: 'older-cursor', hasMore: true),
        ),
      ],
    );
    await tester.pumpWidget(buildTestable(shortHistory));
    await tester.pump();
    await tester.pump();

    expect(find.text('以前のメッセージを読み込む'), findsOneWidget);
  });

  testWidgets('hides manual older loading for scrollable history', (
    tester,
  ) async {
    final longHistory = List.generate(
      40,
      (index) => Message(
        id: 'scrollable-$index',
        role: MessageRole.assistant,
        content: 'A long history item $index with enough text to fill the row.',
        createdAt: DateTime.utc(2026, 8, 19).add(Duration(minutes: index)),
      ),
    );
    final gateway = _Gateway(
      messageResults: [
        GatewaySuccess(
          _page(longHistory, nextCursor: 'older-cursor', hasMore: true),
        ),
      ],
    );
    await tester.pumpWidget(buildTestable(gateway));
    await tester.pump();
    await tester.pump();

    expect(find.text('以前のメッセージを読み込む'), findsNothing);
  });

  testWidgets('shows the conversation beginning when no older page remains', (
    tester,
  ) async {
    await tester.pumpWidget(buildTestable(_Gateway(messages: [_message])));
    await tester.pump();

    expect(find.text('会話の始まり'), findsOneWidget);
  });

  testWidgets(
    'keeps the message ID anchor through two older pages including the beginning row',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final firstOlderPage = Completer<GatewayResult<MessagePage>>();
      final secondOlderPage = Completer<GatewayResult<MessagePage>>();
      final gateway = _Gateway(
        messageResults: [
          GatewaySuccess(
            _page(_history(101, 150), nextCursor: 'page-1', hasMore: true),
          ),
          GatewaySuccess(
            _page(_history(51, 100), nextCursor: 'page-2', hasMore: true),
          ),
          GatewaySuccess(_page(_history(1, 50))),
        ],
        delayedOlderPages: [firstOlderPage, secondOlderPage],
      );
      await tester.pumpWidget(buildTestable(gateway));
      await tester.pump();
      await tester.pump();

      await tester.drag(find.byType(ListView), const Offset(0, 20000));
      await tester.pump();
      firstOlderPage.complete(gateway.takeNextMessageResult());
      await tester.pump();
      await tester.pump();
      expect(gateway.messageCalls, 2);

      await tester.drag(find.byType(ListView), const Offset(0, 20000));
      await tester.pump();
      secondOlderPage.complete(gateway.takeNextMessageResult());
      await tester.pump();
      await tester.pump();
      expect(gateway.messageCalls, 3);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('announces pending and thinking states with accessible labels', (
    tester,
  ) async {
    final gateway = _Gateway(
      sendResults: [
        Stream<ConversationSendEvent>.fromFuture(
          Completer<ConversationSendEvent>().future,
        ),
      ],
    );
    await tester.pumpWidget(buildTestable(gateway));
    await _waitForComposer(tester);
    await tester.enterText(find.byType(TextField), 'hello');
    await tester.pump();
    expect(_composer(tester).sendEnabled, isTrue);
    await tester.tap(find.bySemanticsLabel('送信'));
    await tester.pump();

    expect(gateway.outgoing, hasLength(1));
    expect(find.bySemanticsLabel('あなた、送信中: hello'), findsOneWidget);
    expect(find.byType(ThinkingIndicator), findsOneWidget);
    final thinkingSemantics = tester.widget<Semantics>(
      find.descendant(
        of: find.byType(ThinkingIndicator),
        matching: find.byType(Semantics),
      ),
    );
    expect(thinkingSemantics.properties.label, 'Aliceが回答を作成中です');
  });

  testWidgets('Result Unknown offers a same-key user initiated result check', (
    tester,
  ) async {
    final gateway = _Gateway(
      sendResults: [
        Stream.value(
          SendFailed(
            requestId: null,
            failure: ConversationFailure(
              category: ConversationFailureCategory.resultUnknown,
              operation: ConversationOperation.send,
              resultCertainty: SendResultCertainty.resultUnknown,
            ),
          ),
        ),
        const Stream<ConversationSendEvent>.empty(),
      ],
    );
    await tester.pumpWidget(buildTestable(gateway));
    await _waitForComposer(tester);
    await tester.enterText(find.byType(TextField), 'hello');
    await tester.pump();
    expect(_composer(tester).sendEnabled, isTrue);
    await tester.tap(find.bySemanticsLabel('送信'));
    await tester.pump();
    await tester.pump();

    expect(gateway.outgoing, hasLength(1));
    expect(find.text('送信結果を確認できませんでした。'), findsOneWidget);
    expect(find.text('結果を確認'), findsOneWidget);
    final originalKey = gateway.outgoing.single.idempotencyKey;
    await tester.tap(find.text('結果を確認'));
    await tester.pump();

    expect(gateway.outgoing, hasLength(2));
    expect(gateway.outgoing.last.idempotencyKey, originalKey);
    expect(gateway.outgoing.last.content, 'hello');
  });

  testWidgets('recovery actions remain available at enlarged text scale', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final gateway = _Gateway(
      sendResults: [
        Stream.value(
          SendFailed(
            requestId: null,
            failure: ConversationFailure(
              category: ConversationFailureCategory.conversationBusy,
              operation: ConversationOperation.send,
              resultCertainty: SendResultCertainty.knownFailed,
            ),
          ),
        ),
        const Stream<ConversationSendEvent>.empty(),
      ],
    );
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(textScaler: TextScaler.linear(1.6)),
        child: buildTestable(gateway),
      ),
    );
    await _waitForComposer(tester);
    await tester.enterText(find.byType(TextField), 'hello');
    await tester.pump();
    expect(_composer(tester).sendEnabled, isTrue);
    await tester.tap(find.bySemanticsLabel('送信'));
    await tester.pump();
    await tester.pump();

    expect(gateway.outgoing, hasLength(1));
    expect(find.text('もう一度試す'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'latest action appears for unseen updates while reading history',
    (tester) async {
      final stream = StreamController<ConversationSendEvent>();
      addTearDown(stream.close);
      final gateway = _Gateway(
        messages: List.generate(
          40,
          (index) => Message(
            id: 'history-$index',
            role: MessageRole.assistant,
            content: 'history item $index',
            createdAt: DateTime.utc(2026, 8, 19).add(Duration(minutes: index)),
          ),
        ),
        sendResults: [stream.stream],
      );
      await tester.pumpWidget(buildTestable(gateway));
      await _waitForComposer(tester);
      await tester.pump();
      await tester.drag(find.byType(ListView), const Offset(0, 350));
      await tester.pump();
      expect(find.text('最新へ'), findsNothing);

      await tester.enterText(find.byType(TextField), 'hello');
      await tester.pump();
      await tester.tap(find.bySemanticsLabel('送信'));
      await tester.pump();
      await tester.drag(find.byType(ListView), const Offset(0, 350));
      await tester.pump();
      stream.add(
        StreamStarted(
          'request-1',
          userMessage: Message(
            id: 'canonical-user',
            role: MessageRole.user,
            content: 'hello',
            createdAt: DateTime.utc(2026, 9, 28),
          ),
        ),
      );
      await tester.pump();
      stream.add(const AssistantDelta(requestId: 'request-1', delta: 'answer'));
      await tester.pump();

      expect(find.bySemanticsLabel('最新のメッセージへ移動'), findsOneWidget);
      expect(find.text('最新へ'), findsOneWidget);
      await tester.tap(find.bySemanticsLabel('最新のメッセージへ移動'));
      await tester.pump(const Duration(milliseconds: 250));
      expect(find.bySemanticsLabel('最新のメッセージへ移動'), findsNothing);
      expect(find.text('最新へ'), findsNothing);
    },
  );

  testWidgets('shows error and retries the full load flow', (tester) async {
    final gateway = _Gateway(
      conversationResults: [
        GatewayFailure(
          ConversationFailure(
            category: ConversationFailureCategory.networkUnavailable,
            operation: ConversationOperation.initialLoad,
          ),
        ),
        const GatewaySuccess(null),
      ],
    );
    await tester.pumpWidget(buildTestable(gateway));
    await tester.pump();
    expect(find.text('会話を読み込めませんでした'), findsOneWidget);
    expect(_composer(tester).enabled, isFalse);

    await tester.tap(find.text('再読み込み'));
    await tester.pump();
    expect(find.text('何から始めましょうか？'), findsOneWidget);
    expect(gateway.conversationCalls, 2);
  });

  testWidgets('retries a message failure and shows the successful history', (
    tester,
  ) async {
    final gateway = _Gateway(
      conversationResults: [
        const GatewaySuccess(null),
        const GatewaySuccess(null),
      ],
      messageResults: [
        GatewayFailure(
          ConversationFailure(
            category: ConversationFailureCategory.networkUnavailable,
            operation: ConversationOperation.initialLoad,
          ),
        ),
        GatewaySuccess(_page([_message])),
      ],
    );
    await tester.pumpWidget(buildTestable(gateway));
    await tester.pump();
    expect(find.text('会話を読み込めませんでした'), findsOneWidget);

    await tester.tap(find.text('再読み込み'));
    await tester.pump();
    expect(find.text('history fixture'), findsOneWidget);
    expect(find.text('会話を読み込めませんでした'), findsNothing);
  });

  testWidgets('shows a date separator for each JST calendar date', (
    tester,
  ) async {
    final messages = [
      _message,
      Message(
        id: 'message-2',
        role: MessageRole.user,
        content: 'next day fixture',
        createdAt: DateTime.utc(2026, 8, 19, 15),
      ),
    ];
    await tester.pumpWidget(buildTestable(_Gateway(messages: messages)));
    await tester.pump();

    expect(find.text('2026年8月19日'), findsOneWidget);
    expect(find.text('2026年8月20日'), findsOneWidget);
  });
}

ComposerPanel _composer(WidgetTester tester) =>
    tester.widget(find.byType(ComposerPanel));

Future<void> _waitForComposer(WidgetTester tester) async {
  for (var attempt = 0; attempt < 10 && !_composer(tester).enabled; attempt++) {
    await tester.pump(const Duration(milliseconds: 1));
  }
}

final _message = Message(
  id: 'message-1',
  role: MessageRole.assistant,
  content: 'history fixture',
  createdAt: DateTime.utc(2026, 8, 19),
);

List<Message> _history(int first, int last) => [
  for (var sequence = first; sequence <= last; sequence++)
    Message(
      id: 'history-$sequence',
      role: sequence.isOdd ? MessageRole.user : MessageRole.assistant,
      content:
          'history ${sequence.toString().padLeft(3, '0')} with enough text '
          'to make the history viewport scroll across multiple screens.',
      createdAt: DateTime.utc(2026, 8, 19).add(Duration(minutes: sequence)),
    ),
];

final class _Gateway implements ConversationGateway {
  _Gateway({
    this.messages = const [],
    this.delayedConversation,
    List<GatewayResult<Conversation?>>? conversationResults,
    List<GatewayResult<MessagePage>>? messageResults,
    List<Completer<GatewayResult<MessagePage>>>? delayedOlderPages,
    List<Stream<ConversationSendEvent>>? sendResults,
  }) : _conversationResults =
           conversationResults ?? [const GatewaySuccess(null)],
       _messageResults = messageResults ?? [GatewaySuccess(_page(messages))],
       _delayedOlderPages = delayedOlderPages ?? const [],
       _sendResults =
           sendResults ?? [const Stream<ConversationSendEvent>.empty()];

  final List<Message> messages;
  final Completer<GatewayResult<Conversation?>>? delayedConversation;
  final List<GatewayResult<Conversation?>> _conversationResults;
  final List<GatewayResult<MessagePage>> _messageResults;
  final List<Completer<GatewayResult<MessagePage>>> _delayedOlderPages;
  final List<Stream<ConversationSendEvent>> _sendResults;
  final outgoing = <OutgoingMessage>[];
  int conversationCalls = 0;
  int messageCalls = 0;

  @override
  Future<GatewayResult<Conversation?>> getConversation() {
    conversationCalls++;
    if (delayedConversation != null) return delayedConversation!.future;
    return Future.value(_conversationResults.removeAt(0));
  }

  @override
  Future<GatewayResult<MessagePage>> getMessages({
    int limit = 50,
    String? cursor,
  }) {
    messageCalls++;
    if (messageCalls > 1 && _delayedOlderPages.isNotEmpty) {
      return _delayedOlderPages.removeAt(0).future;
    }
    return Future.value(_messageResults.removeAt(0));
  }

  GatewayResult<MessagePage> takeNextMessageResult() =>
      _messageResults.removeAt(0);

  @override
  Stream<ConversationSendEvent> sendMessage(OutgoingMessage outgoingMessage) {
    outgoing.add(outgoingMessage);
    return _sendResults.removeAt(0);
  }
}

MessagePage _page(
  List<Message> messages, {
  String? nextCursor,
  bool hasMore = false,
}) => MessagePage(messages: messages, nextCursor: nextCursor, hasMore: hasMore);
