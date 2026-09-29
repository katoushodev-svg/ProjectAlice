import 'package:alice/app/app_configuration.dart';
import 'package:alice/app/app_providers.dart';
import 'package:alice/conversation/application/model/conversation_send_event.dart';
import 'package:alice/conversation/application/model/message_page.dart';
import 'package:alice/conversation/application/port/conversation_gateway.dart';
import 'package:alice/conversation/application/state/conversation_screen_state.dart';
import 'package:alice/conversation/domain/conversation.dart';
import 'package:alice/conversation/domain/outgoing_message.dart';
import 'package:alice/conversation/infrastructure/http/conversation_http_gateway.dart';
import 'package:alice/conversation/presentation/provider/conversation_providers.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('production gateway provider creates the concrete HTTP adapter', () {
    final configuration = AppConfiguration.fromRaw(
      rawBaseUrl: 'http://127.0.0.1:8080',
      allowLocalHttp: true,
    );
    final container = ProviderContainer(
      overrides: [appConfigurationProvider.overrideWithValue(configuration)],
    );
    addTearDown(container.dispose);

    final gateway = container.read(conversationGatewayProvider);

    expect(gateway, isA<ConversationHttpGateway>());
  });

  test('controller provider can be overridden through the gateway port', () {
    final configuration = AppConfiguration.fromRaw(
      rawBaseUrl: 'http://127.0.0.1:8080',
      allowLocalHttp: true,
    );
    final fake = _FakeGateway();
    final container = ProviderContainer(
      overrides: [
        appConfigurationProvider.overrideWithValue(configuration),
        conversationGatewayProvider.overrideWithValue(fake),
      ],
    );
    addTearDown(container.dispose);

    final controller = container.read(conversationControllerProvider);

    expect(controller.state.status, ConversationScreenStatus.initialLoading);
  });
}

final class _FakeGateway implements ConversationGateway {
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
  Stream<ConversationSendEvent> sendMessage(OutgoingMessage outgoingMessage) =>
      const Stream.empty();
}
