import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/app_providers.dart';
import '../../application/port/conversation_gateway.dart';
import '../../application/port/idempotency_key_generator.dart';
import '../../infrastructure/http/conversation_http_gateway.dart';
import '../controller/conversation_controller.dart';

final conversationHttpGatewayProvider = Provider<ConversationGateway>((ref) {
  return ConversationHttpGateway(
    configuration: ref.watch(appConfigurationProvider),
    client: ref.watch(httpClientProvider),
  );
});

final conversationGatewayProvider = Provider<ConversationGateway>((ref) {
  return ref.watch(conversationHttpGatewayProvider);
});

final idempotencyKeyGeneratorProvider = Provider<IdempotencyKeyGenerator>(
  (ref) => UuidV4IdempotencyKeyGenerator(),
);

final conversationControllerProvider = Provider<ConversationController>((ref) {
  final controller = ConversationController(
    gateway: ref.watch(conversationGatewayProvider),
    idempotencyKeyGenerator: ref.watch(idempotencyKeyGeneratorProvider),
  );
  ref.onDispose(controller.dispose);
  return controller;
});
