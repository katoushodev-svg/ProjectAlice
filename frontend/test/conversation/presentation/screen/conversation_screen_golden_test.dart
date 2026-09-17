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

  group('ConversationScreen FIP-008 golden tests', () {
    testWidgets('golden - initial loading 390', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        buildTestable(_GoldenGateway(delayed: Completer())),
      );
      await tester.pump(const Duration(milliseconds: 300));
      await expectLater(
        find.byType(ConversationScreen),
        matchesGoldenFile('goldens/initial_history_loading_390.png'),
      );
    });

    testWidgets('golden - empty 390', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(buildTestable(_GoldenGateway()));
      await tester.pump();
      await expectLater(
        find.byType(ConversationScreen),
        matchesGoldenFile('goldens/initial_history_empty_390.png'),
      );
    });

    testWidgets('golden - messages 390', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        buildTestable(_GoldenGateway(messages: [_fixtureMessage])),
      );
      await tester.pump();
      await expectLater(
        find.byType(ConversationScreen),
        matchesGoldenFile('goldens/initial_history_messages_390.png'),
      );
    });

    testWidgets('golden - failure 390', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        buildTestable(
          _GoldenGateway(
            conversation: GatewayFailure(
              ConversationFailure(
                category: ConversationFailureCategory.networkUnavailable,
                operation: ConversationOperation.initialLoad,
              ),
            ),
          ),
        ),
      );
      await tester.pump();
      await expectLater(
        find.byType(ConversationScreen),
        matchesGoldenFile('goldens/initial_history_failure_390.png'),
      );
    });
  });
}

final _fixtureMessage = Message(
  id: 'golden-message',
  role: MessageRole.assistant,
  content: '履歴の表示です。',
  createdAt: DateTime.utc(2026, 8, 19),
);

final class _GoldenGateway implements ConversationGateway {
  _GoldenGateway({
    this.conversation = const GatewaySuccess(null),
    this.messages = const [],
    this.delayed,
  });

  final GatewayResult<Conversation?> conversation;
  final List<Message> messages;
  final Completer<GatewayResult<Conversation?>>? delayed;

  @override
  Future<GatewayResult<Conversation?>> getConversation() =>
      delayed?.future ?? Future.value(conversation);

  @override
  Future<GatewayResult<MessagePage>> getMessages({
    int limit = 50,
    String? cursor,
  }) => Future.value(
    GatewaySuccess(
      MessagePage(messages: messages, nextCursor: null, hasMore: false),
    ),
  );

  @override
  Stream<ConversationSendEvent> sendMessage(OutgoingMessage outgoingMessage) =>
      const Stream.empty();
}
