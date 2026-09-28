import 'package:flutter/material.dart';

import 'history/alice_message_bubble.dart';

final class StreamingAssistantMessage extends StatelessWidget {
  const StreamingAssistantMessage({super.key, required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      key: const ValueKey('streaming-assistant'),
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: AliceMessageBubble(content: text),
    );
  }
}
