import '../parsing/api_contract_exception.dart';

class ConversationDto {
  const ConversationDto({
    required this.id,
    required this.createdAt,
    required this.updatedAt,
  });

  final String id;
  final String createdAt;
  final String updatedAt;

  factory ConversationDto.fromJson(Map<String, dynamic> json) {
    final id = json['id'];
    final createdAt = json['createdAt'];
    final updatedAt = json['updatedAt'];

    if (id is! String || createdAt is! String || updatedAt is! String) {
      throw ApiContractException(category: 'missingRequiredField');
    }

    return ConversationDto(id: id, createdAt: createdAt, updatedAt: updatedAt);
  }
}
