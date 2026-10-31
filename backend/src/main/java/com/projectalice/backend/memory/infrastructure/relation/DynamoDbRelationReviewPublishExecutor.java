package com.projectalice.backend.memory.infrastructure.relation;

import com.projectalice.backend.memory.application.RelationReviewDraft;
import java.util.List;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;

/** Executes only the single atomic publish request produced by the validated Review builder. */
public final class DynamoDbRelationReviewPublishExecutor {
    private final DynamoDbClient client;
    private final DynamoDbRelationReviewPublishRequestBuilder requestBuilder;

    public DynamoDbRelationReviewPublishExecutor(DynamoDbClient client,
            DynamoDbRelationReviewPublishRequestBuilder requestBuilder) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.requestBuilder = Objects.requireNonNull(requestBuilder, "requestBuilder must not be null");
    }

    /** Publishes draft metadata and encrypted rows together with Control, Reference and Binding. */
    public TransactWriteItemsResponse publishDirect(RelationReviewDraft draft,
            List<DynamoDbRelationReviewPublishRequestBuilder.Action> actions) {
        TransactWriteItemsRequest request = requestBuilder.buildDirect(draft, actions);
        return client.transactWriteItems(request);
    }

    /** Commits a previously staged encrypted target set with the same atomic publish boundary. */
    public TransactWriteItemsResponse publishStaged(RelationReviewDraft draft,
            List<DynamoDbRelationReviewPublishRequestBuilder.Action> actions, int stagedTargetCount,
            String targetSetDigest) {
        TransactWriteItemsRequest request = requestBuilder.buildStaged(
                draft, actions, stagedTargetCount, targetSetDigest);
        return client.transactWriteItems(request);
    }
}
