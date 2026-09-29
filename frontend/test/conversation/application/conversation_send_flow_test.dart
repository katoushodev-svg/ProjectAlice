import 'dart:async';

import 'package:alice/conversation/application/model/conversation_send_event.dart';
import 'package:alice/conversation/application/model/message_page.dart';
import 'package:alice/conversation/application/port/conversation_gateway.dart';
import 'package:alice/conversation/application/port/idempotency_key_generator.dart';
import 'package:alice/conversation/application/state/conversation_screen_state.dart';
import 'package:alice/conversation/domain/conversation.dart';
import 'package:alice/conversation/domain/message.dart';
import 'package:alice/conversation/domain/message_role.dart';
import 'package:alice/conversation/domain/outgoing_message.dart';
import 'package:alice/conversation/presentation/controller/conversation_controller.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('validates Unicode code points and preserves content', () {
    expect(
      ConversationController.validateContent('  こんにちは\n世界  '),
      ConversationSendValidation.valid,
    );
    expect(
      ConversationController.validateContent('😀' * 10000),
      ConversationSendValidation.valid,
    );
    expect(
      ConversationController.validateContent('😀' * 10001),
      ConversationSendValidation.tooLong,
    );
    expect(
      ConversationController.validateContent('   \n\t'),
      ConversationSendValidation.whitespaceOnly,
    );
  });

  test(
    'generates one UUID per logical send and keeps it through streaming',
    () async {
      final stream = StreamController<ConversationSendEvent>();
      final generator = _FakeKeyGenerator();
      final gateway = _FakeGateway(sendStream: stream.stream);
      final controller = ConversationController(
        gateway: gateway,
        idempotencyKeyGenerator: generator,
      );
      addTearDown(() async {
        await stream.close();
        controller.dispose();
      });

      await controller.loadInitial();

      expect(controller.send('  hello\nworld  '), isTrue);
      expect(generator.calls, 1);
      expect(gateway.sent.single.content, '  hello\nworld  ');
      final firstKey = gateway.sent.single.idempotencyKey;

      final canonicalUserMessage = Message(
        id: 'user-1',
        role: MessageRole.user,
        content: '  hello\nworld  ',
        createdAt: DateTime.utc(2026, 9, 27),
      );
      stream.add(StreamStarted('request-1', userMessage: canonicalUserMessage));
      await Future<void>.delayed(Duration.zero);
      expect(controller.state.status, ConversationScreenStatus.streaming);

      stream.add(const AssistantDelta(requestId: 'request-1', delta: 'first'));
      await Future<void>.delayed(Duration.zero);
      expect(controller.state.temporaryAssistantText, 'first');

      stream.add(
        const AssistantDelta(requestId: 'request-1', delta: ' second'),
      );
      await Future<void>.delayed(const Duration(milliseconds: 60));
      expect(controller.state.temporaryAssistantText, 'first second');

      stream.add(
        AssistantCompleted(
          requestId: 'request-1',
          messages: [
            canonicalUserMessage,
            Message(
              id: 'assistant-1',
              role: MessageRole.assistant,
              content: 'canonical',
              createdAt: DateTime.utc(2026, 9, 27, 0, 0, 1),
            ),
          ],
        ),
      );
      await Future<void>.delayed(Duration.zero);

      expect(controller.state.status, ConversationScreenStatus.ready);
      expect(controller.state.temporaryAssistantText, isNull);
      expect(controller.state.pendingSend, isNull);
      expect(controller.state.messages.last.content, 'canonical');
      expect(generator.calls, 1);
      expect(gateway.sent.single.idempotencyKey, firstKey);
    },
  );

  test('does not create a second send while sending', () async {
    final stream = StreamController<ConversationSendEvent>();
    final generator = _FakeKeyGenerator();
    final gateway = _FakeGateway(sendStream: stream.stream);
    final controller = ConversationController(
      gateway: gateway,
      idempotencyKeyGenerator: generator,
    );
    addTearDown(() async {
      await stream.close();
      controller.dispose();
    });

    await controller.loadInitial();

    expect(controller.send('first'), isTrue);
    expect(controller.send('second'), isFalse);
    expect(generator.calls, 1);
    expect(gateway.sent, hasLength(1));
  });
}

final class _FakeKeyGenerator implements IdempotencyKeyGenerator {
  int calls = 0;

  @override
  String generate() {
    calls++;
    return '123e4567-e89b-42d3-a456-426614174000';
  }
}

final class _FakeGateway implements ConversationGateway {
  _FakeGateway({required this.sendStream});

  final Stream<ConversationSendEvent> sendStream;
  final sent = <OutgoingMessage>[];

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
    sent.add(outgoingMessage);
    return sendStream;
  }
}
