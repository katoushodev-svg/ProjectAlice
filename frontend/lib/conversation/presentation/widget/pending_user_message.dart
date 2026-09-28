import 'package:flutter/material.dart';

import '../../domain/outgoing_message.dart';
import 'history/user_message_bubble.dart';

final class PendingUserMessage extends StatelessWidget {
  const PendingUserMessage({super.key, required this.message});

  final OutgoingMessage message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      key: ValueKey('pending-user-${message.idempotencyKey}'),
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: UserMessageBubble(content: message.content),
    );
  }
}
