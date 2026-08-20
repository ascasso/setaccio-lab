/**
 * Provider-free T1.8 requirement-to-test map.
 *
 * <p>Preflight:</p>
 * <ul>
 *   <li>Missing model, unknown option, and duplicate option:
 *       {@code ToolCompatibilityRunnerArgumentsTest#requiresEveryExplicitLiveOptionExactlyOnce}.</li>
 *   <li>Missing installed model, incomplete digest, duplicate normalized tag, and duplicate alias
 *       for one digest:
 *       {@code ToolCompatibilityInvocationBoundaryTest#refusesMissingIncompleteOrAmbiguousInstalledModelIdentity}.</li>
 *   <li>Non-loopback endpoint, endpoint userinfo, and disallowed path/query/fragment:
 *       {@code ToolCompatibilityPreflightTest#rejectsNonLoopbackEndpointsBeforeCreatingAProviderSession},
 *       {@code #rejectsEndpointUserInfoBeforeCreatingAProviderSession}, and
 *       {@code #rejectsEndpointPathQueryAndFragmentBeforeCreatingAProviderSession}.</li>
 *   <li>Reused output, outside-root output, and symbolic-link output:
 *       {@code ToolCompatibilityPreflightTest#allocatesOneFreshDatedDirectChildAndNeverReusesIt}
 *       and {@code #rejectsSymbolicLinksInTheOutputPath}.</li>
 *   <li>Oracle byte/digest, case-ID, tool-name, and count drift:
 *       {@code ToolCompatibilityProtocolTest#rejectsCaseOracleByteAndDigestDrift} and
 *       {@code #rejectsCaseOracleCaseIdToolNameAndCountDrift}.</li>
 *   <li>Invalid token and timeout bounds:
 *       {@code ToolCompatibilityPreflightTest#rejectsInvalidTokenBounds} and
 *       {@code #rejectsInvalidTimeoutBounds}.</li>
 * </ul>
 *
 * <p>Execution:</p>
 * <ul>
 *   <li>Exact 16-row order, seeds 42/43, one attempt, successful single-step and multi-step tools,
 *       abstention, no-match, deterministic callback failure, ordered turns, per-call linkage,
 *       retained finish/usage/limit/latency, intermediate output limit, and visible marker:
 *       {@code ToolCompatibilityMatrixExecutorTest#executesTheExactSixteenRowsSequentiallyWithCanonicalSingleMultiNoMatchAndAbstentionCases}.</li>
 *   <li>Initial/continuation/final turn order and callback correlation:
 *       {@code ToolCompatibilityInvocationBoundaryTest#observesEachStandardAdvisorTurnAndLinksTwoCallbacksToTheirOriginatingTurns}.</li>
 *   <li>Per-turn failure retention, no replacement after an initial failure, and continuation
 *       of the remaining schedule:
 *       {@code ToolCompatibilityMatrixExecutorTest#retainsAnInitialProviderFailureWithoutReplacementAndContinuesTheRemainingSchedule}.</li>
 *   <li>Initial and later provider failures:
 *       the preceding matrix test and
 *       {@code ToolCompatibilityInvocationBoundaryTest#retainsAProviderFailureOnALaterTurnWithoutAReplay}.</li>
 *   <li>Missing, additional, reordered, and duplicate call sequences plus exact agreement:
 *       {@code ToolCompatibilityAnalyzerTest#distinguishesMissingAdditionalReorderedDuplicateForbiddenAndAbstentionCounts}.</li>
 *   <li>JSON-typed semantic agreement, numeric-scale equivalence, schema-valid semantic mismatch,
 *       and schema-invalid framework coercion:
 *       {@code ToolCompatibilityRowTest#mathematicalNumberScaleDoesNotCreateAnOracleMismatch},
 *       {@code ToolCompatibilityInvocationBoundaryTest#retainsSchemaValidButSemanticallyWrongArgumentsBeforeCallbackBinding},
 *       and {@code #retainsSchemaCoercionSeparatelyFromCallbackSuccess}.</li>
 *   <li>Callback failure:
 *       {@code ToolCompatibilityInvocationBoundaryTest#retainsCallbackInvocationFailureSeparatelyFromBindingFailure}.</li>
 *   <li>Tool success followed by an empty final response:
 *       {@code ToolCompatibilityAnalyzerTest#excludesFailedContractsFromSuccessfulLatencyAndClassifiesEmptyFinalOutput}.</li>
 *   <li>Complete, partial, and absent per-turn usage plus deterministic row aggregation:
 *       {@code ToolCompatibilityAnalyzerTest#summarizesEveryDimensionWithoutCombiningThemIntoAScore}.</li>
 *   <li>Retained turns at the row deadline and proof of no timeout overlap:
 *       {@code ToolCompatibilityInvocationBoundaryTest#preservesCompletedObservationsAndPreventsOverlapAfterAnInterruptedRowTimeout}
 *       and {@code #refusesTheNextSequentialAttemptWhenTimedOutProviderWorkIgnoresInterruption}.</li>
 * </ul>
 *
 * <p>Evidence:</p>
 * <ul>
 *   <li>Strict raw-result schema:
 *       {@code ToolCompatibilityEvidenceIntegrityTest#rejectsUnknownRawResultSchemaFieldsAfterArtifactIntegrityIsRefreshed}.</li>
 *   <li>Manifest suite/run identity:
 *       {@code ToolCompatibilityEvidenceIntegrityTest#rejectsManifestSuiteAndRunIdentityDrift}.</li>
 *   <li>Canonical prompt, untreated system prompt, and full model digest:
 *       {@code ToolCompatibilityEvidenceIntegrityTest#rejectsCanonicalPromptSystemPromptAndFullModelDigestDrift}.</li>
 *   <li>Settings parity:
 *       {@code ToolCompatibilityEvidenceTest#rejectsManifestSettingsDriftAndRepairsOnlySummaryDriftByteForByte}.</li>
 *   <li>Artifact SHA-256 plus missing, extra, and empty artifacts:
 *       {@code ToolCompatibilityEvidenceTest#rejectsTamperedMissingEmptyAndUnexpectedArtifactsWithoutReanalysis}.</li>
 *   <li>Path traversal and symlinks:
 *       {@code ToolCompatibilityEvidenceTest#rejectsUnsafeManifestPathsAndSymbolicLinkArtifacts}.</li>
 *   <li>Row-order drift:
 *       {@code ToolCompatibilityEvidenceIntegrityTest#rejectsRowOrderDriftAfterArtifactIntegrityIsRefreshed}.</li>
 *   <li>Provider-turn order/linkage:
 *       {@code ToolCompatibilityEvidenceIntegrityTest#rejectsProviderTurnOrderAndToolCallLinkageDrift}.</li>
 *   <li>Tool-call turn/response linkage:
 *       {@code ToolCompatibilityEvidenceIntegrityTest#rejectsToolCallTurnAndResponseLinkageDrift}.</li>
 *   <li>Semantic-oracle identity/assertion drift and attempt-count drift:
 *       {@code ToolCompatibilityEvidenceIntegrityTest#rejectsSemanticOracleIdentityAndAssertionDrift}
 *       and {@code #rejectsAttemptCountDrift}.</li>
 *   <li>Summary drift and deterministic byte-identical reanalysis:
 *       {@code ToolCompatibilityEvidenceTest#rejectsManifestSettingsDriftAndRepairsOnlySummaryDriftByteForByte}
 *       and {@code #standaloneRunnerVerifiesAndReanalyzesWithoutStartingSpring}.</li>
 * </ul>
 */
package com.setaccio.lab.toolcompat;
