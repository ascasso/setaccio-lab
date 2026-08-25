package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LocalEvaluationBudgetProtocolTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Test
    void locksExactlyTheFresh64And256TokenArms() {
        LocalEvaluationRunSettings budget64 = LocalEvaluationBudgetProtocol.settings("judge-model", 64);
        LocalEvaluationRunSettings budget256 = LocalEvaluationBudgetProtocol.settings("judge-model", 256);

        assertThat(LocalEvaluationBudgetProtocol.VERSION).isOne();
        assertThat(LocalEvaluationBudgetProtocol.MAX_TOKENS).containsExactly(64, 256);
        assertThat(LocalEvaluationBudgetProtocol.ROW_COUNT).isEqualTo(12);
        assertThat(budget64.maxTokens()).isEqualTo(64);
        assertThat(budget256.maxTokens()).isEqualTo(256);
        assertThat(budget64.repetitions()).isEqualTo(2);
        assertThat(budget64.temperature()).isZero();
        assertThat(budget64.seeds()).containsExactly(42, 43);
        assertThat(budget64.timeoutMillis()).isEqualTo(Duration.ofMinutes(2).toMillis());
        assertThat(budget64.maxAttempts()).isOne();
        assertThat(budget64.requestedModel()).isEqualTo(budget256.requestedModel());
        assertThat(budget64.repetitions()).isEqualTo(budget256.repetitions());
        assertThat(budget64.temperature()).isEqualTo(budget256.temperature());
        assertThat(budget64.seeds()).isEqualTo(budget256.seeds());
        assertThat(budget64.timeoutMillis()).isEqualTo(budget256.timeoutMillis());
        assertThat(budget64.maxAttempts()).isEqualTo(budget256.maxAttempts());
        LocalEvaluationBudgetProtocol.requirePairSettings(budget64, budget256);
    }

    @Test
    void rejectsAnyThirdTokenLevel() {
        assertThatThrownBy(() -> LocalEvaluationBudgetProtocol.settings("judge-model", 128))
                .isInstanceOf(LocalEvaluationBudgetProtocolIntegrityException.class)
                .hasMessageContaining("exactly 64 or 256");
    }

    @Test
    void retainsTheOriginalFactCheckIdentitiesAndCounterbalancedSchedule() {
        LocalEvaluationContract contract = LocalEvaluationContract.load(OBJECT_MAPPER);

        contract.requireLockedAndConfirmed();
        assertThat(contract.prompt().id()).isEqualTo(LocalFactCheckPromptDefinition.ID);
        assertThat(contract.prompt().version()).isEqualTo(LocalFactCheckPromptDefinition.VERSION);
        assertThat(contract.prompt().sha256()).isEqualTo(LocalFactCheckPromptDefinition.SHA256);
        assertThat(contract.catalog().id()).isEqualTo(LocalFactCheckFixtureCatalog.ID);
        assertThat(contract.catalog().version()).isEqualTo(LocalFactCheckFixtureCatalog.VERSION);
        assertThat(contract.catalog().sha256()).isEqualTo(LocalFactCheckFixtureCatalog.SHA256);
        assertThat(contract.review().id()).isEqualTo(LocalFactCheckFixtureReview.ID);
        assertThat(contract.review().version()).isEqualTo(LocalFactCheckFixtureReview.VERSION);
        assertThat(contract.review().sha256()).isEqualTo(LocalFactCheckFixtureReview.SHA256);

        assertThat(LocalEvaluationProtocol.schedule(contract.catalog())).hasSize(12);
        assertThat(LocalEvaluationProtocol.schedule(contract.catalog()))
                .extracting(LocalEvaluationScheduleEntry::seed)
                .containsExactly(42, 42, 42, 42, 42, 42, 43, 43, 43, 43, 43, 43);
    }
}
