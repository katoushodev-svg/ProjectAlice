class SseFrame {
  const SseFrame({required this.eventName, required this.data});

  final String eventName;
  final String data;

  @override
  bool operator ==(Object other) {
    return other is SseFrame &&
        other.eventName == eventName &&
        other.data == data;
  }

  @override
  int get hashCode => Object.hash(eventName, data);

  @override
  String toString() => 'SseFrame(eventName: $eventName)';
}
