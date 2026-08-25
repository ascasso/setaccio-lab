/**
 * Provider-free tool-compatibility requirement-to-test map.
 *
 * <p>Prompt catalog:</p>
 * <ul>
 *   <li>Exactly two ordered conditions, their catalog and per-prompt SHA-256 identities, and
 *       exact UTF-8 tool-discipline bytes:
 *       {@code ToolCompatibilitySystemPromptCatalogTest#locksExactlyTwoOrderedPromptConditionsWithExactUtf8Bytes}.</li>
 *   <li>Catalog byte, text, and condition-order drift:
 *       {@code ToolCompatibilitySystemPromptCatalogTest#rejectsCatalogByteTextAndConditionOrderDrift}.</li>
 *   <li>Phase 2's exact case-major 32-row alternating schedule and schedule-content drift:
 *       {@code ToolCompatibilityPairedScheduleTest#locksTheExactCaseMajorAlternatingThirtyTwoRowOrder}
 *       and {@code #rejectsScheduleContentDriftEvenWhenItsDigestIsRecomputed}.</li>
 * </ul>
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
 *   <li>Paired fresh-output preflight, clean Git baseline, and drift before or between allocation:
 *       {@code ToolCompatibilityPromptMatrixPreflightTest#preflightsBothFreshOutputsAndTheCleanProtocolBeforeAnyAllocation},
 *       {@code #refusesRepositoryDriftBeforeEitherOutputIsAllocated}, and
 *       {@code #leavesTheFirstAllocatedDirectoryIncompleteWhenRepositoryDriftPreventsTheSecond}.</li>
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
 *   <li>Exact paired interleaving, only prompted system-message injection, one-attempt failure
 *       retention, and repository drift before a row or either manifest finalization:
 *       {@code ToolCompatibilityPromptMatrixExecutorTest#executesTheExactInterleavedScheduleAndInjectsOnlyThePromptedSystemMessage},
 *       {@code #retainsOneProviderFailureWithoutReplacingTheLogicalRowOrChangingTheSchedule},
 *       {@code #abortsBeforeTheNextLogicalRowWhenTheRepositoryDrifts}, and
 *       {@code #leavesBothConditionRunsIncompleteWhenTheRepositoryDriftsBeforeManifestFinalization}
 *       and {@code #invalidatesTheFirstManifestWhenRepositoryDriftPreventsTheSecondFinalization}.</li>
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
 *   <li>Paired condition three-artifact evidence, shared schedule metadata, and incomplete-run
 *       rejection:
 *       {@code ToolCompatibilityPromptMatrixEvidenceTest#writesAndVerifiesOneExactThreeArtifactConditionRunWithTheSharedSchedule}
 *       and {@code #rejectsIncompleteEvidenceAndRegeneratesOnlyADriftedSummary}.</li>
 *   <li>Phase 1 schema isolation from paired row fields:
 *       {@code ToolCompatibilityResultTest#keepsPhaseOneRowsFreeOfThePhaseTwoPairedExecutionFields}.</li>
 * </ul>
 *
 * <p>Phase 2 comparison gate:</p>
 * <ul>
 *   <li>One fully verified untreated/prompted pair accepts the schedule-derived, intentionally
 *       different execution positions and permits changed observed outcomes:
 *       {@code ToolCompatibilityPromptMatrixComparisonTest#acceptsOneVerifiedPairWithExpectedDifferentPositionsAndObservedOutcomes}.</li>
 *   <li>Model identity, framework, clean shared Git commit, dirty-worktree, and prompt-role
 *       mismatches:
 *       {@code ToolCompatibilityPromptMatrixComparisonTest#rejectsValidModelFrameworkAndGitParityMismatches}
 *       and {@code #rejectsReversedPromptRolesBeforeAnySemanticComparison}.</li>
 *   <li>Raw protocol, schedule, missing/equal/inconsistent paired-position, tool-catalog,
 *       semantic-oracle, setting, and policy tampering is rejected during strict offline
 *       verification before comparison:
 *       {@code ToolCompatibilityPromptMatrixComparisonTest#rejectsEveryLockedRawProtocolTamperBeforeComparison}.</li>
 *   <li>Exactly one baseline and one candidate CLI option:
 *       {@code ToolCompatibilityPromptMatrixComparisonRunnerArgumentsTest#requiresBothSavedRunsExactlyOnce}.</li>
 * </ul>
 *
 * <p>Phase 3 reference comparison:</p>
 * <ul>
 *   <li>Every ordered peer/reference pair, all four asymmetric pass outcomes, and retained
 *       deterministic failure diagnostics:
 *       {@code ToolCompatibilityCohortComparisonTest#comparesEveryPeerInOrderAndClassifiesAllFourPairedOutcomes}.</li>
 *   <li>Deterministic deployment-labelled report with no reference-ground-truth or
 *       backend-normalized inference:
 *       {@code ToolCompatibilityCohortComparisonTest#rendersOneDeterministicBoundedReportWithVisibleDeploymentIdentity}.</li>
 *   <li>Strict verification before comparison, no evidence mutation, and missing-evidence
 *       rejection:
 *       {@code ToolCompatibilityCohortComparisonTest#verifiesEvidenceBeforeComparisonAndLeavesEveryArtifactUnchanged}
 *       and {@code #rejectsMissingEvidenceBeforeRenderingAnyComparison}.</li>
 *   <li>Exactly one explicit saved cohort directory:
 *       {@code ToolCompatibilityCohortComparisonRunnerArgumentsTest#requiresExactlyOneTrimmedSavedCohortDirectory}.</li>
 * </ul>
 *
 * <p>Phase 3 capability frontier:</p>
 * <ul>
 *   <li>Unique recorded-size selection across all qualifying installed artifacts and visible
 *       reference role when the reference is selected:
 *       {@code ToolCompatibilityCohortFrontierTest#selectsTheUniqueSmallestRecordedArtifactAmongAllPassModels}
 *       and {@code #keepsReferenceRoleVisibleWhenItIsTheOnlyAllPassArtifact}.</li>
 *   <li>Explicit not-measurable outcomes for no all-pass model, missing qualifying size, and
 *       an ambiguous minimum:
 *       {@code ToolCompatibilityCohortFrontierTest#reportsNotMeasurableWhenNoModelPassesEveryLockedRow}
 *       and {@code #reportsNotMeasurableForMissingQualifyingSizeOrAmbiguousMinimum}.</li>
 *   <li>Strict verification before analysis, no evidence mutation, missing-evidence rejection,
 *       and exactly one explicit saved cohort directory:
 *       {@code ToolCompatibilityCohortFrontierTest#verifiesEvidenceBeforeAnalysisAndLeavesEveryArtifactUnchanged},
 *       {@code #rejectsMissingEvidenceBeforeRenderingAFrontier}, and
 *       {@code ToolCompatibilityCohortFrontierRunnerArgumentsTest#requiresExactlyOneTrimmedSavedCohortDirectory}.</li>
 * </ul>
 */
package com.setaccio.lab.toolcompat;
