import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../application/port/conversation_gateway.dart';
import '../controller/conversation_controller.dart';

final conversationGatewayProvider = Provider<ConversationGateway>((ref) {
  throw UnimplementedError(
    'ConversationGateway must be overridden until FIP-009 wiring.',
  );
});

final conversationControllerProvider = Provider<ConversationController>((ref) {
  final controller = ConversationController(
    gateway: ref.watch(conversationGatewayProvider),
  );
  ref.onDispose(controller.dispose);
  return controller;
});
