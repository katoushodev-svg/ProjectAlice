import 'dart:async';

import 'package:alice/app/theme/alice_theme.dart';
import 'package:alice/conversation/application/model/conversation_send_event.dart';
import 'package:alice/conversation/application/model/message_page.dart';
import 'package:alice/conversation/application/port/conversation_gateway.dart';
import 'package:alice/conversation/application/state/conversation_screen_state.dart';
import 'package:alice/conversation/domain/conversation.dart';
import 'package:alice/conversation/domain/message.dart';
import 'package:alice/conversation/domain/message_role.dart';
import 'package:alice/conversation/domain/outgoing_message.dart';
import 'package:alice/conversation/presentation/provider/conversation_providers.dart';
import 'package:alice/conversation/presentation/screen/conversation_screen.dart';
import 'package:alice/conversation/presentation/widget/composer_panel.dart';
import 'package:alice/conversation/presentation/widget/pending_user_message.dart';
import 'package:alice/conversation/presentation/widget/streaming_assistant_message.dart';
import 'package:alice/conversation/presentation/widget/thinking_indicator.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows pending user and thinking state after Send', (
    tester,
  ) async {
    final gateway = _SendGateway();
    addTearDown(gateway.dispose);

    await tester.pumpWidget(_build(gateway));
    await tester.pump();

    await tester.enterText(find.byType(TextField), '  hello\nworld  ');
    await tester.pump();

    expect(_composer(tester).sendEnabled, isTrue);

    await tester.tap(find.bySemanticsLabel('送信'));
    await tester.pump();

    expect(find.byType(PendingUserMessage), findsOneWidget);
    expect(find.byType(ThinkingIndicator), findsOneWidget);
    expect(find.text('  hello\nworld  '), findsOneWidget);
    expect(_composer(tester).enabled, isFalse);
  });

  testWidgets('first delta replaces thinking and later deltas are batched', (
    tester,
  ) async {
    final gateway = _SendGateway();
    addTearDown(gateway.dispose);

    await tester.pumpWidget(_build(gateway));
    await tester.pump();
    await tester.pump();

    expect(_composer(tester).enabled, isTrue);

    await tester.enterText(find.byType(TextField), 'hello');
    await tester.pump();
    await tester.tap(find.bySemanticsLabel('送信'));
    await tester.pump();

    final controller = ProviderScope.containerOf(
      tester.element(find.byType(ConversationScreen)),
    ).read(conversationControllerProvider);
    expect(controller.state.status, ConversationScreenStatus.sending);
    expect(gateway.sendCount, 1);

    gateway.events.add(
      StreamStarted('request-1', userMessage: _canonicalUserMessage()),
    );
    await tester.pump();

    gateway.events.add(
      const AssistantDelta(requestId: 'request-1', delta: 'one'),
    );
    await tester.pump();

    expect(find.byType(ThinkingIndicator), findsNothing);
    expect(find.byType(StreamingAssistantMessage), findsOneWidget);
    expect(find.text('one'), findsOneWidget);

    gateway.events.add(
      const AssistantDelta(requestId: 'request-1', delta: ' two'),
    );
    await tester.pump();

    expect(find.text('one'), findsOneWidget);
    expect(find.text('one two'), findsNothing);

    await tester.pump(const Duration(milliseconds: 55));

    expect(find.text('one two'), findsOneWidget);
  });

  testWidgets('canonical completion replaces temporary assistant text', (
    tester,
  ) async {
    final gateway = _SendGateway();
    addTearDown(gateway.dispose);

    await tester.pumpWidget(_build(gateway));
    await tester.pump();
    await tester.pump();

    expect(_composer(tester).enabled, isTrue);

    await tester.enterText(find.byType(TextField), 'hello');
    await tester.pump();
    await tester.tap(find.bySemanticsLabel('送信'));
    await tester.pump();

    final controller = ProviderScope.containerOf(
      tester.element(find.byType(ConversationScreen)),
    ).read(conversationControllerProvider);
    expect(controller.state.status, ConversationScreenStatus.sending);
    expect(gateway.sendCount, 1);

    gateway.events.add(
      StreamStarted('request-1', userMessage: _canonicalUserMessage()),
    );
    gateway.events.add(
      const AssistantDelta(requestId: 'request-1', delta: 'temporary'),
    );
    await tester.pump();

    gateway.events.add(
      AssistantCompleted(
        requestId: 'request-1',
        messages: [
          Message(
            id: 'user-1',
            role: MessageRole.user,
            content: 'hello',
            createdAt: DateTime.utc(2026, 9, 27),
          ),
          Message(
            id: 'assistant-1',
            role: MessageRole.assistant,
            content: 'canonical',
            createdAt: DateTime.utc(2026, 9, 27, 0, 0, 1),
          ),
        ],
      ),
    );
    await tester.pump();

    expect(find.byType(StreamingAssistantMessage), findsNothing);
    expect(find.text('canonical'), findsOneWidget);
    expect(find.text('temporary'), findsNothing);
    expect(_composer(tester).enabled, isTrue);
  });

  testWidgets('shows the counter from 9000 code points', (tester) async {
    final gateway = _SendGateway();
    addTearDown(gateway.dispose);

    await tester.pumpWidget(_build(gateway));
    await tester.pump();

    await tester.enterText(find.byType(TextField), 'a' * 9000);
    await tester.pump();

    expect(find.text('9000 / 10,000'), findsOneWidget);
  });
}

Widget _build(_SendGateway gateway) {
  return ProviderScope(
    overrides: [conversationGatewayProvider.overrideWithValue(gateway)],
    child: MaterialApp(
      theme: AliceTheme.darkTheme,
      home: const ConversationScreen(),
    ),
  );
}

ComposerPanel _composer(WidgetTester tester) =>
    tester.widget(find.byType(ComposerPanel));

final class _SendGateway implements ConversationGateway {
  _SendGateway() : events = StreamController<ConversationSendEvent>();

  final StreamController<ConversationSendEvent> events;
  int sendCount = 0;

  @override
  Future<GatewayResult<Conversation?>> getConversation() =>
      Future.value(const GatewaySuccess(null));

  @override
  Future<GatewayResult<MessagePage>> getMessages({
    int limit = 50,
    String? cursor,
  }) => Future.value(
    GatewaySuccess(
      MessagePage(messages: const [], nextCursor: null, hasMore: false),
    ),
  );

  @override
  Stream<ConversationSendEvent> sendMessage(OutgoingMessage outgoingMessage) {
    sendCount++;
    return events.stream;
  }

  void dispose() {
    events.close();
  }
}

Message _canonicalUserMessage() => Message(
  id: 'user-1',
  role: MessageRole.user,
  content: 'hello',
  createdAt: DateTime.utc(2026, 9, 27),
);
