import 'package:flutter/material.dart';

import '../../../domain/message_role.dart';
import '../../model/conversation_message_view_data.dart';
import 'alice_message_bubble.dart';
import 'user_message_bubble.dart';

final class ConversationMessageItem extends StatelessWidget {
  const ConversationMessageItem({super.key, required this.message});

  final ConversationMessageViewData message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      key: ValueKey(message.messageId),
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: switch (message.role) {
        MessageRole.user => UserMessageBubble(content: message.content),
        MessageRole.assistant => AliceMessageBubble(content: message.content),
      },
    );
  }
}
