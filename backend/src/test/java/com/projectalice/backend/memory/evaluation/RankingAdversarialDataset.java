package com.projectalice.backend.memory.evaluation;

import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Supplementary synthetic stress cases, kept separate from memory-ranking-v2 so the baseline
 * dataset is unchanged. Cases are written to probe lexical weaknesses, not to favour any policy.
 * Categories: subject-variant, semantic-paraphrase, negation, similar-project.
 */
public final class RankingAdversarialDataset {
    public static final String DATASET_VERSION = "memory-ranking-adversarial-v2";
    private static final String[] CATEGORIES = {"subject-variant", "semantic-paraphrase", "negation", "similar-project",
        "temporal-conflict"};
    private static final int PER_CATEGORY = 25;

    private RankingAdversarialDataset() {}

    public static List<RankingEvaluationDataset.Scenario> generate() {
        List<RankingEvaluationDataset.Scenario> scenarios = new ArrayList<>();
        for (String category : CATEGORIES) {
            for (int i = 0; i < PER_CATEGORY; i++) scenarios.add(create(category, i));
        }
        return List.copyOf(scenarios);
    }

    private static RankingEvaluationDataset.Scenario create(String category, int index) {
        int n = index + 1;
        String num = "%03d".formatted(n);
        RankingEvaluationDataset.Group group = RankingEvaluationDataset.Group.values()[index % 3];
        RankingEvaluationDataset.SearchPurpose purpose = switch (group) {
            case ANSWER_CURRENT_HISTORICAL -> RankingEvaluationDataset.SearchPurpose.ANSWER_CURRENT;
            case RELATION_DECISION -> RankingEvaluationDataset.SearchPurpose.RELATION_DECISION;
            case MANAGEMENT_SEARCH -> RankingEvaluationDataset.SearchPurpose.MANAGEMENT_SEARCH;
        };
        String id = category + "-%03d".formatted(n);

        String query;
        String required;
        String related;
        List<String> decoys = new ArrayList<>();
        switch (category) {
            case "subject-variant" -> {
                query = "ＡＬ－" + num + " のタイムアウト設定を教えて";
                required = "試験用プロジェクトAL-" + num + " のタイムアウトは3秒に決定。";
                related = "試験用プロジェクトAL-" + num + " は ModuleB と連携する。";
                for (int d = 0; d < 60; d++) decoys.add("試験用プロジェクトAL-%03d のタイムアウトは5秒に決定。".formatted(500 + d));
            }
            case "semantic-paraphrase" -> {
                query = "AL-" + num + " で、応答を待つ時間の上限は？";
                required = "AL-" + num + " のAPI応答待機は最大3秒と決めた。";
                related = "AL-" + num + " の通信まわりは ModuleB が担当。";
                for (int d = 0; d < 60; d++) decoys.add("AL-%03d で、応答を待つ時間の上限は5秒だった。".formatted(500 + d));
            }
            case "negation" -> {
                query = "AL-" + num + " のタイムアウトは変更された？";
                required = "AL-" + num + " のタイムアウトは変更されていない。";
                related = "AL-" + num + " の設定履歴は別途管理されている。";
                for (int d = 0; d < 20; d++) decoys.add("AL-" + num + " のタイムアウトは変更された。記録" + d + "。");
                for (int d = 0; d < 40; d++) decoys.add("AL-%03d のタイムアウトは変更されていない。".formatted(500 + d));
            }
            case "temporal-conflict" -> {
                query = "AL-" + num + " の現在の期限は？";
                required = "AL-" + num + " の現在の期限は2026年11月30日です。";
                related = "AL-" + num + " の期限管理は週次で見直す。";
                // Same project but superseded value: wrong for a current-state question.
                for (int d = 0; d < 20; d++) decoys.add("AL-" + num + " の以前の期限は2026年10月31日でした。記録" + d + "。");
                for (int d = 0; d < 40; d++) decoys.add("AL-%03d の現在の期限は2026年12月31日です。".formatted(500 + d));
            }
            default -> {
                query = "AL-" + num + " の担当者は誰？";
                required = "AL-" + num + " の担当者は佐藤です。";
                related = "AL-" + num + " の担当チームは基盤チームです。";
                for (int p = 0; p < 3; p++) {
                    for (char c = '0'; c <= '9'; c++) {
                        if (c == num.charAt(p)) continue;
                        char[] digits = num.toCharArray();
                        digits[p] = c;
                        decoys.add("AL-" + new String(digits) + " の担当者は鈴木です。");
                    }
                }
            }
        }

        List<RankingEvaluationDataset.MemoryFixture> memories = new ArrayList<>(200);
        memories.add(fixture(id + "-m001", required, index, 1, RankingEvaluationDataset.Relevance.REQUIRED, false));
        memories.add(fixture(id + "-m002", related, index, 2, RankingEvaluationDataset.Relevance.RELATED, false));
        // Deleted memory with near-identical text to the query; must never be selected.
        memories.add(fixture(id + "-m003", required + "（削除済み）", index, 3,
                RankingEvaluationDataset.Relevance.IRRELEVANT, true));
        int ordinal = 4;
        for (String decoy : decoys) {
            memories.add(fixture(id + "-m%03d".formatted(ordinal), decoy, index, ordinal,
                    RankingEvaluationDataset.Relevance.IRRELEVANT, false));
            ordinal++;
        }
        while (memories.size() < 200) {
            memories.add(fixture(id + "-m%03d".formatted(ordinal), "週次定例の議事メモ " + ordinal + "。", index, ordinal,
                    RankingEvaluationDataset.Relevance.IRRELEVANT, false));
            ordinal++;
        }

        String requiredId = id + "-m001";
        String relatedId = id + "-m002";
        List<String> recallTargets = switch (group) {
            case ANSWER_CURRENT_HISTORICAL -> List.of(requiredId);
            case RELATION_DECISION -> List.of(relatedId);
            case MANAGEMENT_SEARCH -> List.of(requiredId, relatedId);
        };
        return new RankingEvaluationDataset.Scenario(id, DATASET_VERSION, "adversarial-generator-v1", group, purpose,
                category, query, memories, recallTargets, List.of(id + "-m003"), List.of(requiredId), List.of(relatedId));
    }

    private static RankingEvaluationDataset.MemoryFixture fixture(String id, String content, int scenarioIndex,
            int ordinal, RankingEvaluationDataset.Relevance relevance, boolean deleted) {
        OffsetDateTime created = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(9))
                .plusDays(scenarioIndex).plusMinutes(ordinal);
        var unknown = new RankingEvaluationDataset.ExpectedSignals(RankingEvaluationDataset.SignalLabel.UNKNOWN,
                RankingEvaluationDataset.SignalLabel.UNKNOWN, RankingEvaluationDataset.SignalLabel.UNKNOWN,
                RankingEvaluationDataset.SignalLabel.UNKNOWN, RankingEvaluationDataset.SignalLabel.UNKNOWN);
        return new RankingEvaluationDataset.MemoryFixture(id, content, MemoryCategory.values()[0],
                CaptureType.EXPLICIT, MemoryState.ACTIVE, SensitivityLevel.NORMAL, 1, created, created, null,
                deleted, true, RankingEvaluationDataset.TemporalRole.CURRENT,
                deleted ? RankingEvaluationDataset.Eligibility.INELIGIBLE : RankingEvaluationDataset.Eligibility.ELIGIBLE,
                deleted ? RankingEvaluationDataset.EligibilityReason.DELETED : RankingEvaluationDataset.EligibilityReason.ALLOWED,
                relevance,
                deleted ? RankingEvaluationDataset.SafetyCondition.DELETED : RankingEvaluationDataset.SafetyCondition.NONE,
                unknown);
    }
}
