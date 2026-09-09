import '../parsing/api_contract_exception.dart';

class MessageDto {
  const MessageDto({
    required this.id,
    required this.role,
    required this.content,
    required this.createdAt,
  });

  final String id;
  final String role;
  final String content;
  final String createdAt;

  factory MessageDto.fromJson(Map<String, dynamic> json) {
    final id = json['id'];
    final role = json['role'];
    final content = json['content'];
    final createdAt = json['createdAt'];

    if (id is! String ||
        role is! String ||
        content is! String ||
        createdAt is! String) {
      throw const ApiContractException(category: 'missingRequiredField');
    }

    return MessageDto(
      id: id,
      role: role,
      content: content,
      createdAt: createdAt,
    );
  }
}
