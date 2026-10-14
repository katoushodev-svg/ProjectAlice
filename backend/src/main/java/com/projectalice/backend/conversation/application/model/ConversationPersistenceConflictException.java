package com.projectalice.backend.conversation.application.model;

public final class ConversationPersistenceConflictException extends RuntimeException {
    public ConversationPersistenceConflictException() { super("Conversation state changed during the transaction."); }
}
