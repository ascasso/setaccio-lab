package com.setaccio.lab.thinking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.ollama.api.OllamaApi;

/** Resolves the two pre-registered tags against the already-installed inventory. No pull. */
public final class ThinkingDiagnosticModelInventory {

    private ThinkingDiagnosticModelInventory() {}

    public static ThinkingDiagnosticModelIdentity requireInstalled(
            OllamaApi.ListModelResponse response,
            OllamaApi.ShowModelResponse shown,
            ThinkingDiagnosticModelRole role,
            String requestedModel
    ) {
        String normalizedRequested = ThinkingDiagnosticProtocol.normalizeModelTag(requestedModel);
        Map<String, OllamaApi.Model> installed = new LinkedHashMap<>();
        if (response != null && response.models() != null) {
            for (OllamaApi.Model model : response.models()) {
                if (model == null || model.name() == null || model.name().isBlank()) {
                    continue;
                }
                String normalized = ThinkingDiagnosticProtocol.normalizeModelTag(model.name());
                if (installed.putIfAbsent(normalized, model) != null) {
                    throw new IllegalArgumentException(
                            "Installed Ollama inventory contains duplicate normalized tag: " + normalized);
                }
            }
        }
        OllamaApi.Model resolved = installed.get(normalizedRequested);
        if (resolved == null) {
            throw new ThinkingDiagnosticModelUnavailableException(
                    "Requested Ollama model is not installed: " + normalizedRequested);
        }
        try {
            return new ThinkingDiagnosticModelIdentity(
                    role,
                    requestedModel.strip(),
                    normalizedRequested,
                    resolved.digest(),
                    advertisesThinking(shown));
        } catch (IllegalArgumentException exception) {
            throw new ThinkingDiagnosticModelUnavailableException(
                    "Installed Ollama model has no complete immutable digest: " + normalizedRequested,
                    exception);
        }
    }

    /**
     * Reads the advertised capability only. An advertised capability describes what the artifact
     * manifest declares under the current runtime, not how the model behaves.
     */
    public static boolean advertisesThinking(OllamaApi.ShowModelResponse shown) {
        List<String> capabilities = shown == null ? null : shown.capabilities();
        return capabilities != null && capabilities.stream()
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch("thinking"::equals);
    }

    /**
     * Confirms the resolved subject and control satisfy their assigned roles and are two
     * distinct installed artifacts, before any evidence directory is allocated or the run
     * executes. The subject-versus-non-thinking-control comparison this diagnostic exists to
     * make is only valid when the subject advertises thinking, the control does not, and the
     * two are not the same artifact under different tags.
     */
    public static void requireDistinctRoleSatisfyingIdentities(
            Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities
    ) {
        ThinkingDiagnosticModelIdentity subject = requirePresent(
                identities, ThinkingDiagnosticModelRole.SUBJECT);
        ThinkingDiagnosticModelIdentity control = requirePresent(
                identities, ThinkingDiagnosticModelRole.CONTROL);
        requireSatisfiesRole(subject, true);
        requireSatisfiesRole(control, false);
        if (subject.digest().equals(control.digest())) {
            throw new ThinkingDiagnosticModelUnavailableException(
                    "Subject and control must resolve to different installed artifacts; both resolved "
                            + "to digest " + subject.digest());
        }
    }

    private static ThinkingDiagnosticModelIdentity requirePresent(
            Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities,
            ThinkingDiagnosticModelRole role
    ) {
        ThinkingDiagnosticModelIdentity identity = identities.get(role);
        if (identity == null) {
            throw new IllegalArgumentException("Missing resolved model identity for role " + role);
        }
        return identity;
    }

    private static void requireSatisfiesRole(
            ThinkingDiagnosticModelIdentity identity, boolean expectedAdvertisesThinking
    ) {
        if (identity.advertisesThinking() != expectedAdvertisesThinking) {
            throw new ThinkingDiagnosticModelUnavailableException(
                    identity.role() + " model " + identity.normalizedInstalledName() + " must "
                            + (expectedAdvertisesThinking ? "" : "not ")
                            + "advertise the thinking capability for this role, but advertisesThinking="
                            + identity.advertisesThinking());
        }
    }
}
