package com.projectalice.backend.conversation.application.port;

import com.projectalice.backend.conversation.domain.ConversationMessage;
import java.util.List;

public interface ConversationRepository {

    /**
     * Returns up to {@code limit} latest messages of the single conversation,
     * ordered by sequence descending, using a strongly consistent read.
     */
    List<ConversationMessage> findLatestMessages(int limit);
}
