class ApiContractException implements Exception {
  const ApiContractException({
    required this.category,
    this.field,
    this.message,
  });

  final String category;
  final String? field;
  final String? message;

  @override
  String toString() {
    final fieldText = field == null ? '' : ' field';
    final messageText = message == null ? '' : ' message';
    return 'ApiContractException(category: $category$fieldText$messageText)';
  }
}
