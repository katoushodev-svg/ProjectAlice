final class Conversation {
  const Conversation({
    required this.id,
    required this.createdAt,
    required this.updatedAt,
  });

  final String id;
  final DateTime createdAt;
  final DateTime updatedAt;

  @override
  bool operator ==(Object other) {
    return other is Conversation &&
        other.id == id &&
        other.createdAt == createdAt &&
        other.updatedAt == updatedAt;
  }

  @override
  int get hashCode => Object.hash(id, createdAt, updatedAt);

  @override
  String toString() {
    return 'Conversation(id: $id, createdAt: $createdAt, updatedAt: $updatedAt)';
  }
}
