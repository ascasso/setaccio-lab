package com.setaccio.lab.thinking;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Confirms a subject-versus-control pairing that does not satisfy the diagnostic's role
 * assignment is rejected before any evidence directory is allocated or the run executes.
 */
class ThinkingDiagnosticModelInventoryTest {

    @Test
    void acceptsASubjectThatAdvertisesThinkingAndADistinctNonThinkingControl() {
        Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities =
                ThinkingDiagnosticTestSupport.identities();

        assertThatCode(() -> ThinkingDiagnosticModelInventory
                .requireDistinctRoleSatisfyingIdentities(identities))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsASubjectThatDoesNotAdvertiseThinking() {
        Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities = Map.of(
                ThinkingDiagnosticModelRole.SUBJECT, ThinkingDiagnosticTestSupport.subject(false),
                ThinkingDiagnosticModelRole.CONTROL, ThinkingDiagnosticTestSupport.control());

        assertThatThrownBy(() -> ThinkingDiagnosticModelInventory
                .requireDistinctRoleSatisfyingIdentities(identities))
                .isInstanceOf(ThinkingDiagnosticModelUnavailableException.class)
                .hasMessageContaining("SUBJECT")
                .hasMessageContaining("must advertise the thinking capability");
    }

    @Test
    void rejectsAControlThatAdvertisesThinking() {
        ThinkingDiagnosticModelIdentity thinkingControl = new ThinkingDiagnosticModelIdentity(
                ThinkingDiagnosticModelRole.CONTROL, "control:model", "control:model",
                "b".repeat(64), true);
        Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities = Map.of(
                ThinkingDiagnosticModelRole.SUBJECT, ThinkingDiagnosticTestSupport.subject(true),
                ThinkingDiagnosticModelRole.CONTROL, thinkingControl);

        assertThatThrownBy(() -> ThinkingDiagnosticModelInventory
                .requireDistinctRoleSatisfyingIdentities(identities))
                .isInstanceOf(ThinkingDiagnosticModelUnavailableException.class)
                .hasMessageContaining("CONTROL")
                .hasMessageContaining("must not advertise the thinking capability");
    }

    @Test
    void rejectsTheSameArtifactResolvedForBothRoles() {
        ThinkingDiagnosticModelIdentity control = new ThinkingDiagnosticModelIdentity(
                ThinkingDiagnosticModelRole.CONTROL, "control:model", "control:model",
                "a".repeat(64), false);
        Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities = Map.of(
                ThinkingDiagnosticModelRole.SUBJECT, ThinkingDiagnosticTestSupport.subject(true),
                ThinkingDiagnosticModelRole.CONTROL, control);

        assertThatThrownBy(() -> ThinkingDiagnosticModelInventory
                .requireDistinctRoleSatisfyingIdentities(identities))
                .isInstanceOf(ThinkingDiagnosticModelUnavailableException.class)
                .hasMessageContaining("must resolve to different installed artifacts");
    }

    @Test
    void rejectsAMissingRole() {
        Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities = Map.of(
                ThinkingDiagnosticModelRole.SUBJECT, ThinkingDiagnosticTestSupport.subject(true));

        assertThatThrownBy(() -> ThinkingDiagnosticModelInventory
                .requireDistinctRoleSatisfyingIdentities(identities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONTROL");
    }
}
