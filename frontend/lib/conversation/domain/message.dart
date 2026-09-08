import 'message_role.dart';

final class Message {
  const Message({
    required this.id,
    required this.role,
    required this.content,
    required this.createdAt,
  });

  final String id;
  final MessageRole role;
  final String content;
  final DateTime createdAt;

  @override
  bool operator ==(Object other) {
    return other is Message &&
        other.id == id &&
        other.role == role &&
        other.content == content &&
        other.createdAt == createdAt;
  }

  @override
  int get hashCode => Object.hash(id, role, content, createdAt);

  @override
  String toString() {
    return 'Message(id: $id, role: $role, createdAt: $createdAt)';
  }
}
