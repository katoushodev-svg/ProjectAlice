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

final _message = Message(
  id: 'message-1',
  role: MessageRole.assistant,
  content: 'history fixture',
  createdAt: DateTime.utc(2026, 8, 19),
);

final class _Gateway implements ConversationGateway {
  _Gateway({
    this.messages = const [],
    this.delayedConversation,
    List<GatewayResult<Conversation?>>? conversationResults,
    List<GatewayResult<MessagePage>>? messageResults,
  }) : _conversationResults =
           conversationResults ?? [const GatewaySuccess(null)],
       _messageResults = messageResults ?? [GatewaySuccess(_page(messages))];

  final List<Message> messages;
  final Completer<GatewayResult<Conversation?>>? delayedConversation;
  final List<GatewayResult<Conversation?>> _conversationResults;
  final List<GatewayResult<MessagePage>> _messageResults;
  int conversationCalls = 0;

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
  }) => Future.value(_messageResults.removeAt(0));

  @override
  Stream<ConversationSendEvent> sendMessage(OutgoingMessage outgoingMessage) =>
      const Stream.empty();
}

MessagePage _page(List<Message> messages) =>
    MessagePage(messages: messages, nextCursor: null, hasMore: false);
