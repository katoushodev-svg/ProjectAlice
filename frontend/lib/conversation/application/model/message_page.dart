import 'dart:collection';

import '../../domain/message.dart';

final class MessagePage {
  MessagePage({
    required List<Message> messages,
    required this.nextCursor,
    required this.hasMore,
  }) : messages = UnmodifiableListView<Message>(List<Message>.of(messages)) {
    if (hasMore && (nextCursor == null || nextCursor!.isEmpty)) {
      throw ArgumentError('A page with more messages needs a cursor.');
    }
    if (!hasMore && nextCursor != null) {
      throw ArgumentError('A page without more messages cannot have a cursor.');
    }
  }

  final List<Message> messages;
  final String? nextCursor;
  final bool hasMore;
}
