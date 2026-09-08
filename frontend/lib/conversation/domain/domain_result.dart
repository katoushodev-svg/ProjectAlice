import 'conversation_domain_error.dart';

sealed class DomainResult<T> {
  const DomainResult();
}

final class DomainSuccess<T> extends DomainResult<T> {
  const DomainSuccess(this.value);

  final T value;
}

final class DomainFailure<T> extends DomainResult<T> {
  const DomainFailure(this.error);

  final ConversationDomainError error;
}
