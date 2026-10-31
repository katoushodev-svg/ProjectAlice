package com.projectalice.backend.memory.evaluation;

import java.util.ArrayList;
import java.util.List;

/**
 * Independent synthetic dataset for checking whether Answer Context Precision@8 can be measured at all.
 * Built on the adversarial scenarios (same queries, decoys and safety fixtures), but each scenario carries
 * 10 eligible relevant memories (2 REQUIRED + 8 RELATED) instead of 2, so the Precision@8 ceiling is 1.0.
 * Assumption: a real answer query on a well-used subject can have 8+ relevant memories; this is not
 * measured from production data. Raising the relevant share raises achievable Precision by construction,
 * so scores here must not be read as evidence about real-world precision.
 * Extra relevant texts are 4 topic-related and 3 subject-only memories; none are tuned to any policy.
 */
public final class RankingDenseRelevanceDataset {
    public static final String DATASET_VERSION = "memory-ranking-dense-relevance-v1";
    static final int EXTRA_RELEVANT = 8;

    private RankingDenseRelevanceDataset() {}

    public static List<RankingEvaluationDataset.Scenario> generate() {
        List<RankingEvaluationDataset.Scenario> result = new ArrayList<>();
        for (var base : RankingAdversarialDataset.generate()) result.add(densify(base));
        return List.copyOf(result);
    }

    private static RankingEvaluationDataset.Scenario densify(RankingEvaluationDataset.Scenario base) {
        String num = base.id().substring(base.id().length() - 3);
        String subject = base.category().equals("subject-variant") ? "試験用プロジェクトAL-" + num : "AL-" + num;
        String topic = switch (base.category()) {
            case "subject-variant" -> "のタイムアウト設定の経緯";
            case "semantic-paraphrase" -> "の応答待機に関する検討メモ";
            case "negation" -> "のタイムアウトについての設定確認";
            case "similar-project" -> "の担当者に関する引き継ぎ";
            default -> "の期限に関する調整記録";
        };
        String[] subjectOnly = {"に関する会議メモ", "の関係者一覧", "のリリース計画"};

        List<RankingEvaluationDataset.MemoryFixture> memories = new ArrayList<>(base.memories());
        // Replace the trailing generic filler memories; decoys and the safety fixture stay untouched.
        List<String> extraIds = new ArrayList<>();
        for (int k = 0; k < EXTRA_RELEVANT; k++) {
            int slot = memories.size() - 1 - k;
            var old = memories.get(slot);
            boolean required = k == 0;
            String content = k < 5 ? subject + topic + " " + (k + 1) + "。"
                    : subject + subjectOnly[k - 5] + " " + (k + 1) + "。";
            var relevance = required ? RankingEvaluationDataset.Relevance.REQUIRED : RankingEvaluationDataset.Relevance.RELATED;
            memories.set(slot, new RankingEvaluationDataset.MemoryFixture(old.id(), content, old.category(),
                    old.captureType(), old.state(), old.sensitivityLevel(), old.version(), old.createdAt(),
                    old.updatedAt(), old.confirmedAt(), old.deleted(), old.sensitiveUseAllowed(), old.temporalRole(),
                    old.eligibility(), old.eligibilityReason(), relevance, old.safetyCondition(), old.expectedSignals()));
            extraIds.add(old.id());
        }
        List<String> required = new ArrayList<>(base.requiredMemoryIds());
        List<String> related = new ArrayList<>(base.relatedMemoryIds());
        required.add(extraIds.get(0));
        related.addAll(extraIds.subList(1, extraIds.size()));
        List<String> targets = switch (base.group()) {
            case ANSWER_CURRENT_HISTORICAL -> required;
            case RELATION_DECISION -> related;
            case MANAGEMENT_SEARCH -> {
                List<String> all = new ArrayList<>(required);
                all.addAll(related);
                yield all;
            }
        };
        return new RankingEvaluationDataset.Scenario(base.id(), DATASET_VERSION, "dense-relevance-generator-v1",
                base.group(), base.purpose(), base.category(), base.query(), memories, targets,
                base.safetyExcludedIds(), required, related);
    }
}
