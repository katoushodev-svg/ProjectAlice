package com.projectalice.backend.memory.evaluation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.SensitivityLevel;

/** Synthetic, deterministic fixtures for validating the DB2-065 evaluation tooling. */
public final class RankingEvaluationDataset {

    public static final String DATASET_VERSION = "memory-ranking-v2";
    public static final String GENERATOR_VERSION = "synthetic-generator-v2";
    public static final int SCENARIO_COUNT = 300;

    public enum Group { ANSWER_CURRENT_HISTORICAL, RELATION_DECISION, MANAGEMENT_SEARCH }
    public enum SearchPurpose { ANSWER_CURRENT, ANSWER_HISTORICAL, RELATION_DECISION, MANAGEMENT_SEARCH }
    public enum Eligibility { ELIGIBLE, INELIGIBLE }
    public enum EligibilityReason { ALLOWED, ANSWER_USE_DISABLED, DELETED, SENSITIVE_NOT_ALLOWED, NON_HISTORICAL_RESOLVED }
    public enum Relevance { REQUIRED, RELATED, IRRELEVANT }
    public enum SafetyCondition { NONE, DELETED, DISALLOWED_SENSITIVE, NON_HISTORICAL_RESOLVED }
    public enum TemporalRole { CURRENT, HISTORICAL }
    public enum SignalLabel { MATCH, PARTIAL_MATCH, MISMATCH, NOT_APPLICABLE, UNKNOWN }

    /** Expected signal labels for evaluation only; these labels do not define production ranking scores. */
    public record ExpectedSignals(SignalLabel entity, SignalLabel intent, SignalLabel scope,
            SignalLabel temporal, SignalLabel confirmation) {
        public ExpectedSignals {
            Objects.requireNonNull(entity);
            Objects.requireNonNull(intent);
            Objects.requireNonNull(scope);
            Objects.requireNonNull(temporal);
            Objects.requireNonNull(confirmation);
        }
    }

    public record MemoryFixture(String id, String content, MemoryCategory category, CaptureType captureType,
            MemoryState state, SensitivityLevel sensitivityLevel, long version,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime confirmedAt,
            boolean deleted, boolean sensitiveUseAllowed, TemporalRole temporalRole,
            Eligibility eligibility, EligibilityReason eligibilityReason, Relevance relevance,
            SafetyCondition safetyCondition, ExpectedSignals expectedSignals) {
        public MemoryFixture {
            Objects.requireNonNull(id);
            Objects.requireNonNull(content);
            Objects.requireNonNull(category);
            Objects.requireNonNull(captureType);
            Objects.requireNonNull(state);
            Objects.requireNonNull(sensitivityLevel);
            if (version < 1) throw new IllegalArgumentException("version must be positive");
            Objects.requireNonNull(createdAt);
            Objects.requireNonNull(updatedAt);
            if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt precedes createdAt");
            if (confirmedAt != null && confirmedAt.isBefore(createdAt)) throw new IllegalArgumentException("confirmedAt precedes createdAt");
            Objects.requireNonNull(temporalRole);
            Objects.requireNonNull(eligibility);
            Objects.requireNonNull(eligibilityReason);
            Objects.requireNonNull(relevance);
            Objects.requireNonNull(safetyCondition);
            Objects.requireNonNull(expectedSignals);
        }
    }

    public record Scenario(String id, String datasetVersion, String generatorVersion, Group group, SearchPurpose purpose,
            String category, String query, List<MemoryFixture> memories,
            List<String> recallTargetIds, List<String> safetyExcludedIds,
            List<String> requiredMemoryIds, List<String> relatedMemoryIds) {
        public Scenario {
            Objects.requireNonNull(id);
            Objects.requireNonNull(datasetVersion);
            Objects.requireNonNull(generatorVersion);
            Objects.requireNonNull(group);
            Objects.requireNonNull(purpose);
            Objects.requireNonNull(category);
            Objects.requireNonNull(query);
            memories = List.copyOf(memories);
            recallTargetIds = List.copyOf(recallTargetIds);
            safetyExcludedIds = List.copyOf(safetyExcludedIds);
            requiredMemoryIds = List.copyOf(requiredMemoryIds);
            relatedMemoryIds = List.copyOf(relatedMemoryIds);
        }
    }

    private RankingEvaluationDataset() {}

    public static List<Scenario> generate() {
        List<Scenario> scenarios = new ArrayList<>(SCENARIO_COUNT);
        for (Group group : Group.values()) {
            for (int i = 0; i < 100; i++) scenarios.add(create(group, i));
        }
        validate(scenarios);
        return List.copyOf(scenarios);
    }

    private static Scenario create(Group group, int index) {
        String id = group.name().toLowerCase() + "-%03d".formatted(index + 1);
        SearchPurpose purpose = switch (group) {
            case ANSWER_CURRENT_HISTORICAL -> index < 50 ? SearchPurpose.ANSWER_CURRENT : SearchPurpose.ANSWER_HISTORICAL;
            case RELATION_DECISION -> SearchPurpose.RELATION_DECISION;
            case MANAGEMENT_SEARCH -> SearchPurpose.MANAGEMENT_SEARCH;
        };
        String category = switch (index % 10) {
            case 0 -> "ja-orthography";
            case 1 -> "mixed-alphanumeric";
            case 2 -> "paraphrase";
            case 3 -> "homonym";
            case 4 -> "negation";
            case 5 -> "temporal";
            case 6 -> "sensitive";
            case 7 -> "resolved";
            case 8 -> "deleted";
            default -> "ordinary";
        };
        String subject = "試験用プロジェクトAL-%03d".formatted(index + 1);
        String query = switch (category) {
            case "ja-orthography" -> "試験用プロジェクト AL－%03d の%s担当は？".formatted(index + 1,
                    purpose == SearchPurpose.ANSWER_HISTORICAL ? "以前の" : "現在の");
            case "mixed-alphanumeric" -> "Project AL-%03d relation decision: ModuleB".formatted(index + 1);
            case "paraphrase" -> subject + "について以前に決めたことを教えて";
            case "homonym" -> "別対象のAL-%03dではなく、%s の記録".formatted(index + 1, subject);
            case "negation" -> subject + " の設定を変更した？変更していない？";
            case "temporal" -> subject + " の期限はいつ？以前の期限と現在を区別して";
            case "sensitive" -> subject + " の許可された範囲の情報は？";
            case "resolved" -> subject + " の現在状態は？";
            case "deleted" -> subject + " の情報を確認";
            default -> subject + " の検証用記録を検索";
        };
        SafetyCondition unsafe = switch (category) {
            case "deleted" -> SafetyCondition.DELETED;
            case "sensitive" -> SafetyCondition.DISALLOWED_SENSITIVE;
            case "resolved" -> SafetyCondition.NON_HISTORICAL_RESOLVED;
            default -> SafetyCondition.NONE;
        };
        MemoryState excludedState = unsafe == SafetyCondition.NON_HISTORICAL_RESOLVED ? MemoryState.RESOLVED : MemoryState.ACTIVE;
        SensitivityLevel excludedSensitivity = unsafe == SafetyCondition.DISALLOWED_SENSITIVE ? SensitivityLevel.SENSITIVE : SensitivityLevel.NORMAL;
        TemporalRole answerRole = purpose == SearchPurpose.ANSWER_HISTORICAL ? TemporalRole.HISTORICAL : TemporalRole.CURRENT;
        MemoryState requiredState = purpose == SearchPurpose.ANSWER_HISTORICAL ? MemoryState.RESOLVED : MemoryState.ACTIVE;
        List<MemoryFixture> memories = new ArrayList<>(200);
        memories.add(fixture(id + "-m001", subject + " の検証用決定。", index, 1, requiredState, SensitivityLevel.NORMAL,
                answerRole, Eligibility.ELIGIBLE, EligibilityReason.ALLOWED, Relevance.REQUIRED, SafetyCondition.NONE, false, true));
        memories.add(fixture(id + "-m002", subject + " は ModuleB と連携する。", index, 2, MemoryState.ACTIVE,
                SensitivityLevel.NORMAL, TemporalRole.CURRENT, Eligibility.ELIGIBLE, EligibilityReason.ALLOWED,
                Relevance.RELATED, SafetyCondition.NONE, false, true));
        Relevance thirdRelevance = group == Group.RELATION_DECISION && index % 2 == 0
                ? Relevance.RELATED : Relevance.IRRELEVANT;
        memories.add(fixture(id + "-m003", thirdRelevance == Relevance.RELATED
                        ? subject + " の関連モジュールに関する補足記録。"
                        : "別対象「AL-" + (index + 1) + "」の合成記録。",
                index, 3, MemoryState.ACTIVE, SensitivityLevel.NORMAL, TemporalRole.CURRENT,
                Eligibility.ELIGIBLE, EligibilityReason.ALLOWED, thirdRelevance, SafetyCondition.NONE, false, true));
        EligibilityReason excludedReason = switch (unsafe) {
            case NONE -> EligibilityReason.ANSWER_USE_DISABLED;
            case DELETED -> EligibilityReason.DELETED;
            case DISALLOWED_SENSITIVE -> EligibilityReason.SENSITIVE_NOT_ALLOWED;
            case NON_HISTORICAL_RESOLVED -> EligibilityReason.NON_HISTORICAL_RESOLVED;
        };
        memories.add(fixture(id + "-m004", "安全除外を表す合成fixture。", index, 4, excludedState,
                excludedSensitivity, TemporalRole.CURRENT, Eligibility.INELIGIBLE, excludedReason,
                Relevance.IRRELEVANT, unsafe, unsafe == SafetyCondition.DELETED,
                unsafe != SafetyCondition.DISALLOWED_SENSITIVE));
        for (int candidate = 5; candidate <= 200; candidate++) {
            Relevance relevance = candidate == 5 && index % 3 == 0 ? Relevance.REQUIRED : Relevance.IRRELEVANT;
            String content = relevance == Relevance.REQUIRED
                    ? subject + " の検証用決定を補足する必須情報。"
                    : "無関係な合成候補 " + candidate + " for " + subject + "。";
            memories.add(fixture(id + "-m%03d".formatted(candidate), content, index, candidate,
                    MemoryState.ACTIVE, SensitivityLevel.NORMAL, TemporalRole.CURRENT,
                    Eligibility.ELIGIBLE, EligibilityReason.ALLOWED, relevance, SafetyCondition.NONE, false, true));
        }
        List<String> requiredIds = memories.stream()
                .filter(m -> m.eligibility() == Eligibility.ELIGIBLE && m.relevance() == Relevance.REQUIRED)
                .map(MemoryFixture::id).toList();
        List<String> relatedIds = memories.stream()
                .filter(m -> m.eligibility() == Eligibility.ELIGIBLE && m.relevance() == Relevance.RELATED)
                .map(MemoryFixture::id).toList();
        List<String> recallTargets = switch (group) {
            case ANSWER_CURRENT_HISTORICAL -> requiredIds;
            case RELATION_DECISION -> relatedIds;
            case MANAGEMENT_SEARCH -> java.util.stream.Stream.concat(requiredIds.stream(), relatedIds.stream()).toList();
        };
        return new Scenario(id, DATASET_VERSION, GENERATOR_VERSION, group, purpose, category, query, memories,
                recallTargets, unsafe == SafetyCondition.NONE ? List.of() : List.of(id + "-m004"),
                requiredIds, relatedIds);
    }

    private static MemoryFixture fixture(String id, String content, int scenarioIndex, int ordinal,
            MemoryState state, SensitivityLevel sensitivity, TemporalRole role, Eligibility eligibility,
            EligibilityReason eligibilityReason, Relevance relevance, SafetyCondition safetyCondition,
            boolean deleted, boolean sensitiveUseAllowed) {
        OffsetDateTime created = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(9))
                .plusDays(scenarioIndex).plusMinutes(ordinal);
        long version = state == MemoryState.RESOLVED ? 2 : 1;
        return new MemoryFixture(id, content, MemoryCategory.values()[(scenarioIndex + ordinal) % MemoryCategory.values().length],
                ordinal % 2 == 0 ? CaptureType.AUTOMATIC : CaptureType.EXPLICIT, state, sensitivity, version,
                created, created.plusDays(version), ordinal == 1 ? created.plusHours(1) : null, deleted,
                sensitiveUseAllowed, role, eligibility, eligibilityReason, relevance, safetyCondition,
                expectedSignals(scenarioIndex, ordinal, relevance, role));
    }

    private static ExpectedSignals expectedSignals(int scenarioIndex, int ordinal, Relevance relevance,
            TemporalRole temporalRole) {
        if (relevance == Relevance.REQUIRED) {
            return new ExpectedSignals(SignalLabel.MATCH, SignalLabel.MATCH, SignalLabel.MATCH,
                    SignalLabel.MATCH, SignalLabel.MATCH);
        }
        if (relevance == Relevance.RELATED) {
            return new ExpectedSignals(SignalLabel.MATCH, SignalLabel.PARTIAL_MATCH, SignalLabel.PARTIAL_MATCH,
                    temporalRole == TemporalRole.CURRENT ? SignalLabel.MATCH : SignalLabel.PARTIAL_MATCH,
                    SignalLabel.UNKNOWN);
        }
        // Deterministic synthetic labels deliberately include partial, mismatched, inapplicable and unknown signals.
        SignalLabel[] labels = SignalLabel.values();
        int seed = scenarioIndex + ordinal;
        return new ExpectedSignals(labels[seed % labels.length], labels[(seed + 1) % labels.length],
                labels[(seed + 2) % labels.length], labels[(seed + 3) % labels.length],
                labels[(seed + 4) % labels.length]);
    }

    public static void validate(List<Scenario> scenarios) {
        if (scenarios.size() != SCENARIO_COUNT) throw new IllegalArgumentException("Expected 300 scenarios");
        Set<String> ids = new HashSet<>();
        Set<String> allMemoryIds = new HashSet<>();
        java.util.Map<String, Long> categoryCounts = new java.util.HashMap<>();
        for (Scenario scenario : scenarios) {
            if (!DATASET_VERSION.equals(scenario.datasetVersion()) || !GENERATOR_VERSION.equals(scenario.generatorVersion()))
                throw new IllegalArgumentException("Version mismatch");
            if (!ids.add(scenario.id())) throw new IllegalArgumentException("Duplicate scenario id");
            categoryCounts.merge(scenario.category(), 1L, Long::sum);
            if (scenario.query().isBlank() || scenario.category().isBlank() || scenario.memories().size() != 200)
                throw new IllegalArgumentException("Required field missing");
            Set<String> memoryIds = new HashSet<>();
            for (MemoryFixture fixture : scenario.memories())
                if (!memoryIds.add(fixture.id()) || !allMemoryIds.add(fixture.id()))
                    throw new IllegalArgumentException("Duplicate fixture id");
            if (!memoryIds.containsAll(scenario.recallTargetIds()) || !memoryIds.containsAll(scenario.safetyExcludedIds())
                    || !memoryIds.containsAll(scenario.requiredMemoryIds())
                    || !memoryIds.containsAll(scenario.relatedMemoryIds()))
                throw new IllegalArgumentException("Dangling fixture reference");
            if (!new HashSet<>(scenario.requiredMemoryIds()).equals(scenario.memories().stream()
                    .filter(m -> m.eligibility() == Eligibility.ELIGIBLE && m.relevance() == Relevance.REQUIRED)
                    .map(MemoryFixture::id).collect(java.util.stream.Collectors.toSet())))
                throw new IllegalArgumentException("Required labels disagree with fixture relevance");
            if (!new HashSet<>(scenario.relatedMemoryIds()).equals(scenario.memories().stream()
                    .filter(m -> m.eligibility() == Eligibility.ELIGIBLE && m.relevance() == Relevance.RELATED)
                    .map(MemoryFixture::id).collect(java.util.stream.Collectors.toSet())))
                throw new IllegalArgumentException("Related labels disagree with fixture relevance");
            for (String excluded : scenario.safetyExcludedIds())
                if (scenario.memories().stream().filter(m -> m.id().equals(excluded)).anyMatch(m ->
                        m.eligibility() != Eligibility.INELIGIBLE || m.safetyCondition() == SafetyCondition.NONE))
                    throw new IllegalArgumentException("Safety exclusion must be ineligible");
            for (MemoryFixture fixture : scenario.memories())
                if (fixture.safetyCondition() != SafetyCondition.NONE && !scenario.safetyExcludedIds().contains(fixture.id()))
                    throw new IllegalArgumentException("Unsafe fixture missing from safety exclusion labels");
            for (MemoryFixture fixture : scenario.memories()) {
                boolean stateMatches = switch (fixture.safetyCondition()) {
                    case NONE -> true;
                    case DELETED -> fixture.deleted();
                    case DISALLOWED_SENSITIVE -> fixture.sensitivityLevel() == SensitivityLevel.SENSITIVE
                            && !fixture.sensitiveUseAllowed();
                    case NON_HISTORICAL_RESOLVED -> fixture.state() == MemoryState.RESOLVED
                            && fixture.temporalRole() != TemporalRole.HISTORICAL;
                };
                if (!stateMatches) throw new IllegalArgumentException("Safety condition and fixture attributes disagree");
            }
            for (String target : scenario.recallTargetIds())
                if (scenario.memories().stream().filter(m -> m.id().equals(target)).anyMatch(m ->
                        m.eligibility() != Eligibility.ELIGIBLE || m.relevance() == Relevance.IRRELEVANT))
                    throw new IllegalArgumentException("Recall target must be relevant and eligible");
            if (scenario.requiredMemoryIds().stream().anyMatch(scenario.relatedMemoryIds()::contains))
                throw new IllegalArgumentException("A fixture cannot be both required and related");
        }
        if (categoryCounts.size() != 10 || categoryCounts.values().stream().anyMatch(count -> count != 30L))
            throw new IllegalArgumentException("Expected 30 scenarios for each of 10 categories");
        for (Group group : Group.values())
            if (scenarios.stream().filter(s -> s.group() == group).count() != 100)
                throw new IllegalArgumentException("Expected 100 scenarios for " + group);
        if (scenarios.stream().filter(s -> s.purpose() == SearchPurpose.ANSWER_CURRENT).count() != 50
                || scenarios.stream().filter(s -> s.purpose() == SearchPurpose.ANSWER_HISTORICAL).count() != 50)
            throw new IllegalArgumentException("Expected 50 current and 50 historical answer scenarios");
    }
}
