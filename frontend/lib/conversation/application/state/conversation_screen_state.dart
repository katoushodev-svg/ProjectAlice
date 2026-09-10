import 'dart:collection';

import '../../domain/conversation.dart';
import '../../domain/message.dart';
import '../../domain/outgoing_message.dart';
import '../error/conversation_failure.dart';

enum ConversationScreenStatus {
  initialLoading,
  ready,
  loadingOlder,
  sending,
  streaming,
  sendFailed,
  initialLoadFailed,
}

final class ConversationPaginationState {
  ConversationPaginationState({
    required this.hasMore,
    required this.nextCursor,
  }) {
    if (hasMore && (nextCursor == null || nextCursor!.isEmpty)) {
      throw ArgumentError('Pagination requires a non-empty cursor.');
    }
    if (!hasMore && nextCursor != null) {
      throw ArgumentError('Pagination without more pages has no cursor.');
    }
  }

  ConversationPaginationState.initial() : hasMore = false, nextCursor = null;

  final bool hasMore;
  final String? nextCursor;
}

final class ConversationScreenState {
  ConversationScreenState._({
    required this.status,
    required this.conversation,
    required List<Message> messages,
    required this.temporaryAssistantText,
    required this.pagination,
    required this.pendingSend,
    required this.activeRequestId,
    required this.failure,
  }) : messages = UnmodifiableListView<Message>(List<Message>.of(messages)) {
    _validate();
  }

  factory ConversationScreenState.initial() {
    return ConversationScreenState._(
      status: ConversationScreenStatus.initialLoading,
      conversation: null,
      messages: const [],
      temporaryAssistantText: null,
      pagination: ConversationPaginationState.initial(),
      pendingSend: null,
      activeRequestId: null,
      failure: null,
    );
  }

  final ConversationScreenStatus status;
  final Conversation? conversation;
  final List<Message> messages;
  final String? temporaryAssistantText;
  final ConversationPaginationState pagination;
  final OutgoingMessage? pendingSend;
  final String? activeRequestId;
  final ConversationFailure? failure;

  static ConversationScreenState fromReducer({
    required ConversationScreenStatus status,
    Conversation? conversation,
    List<Message> messages = const [],
    String? temporaryAssistantText,
    ConversationPaginationState? pagination,
    OutgoingMessage? pendingSend,
    String? activeRequestId,
    ConversationFailure? failure,
  }) {
    return ConversationScreenState._(
      status: status,
      conversation: conversation,
      messages: messages,
      temporaryAssistantText: temporaryAssistantText,
      pagination: pagination ?? ConversationPaginationState.initial(),
      pendingSend: pendingSend,
      activeRequestId: activeRequestId,
      failure: failure,
    );
  }

  ConversationScreenState copyWith({
    ConversationScreenStatus? status,
    Object? conversation = _unchanged,
    List<Message>? messages,
    Object? temporaryAssistantText = _unchanged,
    ConversationPaginationState? pagination,
    Object? pendingSend = _unchanged,
    Object? activeRequestId = _unchanged,
    Object? failure = _unchanged,
  }) {
    return ConversationScreenState._(
      status: status ?? this.status,
      conversation: identical(conversation, _unchanged)
          ? this.conversation
          : conversation as Conversation?,
      messages: messages ?? this.messages,
      temporaryAssistantText: identical(temporaryAssistantText, _unchanged)
          ? this.temporaryAssistantText
          : temporaryAssistantText as String?,
      pagination: pagination ?? this.pagination,
      pendingSend: identical(pendingSend, _unchanged)
          ? this.pendingSend
          : pendingSend as OutgoingMessage?,
      activeRequestId: identical(activeRequestId, _unchanged)
          ? this.activeRequestId
          : activeRequestId as String?,
      failure: identical(failure, _unchanged)
          ? this.failure
          : failure as ConversationFailure?,
    );
  }

  void _validate() {
    final hasTemporaryText = temporaryAssistantText != null;
    switch (status) {
      case ConversationScreenStatus.initialLoading:
        if (activeRequestId != null || hasTemporaryText) {
          throw ArgumentError('Invalid initial loading state.');
        }
      case ConversationScreenStatus.ready:
        if (pendingSend != null ||
            activeRequestId != null ||
            hasTemporaryText) {
          throw ArgumentError('Invalid ready state.');
        }
        if (failure != null &&
            failure!.operation != ConversationOperation.pagination) {
          throw ArgumentError('Ready may only retain pagination failure.');
        }
      case ConversationScreenStatus.loadingOlder:
        if (!pagination.hasMore || pagination.nextCursor == null) {
          throw ArgumentError('Invalid older-page loading state.');
        }
        if (pendingSend != null ||
            activeRequestId != null ||
            hasTemporaryText) {
          throw ArgumentError('Invalid older-page loading state.');
        }
      case ConversationScreenStatus.sending:
        if (pendingSend == null || activeRequestId != null || failure != null) {
          throw ArgumentError('Invalid sending state.');
        }
        if (hasTemporaryText) {
          throw ArgumentError('Sending cannot have temporary text.');
        }
      case ConversationScreenStatus.streaming:
        if (pendingSend == null ||
            activeRequestId == null ||
            !hasTemporaryText) {
          throw ArgumentError('Invalid streaming state.');
        }
        if (failure != null) {
          throw ArgumentError('Streaming cannot have a failure.');
        }
      case ConversationScreenStatus.sendFailed:
        if (failure == null ||
            failure!.operation != ConversationOperation.send) {
          throw ArgumentError('Send failure is required.');
        }
        if (activeRequestId != null) {
          throw ArgumentError('Send failure cannot have an active request.');
        }
      case ConversationScreenStatus.initialLoadFailed:
        if (failure == null ||
            failure!.operation != ConversationOperation.initialLoad ||
            conversation != null ||
            messages.isNotEmpty ||
            pendingSend != null ||
            activeRequestId != null ||
            hasTemporaryText) {
          throw ArgumentError('Invalid initial load failure state.');
        }
    }
  }

  @override
  String toString() => 'ConversationScreenState(status: $status)';
}

const Object _unchanged = Object();
