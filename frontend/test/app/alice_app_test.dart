import 'package:alice/conversation/application/model/message_page.dart';
import 'package:alice/conversation/application/port/conversation_gateway.dart';
import 'package:alice/conversation/application/model/conversation_send_event.dart';
import 'package:alice/conversation/domain/conversation.dart';
import 'package:alice/conversation/domain/outgoing_message.dart';
import 'package:alice/conversation/presentation/provider/conversation_providers.dart';
import 'package:alice/conversation/presentation/screen/conversation_screen.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:alice/app/alice_app.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

void main() {
  testWidgets('AliceApp starts at ConversationScreen', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          conversationGatewayProvider.overrideWithValue(_FakeGateway()),
        ],
        child: const AliceApp(),
      ),
    );

    expect(find.byType(ConversationScreen), findsOneWidget);
  });
}

final class _FakeGateway implements ConversationGateway {
  @override
  Future<GatewayResult<Conversation?>> getConversation() async =>
      const GatewaySuccess(null);

  @override
  Future<GatewayResult<MessagePage>> getMessages({
    int limit = 50,
    String? cursor,
  }) async => GatewaySuccess(
    MessagePage(messages: const [], nextCursor: null, hasMore: false),
  );

  @override
  Stream<ConversationSendEvent> sendMessage(OutgoingMessage outgoingMessage) =>
      const Stream.empty();
}
