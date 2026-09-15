import 'package:flutter/widgets.dart';

import '../layout/conversation_layout_metrics.dart';

/// The single, primary vertical Scroll region of the Conversation Screen.
///
/// Displays externally supplied Fixture/Message Widgets in the order given
/// (oldest to newest). Does not paginate, auto-scroll, or own the
/// [ScrollController]; the Parent owns it.
class MessageViewport extends StatelessWidget {
  const MessageViewport({
    super.key,
    required this.controller,
    required this.items,
  });

  final ScrollController controller;
  final List<Widget> items;

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      controller: controller,
      padding: const EdgeInsets.symmetric(
        horizontal: ConversationLayoutMetrics.messageViewportHorizontalPadding,
      ),
      itemCount: items.length,
      itemBuilder: (context, index) => items[index],
    );
  }
}
