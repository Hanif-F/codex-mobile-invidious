# Test catalog

[Documentation index](../README.md) · [Testing guide](../TESTING.md)

Generated inventory of declared `@Test` methods. Names describe intended coverage; this is not a run report. Ignore annotations below remain acceptance gaps until executed on a supported target.

## JVM tests

### AccountApiTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/AccountApiTest.kt) — Account Api declared scenarios; exact cases are enumerated in the test catalog.

- `registrationChallengeAndSignupUseCapturedInstanceWithoutBearer`
- `wrongCurrentPasswordDoesNotExpireTheBearerButRevocationDoes`
- `credentialsSessionsAndTokensRetainTheirContracts`
- `oldServersAndOldScopesExplainTheRequiredUpdate`
- `lateLoginCannotBindItsTokenToAnotherInstance`
- `guidedAndAdvancedPermissionsRequireAnExplicitValidSelection`
- `staleAccountOperationsNeverSendOrAcceptCredentialsForAnotherAccount`

### AiFilterTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/AiFilterTest.kt) — Ai Filter declared scenarios; exact cases are enumerated in the test catalog.

- `preferencesRoundTripSparsePatchesAndPausePreserveAllActions`
- `legacyChoicesAndInvalidCanonicalValuesFollowWebsiteRules`
- `hideWinsAndReplacementUsesBlocklistWithoutHidingLibrary`
- `scopedSearchesAndLibraryNeverUseDiscoveryActions`
- `apiUsesPublicBoundedQueriesAndParsesUnavailableLists`
- `batchesDeduplicatesExpiresAndInvalidatesWhenListVersionChanges`
- `overlappingSurfacesShareInflightChannelAndListWork`
- `unknownResponsesAreRetriedOnLaterLoadsAndFailuresKeepKnownMatches`
- `boundedPageWaitLeavesTheBackgroundRequestAlive`
- `pendingClassificationsRetryOnlyOnceAfterFiveSeconds`
- `aLateBatchCannotRestoreAnOlderListSnapshot`
- `pauseAndInstanceChangesCancelOldWorkAndDoNotScheduleDisabledLists`

### AudioSelectionTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/AudioSelectionTest.kt) — Preferred-audio parsing, API patches, regional matching, original/DRC fallback and manual restoration scenarios.

- `preferenceCatalogNormalizesAndKeepsRegionalChoicesWithoutCaptionVariants`
- `storedPreferencesRoundTripAndOnlyPatchAudio`
- `accountAudioPatchesAreAuthenticatedSparseAndReadsUseTheSameField`
- `originalsStableAndDubsIgnoreManifestOrderingAndSelectHighestBitrate`
- `regionalChoicesAreExactAndGeneralLanguagesPreferExactBeforeRegionalTracks`
- `duplicateItagsAreMatchedUsingLanguageProcessingAndBitrate`
- `processedDubsAreNotUsedForStableOriginalOrLanguagePreferences`
- `unknownOriginalMetadataRetainsDefaultAndUnsupportedTracksAreExcluded`
- `bitrateTiesRetainManifestOrderAcrossLanguageSortedMenus`
- `manualTrackSurvivesRenditionIdChangesAndFallbackWithoutChangingCapturedDefaults`
- `reusedRenditionIdCannotRestoreAnotherLanguage`
- `legacyManifestLanguageTagsMatchCanonicalPreferences`

### AvatarsTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/AvatarsTest.kt) — Avatars declared scenarios; exact cases are enumerated in the test catalog.

- `parsesModernLegacyAndMissingAvatarsAcrossModels`
- `normalizesAllSupportedSourcesToOneInstanceImage`
- `preservesQueryAndEncodedPathWithoutLeakingFragments`
- `refusesUnrelatedAndMalformedSources`
- `instanceChangesProduceDistinctCacheKeys`
- `initialsPreserveUnicodeAndUseIconForNonletters`
- `thinModeAndOwnerSuppressionApplyWithoutHidingOtherCreators`
- `detailedHistoryRetainsAvatarAlongsideSavedMetadata`
- `existingApiResponsesNeedNoAvatarMetadataRequests`
- `imageClientNeverFollowsRedirectsOrSendsCredentials`

### ChannelSpoilersTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/ChannelSpoilersTest.kt) — Channel rule normalization, expiry, sparse changes, lookup isolation and reveal state tests.

- `normalizationMatchesWebsiteAndKeepsInvalidDelaysOut`
- `sparseChangesAndDeletionPreserveOtherChannelsAndGuestNames`
- `exactExpiryUnknownAndFutureDatesRemainCorrect`
- `pendingRulesNeverExposeTitlesAndRevealModesAreExplicit`
- `lookupsBatchDeduplicateRetainMissesAndSkipKnownOrInactiveRules`
- `accountChangeRejectsLateLookupAndReveals`
- `publicLookupUsesBoundedCacheEndpointWithoutAuthentication`

### ChannelTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/ChannelTest.kt) — Channel declared scenarios; exact cases are enumerated in the test catalog.

- `metadataKeepsAdvertisedTabsAndIgnoresMalformedEntries`
- `supportedTabsUseStableOrderAndPreferUploads`
- `refreshPreservesAnAvailableSelectionAndFallsBackWhenItDisappears`
- `firstPagesOmitDefaultEmptyAndWhitespaceTokensOnBothEndpoints`
- `followUpPagesPreserveOpaqueTokensAndParseVideosAndEndOfList`
- `transientErrorsCanRetryTheSameSelectedEndpointFromItsFirstPage`

### ChaptersTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/ChaptersTest.kt) — Chapters declared scenarios; exact cases are enumerated in the test catalog.

- `manualDescriptionIncludesMinuteAndHourTimestamps`
- `shortLaterChaptersSortAndKeepTheFirstDuplicateWithUnicodeTitles`
- `unicodeWhitespaceAroundBulletsTimestampsAndTitlesIsPreserved`
- `malformedOverflowingMissingAndOutOfDurationEntriesAreRejected`
- `titlesRemainPlainTextAndApiUsesOnlyTheSuppliedDescription`
- `currentChapterUsesExactBoundariesWithoutInventingAnInitialChapter`
- `runtimeDurationAndLiveChangesRemoveUnavailableChapters`

### ClipsTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/ClipsTest.kt) — Clips declared scenarios; exact cases are enumerated in the test catalog.

- `nativeAndLegacyKeepPreciseBoundsAndSafeIdentity`
- `missingAndInvalidBoundsNeverBecomeFullVideoPlayback`
- `sourceAvatarSurvivesClipPlaybackHandoffAndRestoration`
- `unicodeTitlesAndLimitsMatchTheServer`
- `tenthSecondFieldsHandleLongArchivesAndRejectMalformedValues`
- `clipAndChannelLinksRetainOriginAndDoNotTreatClipsAsVideoLinks`
- `storyboardCuesResolveSpritesAndIgnoreMalformedBlocks`
- `apiListsCreatesAndDeletesWithExactFieldsAndScopes`
- `missingScopeExplainsTokenRenewalAndStaleContextsSendNothing`
- `storyboardReadsArePublicAndLimitedToTheInstance`
- `creationRejectsInvalidDraftsBeforeSendingAndDropsLateAccountResponses`

### CommentsTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/CommentsTest.kt) — Comments declared scenarios; exact cases are enumerated in the test catalog.

- `modernCommentsPreserveRichContentMetadataAndReplies`
- `legacyCommentsAndMissingOptionalFieldsRemainReadable`
- `linksResolveNativeChannelsVideosTimestampsAndExternalTargets`
- `commentsApiAlwaysUsesPublicYoutubeRequestsAndEncodesOpaqueTokens`
- `commentsApiRejectsAResponseAfterTheInstanceChanges`
- `entryDoesNotFetchUntilOpenedAndCloseReopenKeepsTheListAndPosition`
- `paginationRetainsRowsCountAndFailedCursorAndDeduplicatesOverlap`
- `repliesHaveIndependentPagesErrorsAndCachedPositions`
- `duplicateLoadingIsIgnoredAndLateSortResponsesCannotOverwriteNewest`
- `videoAccountAndVisibilityChangesDiscardPendingRepliesAndCloseTheDrawer`
- `repeatedContinuationStopsRatherThanOfferingAnEndlessPage`

### CommunityTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/CommunityTest.kt) — Community declared scenarios; exact cases are enumerated in the test catalog.

- `channelTabsNormalizeAliasesFollowWebsiteOrderAndDeferClips`
- `channelHeaderParsesOptionalMetadataAndSelectsAWideBanner`
- `postsKeepRootOwnerAndDisplayMetadataSeparateFromCommentReplies`
- `everyAttachmentTypeParsesWithoutDiscardingThePost`
- `proxyImagesKeepMediaSizeAndNeverUseAnUntrustedOrigin`
- `postLinksResolveDirectLegacyAndHandleFormsAndRemainSeparateFromVideoLinks`
- `videoTabsSendSortAndOpaqueTokensAsPublicRequests`
- `playlistTabsUsePlaylistResultsAndOnlyPlaylistsHaveSorting`
- `postsRelatedChannelsDetailsAndCommentsUseTheExistingServerContracts`
- `unavailablePostGetsAReadableFailure`
- `postApiDiscardsResponsesAfterAnInstanceChange`
- `videoAndPostCommentControllersRetainIndependentTargetsAndPositions`
- `restoredPostRepliesRetryPendingLoadWithoutLosingParentOrMainPosition`
- `specializedFirstPagesOmitBlankTokensAndPaginationPreservesOpaqueTokens`
- `latePostCommentResponsesCannotCrossTargetsSortsOrAccounts`
- `postReplyFailuresPreserveTheirCursorAndRepeatedContinuationsTerminate`

### ContentLinksTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/ContentLinksTest.kt) — Content Links declared scenarios; exact cases are enumerated in the test catalog.

- `aliasesPreservePreciseTimesAndNormalizeOnlyYoutubeIndexes`
- `unsafeOriginsPathsAndOverflowAreRejected`
- `sharedAndRichLinksResolveChannelsTabsPostsAndHashtags`
- `nullableOptionsKeepExplicitFalseAndNeverChangeSavedDefaults`
- `boundsClampWithoutChangingAbsoluteCoordinates`
- `sharesUseSourceOccurrencesForDuplicatesAndOmitContextForInsertedVideos`
- `onlyModeSpeedAndProxyCarryToOtherOccurrences`
- `publicResolutionAndHashtagsUseSelectedInstanceAndEncodedValues`
- `delayedResolutionCannotCrossInstanceChanges`

### ContentVisibilityTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/ContentVisibilityTest.kt) — Content Visibility declared scenarios; exact cases are enumerated in the test catalog.

- `membershipUsesBooleanMetadataAndNeverInfersFromPremiumOrTitle`
- `memberPreferenceDefaultsOffRoundTripsAndPatchesOnlyChangedFields`
- `blocksFilterOnlyDiscoverySearchAndRecommendations`
- `membershipFiltersEveryListExceptHistoryAndSearchCanOverrideBothFilters`
- `searchOverrideRetainsExplicitFalseAndResetUsesCurrentDefault`
- `hiddenPagesAndDuplicatePlaylistOccurrencesPreservePaginationAndOriginalData`
- `blockApiUsesBearerJsonAndPublicSearchUsesCorrectSortWithoutAuthorization`
- `oldServerAndScopeFailuresExplainTheRequiredUpdate`
- `confirmedSnapshotSurvivesOfflineFailureButFailedWritesDoNotChangeIt`
- `delayedRefreshCannotUndoSuccessfulBlockOrUnblock`
- `contextChangesRejectDelayedReadsAndWritesAndRestoreOnlyOwnedSnapshots`
- `mutationResponseCannotPublishIntoAnotherAccount`
- `guestsNeverSendBlockListReadsOrInheritAccountSnapshot`
- `successfulActionAfterFailedInitialReadIsStillSavedForOfflineFiltering`
- `offlinePublicCacheRetainsRawMembershipAndCanBeRefilteredWithoutAnotherRequest`

### CoreTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/CoreTest.kt) — Core declared scenarios; exact cases are enumerated in the test catalog.

- `playerSeekingStaysWithinAvailableDuration`
- `offlineSnapshotsExpireAndNeverCrossAccountKeys`
- `linksAndTimestamps`
- `explicitTimeOverridesResumeAndFinishedVideosRestart`
- `parsersKeepUnavailableHistoryAndCaptionVariants`
- `recommendationsKeepTheFirstRankedVideoWithoutCollapsingPlaylistOccurrences`
- `rejectInsecureAndCredentialBearingInstances`
- `nativeSignInAndBearerOnlyForAuthenticatedRequests`
- `apiDoesNotForwardCredentialsOnRedirectAndInvalidatesExpiredSessions`

### DeArrowTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/DeArrowTest.kt) — De Arrow declared scenarios; exact cases are enumerated in the test catalog.

- `titleAndIdentityValidationMatchesServerAndKeepsOriginalVoteRules`
- `parsingPreservesProposalOrderAndPreferencePatchesContainOnlyChanges`
- `lookupsDeduplicateAndBoundConcurrencyToFour`
- `failuresAndMissingTitlesKeepOriginalUntilExplicitInvalidation`
- `lateResponseCannotCrossInstancesOrReplaceANewerLookup`
- `nativeContractUsesBearerAndJsonBooleansWithoutSendingIdentityOnReads`
- `writeTimeoutIsNotRetriedAndAnOldContextCannotWriteForANewAccount`
- `oldServersAndOldTokensHaveActionableErrorsWithoutSigningOut`
- `privateIdentityImportCannotFollowARedirectEvenWithAnInjectedClient`

### DeviceProfilesTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/DeviceProfilesTest.kt) — Device Profiles declared scenarios; exact cases are enumerated in the test catalog.

- `identitiesNormalizeInstancesAndIgnoreSessionsButDistinguishReusedNames`
- `savesSurviveRecreationAndRemainIndependentForGuestAndTwoAccounts`
- `guestAudioPreferencesSurviveRecreationAndStayWithinTheirInstanceAndProfile`
- `legacyUnownedValuesGoOnlyToGuestsAndMigrationIsRepeatable`
- `existingDestinationWinsAndOtherInstanceGuestPreferencesRemainSeparate`
- `failedMigrationAndTransferDoNotDeleteSourcesOrAdvanceMarker`
- `legacyOwnedPositionsIncludingExpiredSessionNeverBecomeGuestData`
- `unreadableSavedSessionCannotMakeLegacyAccountProgressGuestData`
- `malformedLocalPreferencesDropAccountReferencesAndUnsupportedValues`
- `guestSponsorBlockDeltasPreserveOtherOverridesAndResetOnlyOneChannel`
- `guestBlockingPersistsAndDoesNotMakeAuthenticatedRequests`
- `authenticationParsesOptionalProfileAndPreferencesMetadataIsSeparate`
- `delayedProfileMetadataCannotClaimTheNextSession`

### DiscoverySearchTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/DiscoverySearchTest.kt) — Discovery Search declared scenarios; exact cases are enumerated in the test catalog.

- `discoverySendsCategoryAndRegionOnlyToTrending`
- `eachSearchTypeUsesItsOwnFiltersWithoutAuthorization`
- `mixedParserPreservesOrderChannelMetadataAndMixSeed`
- `missingOptionalChannelMetadataStaysAbsentAndInvalidIdsAreSkipped`
- `legacyResultsWithoutTypeAreRecognized`
- `rawPagesGovernPaginationIncludingUnsupportedAndRepeatedPages`
- `mergingDeduplicatesWithinTypesWithoutRegrouping`
- `visibilityFiltersAllTypesAndOnlyAppliesMembershipToVideos`
- `countrySearchSupportsLocalizedNamesCodesAndDefaultCategory`
- `delayedDiscoveryAndSearchCannotEscapeAnAccountSwitch`

### DisplayFormatsTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/DisplayFormatsTest.kt) — Display Formats declared scenarios; exact cases are enumerated in the test catalog.

- `compactCountsRoundAcrossUnitsWithoutLosingLargeIntegerPrecision`
- `localeChangesSeparatorsWithoutChangingSuffixesOrSourcePrecision`
- `sizesDurationsAndBitratesUseConsistentUnitsAndHandleZero`
- `apiCountsDistinguishZeroUnknownAndFractions`
- `publicationPlaceholdersCannotOverrideValidationWithRelativeText`
- `calendarDatesDoNotShiftAndTimestampsUseTheChosenTimezone`

### DownloadsTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/DownloadsTest.kt) — Downloads declared scenarios; exact cases are enumerated in the test catalog.

- `selectionsRequireMediaAndKeepExactIdentities`
- `repeatDownloadsCreateIndependentEntriesAndRoundTripAllAssets`
- `restrictedCatalogAndCombinedOrInvalidChoicesCannotBeSelected`
- `exportConsentBelongsToOnePendingDestinationAndSelection`
- `progressIncludesAllSelectedFilesButNotArtworkAndKeepsUnknownTotalsIndeterminate`

### HistorySearchTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/HistorySearchTest.kt) — History Search declared scenarios; exact cases are enumerated in the test catalog.

- `historyDatesStaySeparateFromPublicationAndRetainSavedMetadata`
- `unavailableHistoryRetainsIdsAndUnknownDates`
- `rollingGroupsAreDisjointAcrossYearAndLeapBoundaries`
- `organizedCalendarComesFromServerRatherThanDeviceClock`
- `oldServerArraysRetainViewingAndDeclareSearchUnsupported`
- `malformedOrganizedResponsesDoNotPretendToBeLegacyHistory`
- `mixedChannelResultsUseOriginalPageSizeForPagination`
- `scopedQueriesAreEncodedAndOnlySubscriptionsUseBearer`
- `historyRequestsOrganizedSearchAndPreservesExplicitEnd`
- `missingSubscriptionRoutesAndOldTokensExplainUpgradeWithoutLoggingOut`
- `scopedRequestsRejectCapturedContextsAfterAccountChanges`
- `delayedScopedReadsNeverReturnThePreviousAccountsResults`

### LiveChatTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/LiveChatTest.kt) — Live Chat declared scenarios; exact cases are enumerated in the test catalog.

- `availabilityIsOptionalAndExcludesLiveAndUpcoming`
- `chunkParsingValidatesEnvelopeAndRetainsKindsAndUnicode`
- `appearanceBoundsInvalidGeometryAndPreservesNativeDefaults`
- `besideWidthAllowsTenPercentAndSurvivesStorageWithoutChangingOtherDefaults`
- `channelIdsAreHiddenForNewSettingsAndExplicitOlderChoicesArePreserved`
- `filtersMatchIdsHandlesWordsAndSafeRegexCaseInsensitively`
- `importedUnsupportedPatternsAreSkippedAndDoNotRewritePreferences`
- `unchangedUnsupportedImportsCanStaySavedWhileOtherPreferencesChange`
- `chatFieldsNeverLeakIntoGeneralPreferencePatches`
- `requestsStartOnDemandAndUsePlayerPositionRatherThanWallTime`
- `offsetsDelayOrAdvanceChatAndClampPublicRequests`
- `continuationOverlapReplacementsRemovalsAndEndAreApplied`
- `prefetchStopsAheadAndSparseChunksContinue`
- `errorsKeepRowsAndRetryTheSameCursorAndUnavailableDoesNotRetry`
- `repeatedCursorsAndCyclesStopPagination`
- `suspendAndCloseStopRequestsButReopenReusesCachedRows`
- `eachOccurrenceStartsClosedEvenForTheSameVideoAndTimingRemainsPerVideo`
- `readingPositionFreezesUntilReturningToPlaybackAndWindowIsBounded`
- `recreationAndPanelRestorationPreserveManualReadingPosition`
- `replacementsKeepAManuallyScrolledWindowInTimestampOrder`
- `oldestSeekSegmentsAreEvictedAndReloaded`
- `continuationThrottleAndCancellationPreventEarlyOrHiddenRequests`
- `timingReadFromBeforeRecreationCannotReplaceTheNewRead`
- `cachePruningRequiresRefetchOutsideRetainedCoverage`
- `lateSeekAndOccurrenceResponsesCannotPublish`
- `lateTimingReadCannotOverwriteManualEditAndFailedWritesRetry`
- `failedTimingReadsRetryWithoutBecomingWrites`
- `accountAndInstanceChangesInvalidateEvenUncancellableResponses`
- `publicApiEncodesCursorsWithoutSendingAccountCredentials`
- `authenticatedApisUseDedicatedSparseWritesAndExplainOldScopes`
- `apiRejectsResponsesAfterContextChanges`

### NetworkReliabilityTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/NetworkReliabilityTest.kt) — Network Reliability declared scenarios; exact cases are enumerated in the test catalog.

- `explicitRefreshReportsFailuresWithoutDiscardingTheNormalOfflineFallback`
- `cancellingAResponseBodyReleasesTheCallAndDoesNotPublishOrCache`
- `oldSessionSuccessAndFailureCannotChangeSnapshotsOrOfflineStatus`
- `successfulHtmlDoesNotPoisonTheLastUsableFeed`

### PlaybackQueueTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/PlaybackQueueTest.kt) — Playback Queue declared scenarios; exact cases are enumerated in the test catalog.

- `queueVisibilityRequiresAnActiveExplicitSessionRatherThanAnItemCount`
- `duplicatesAndOverlappingWindowsKeepLocalEdits`
- `removingCurrentRetainsItUntilCompletionAndFindsSuccessor`
- `navigationSkipsUnavailableHiddenAndRemovedOccurrences`
- `previousAndFiniteEndDoNotWrapImplicitly`
- `mixPagesFollowQueuedTailAndFinitePagesPrecedeIt`
- `mixContinuationAddsOccurrencesAndRejectsEmptyOrRepeatedWindows`
- `resolvingInitialSourceKeepsPlayNextAfterTheSelectedOccurrence`
- `previousLoadsMissingWindowsAndRecommendationsReevaluateBlocking`
- `allWebAutoplayCombinations`
- `repeatWrapDoesNotJumpAcrossUnloadedMiddlePages`
- `linksKeepContextNormalizeIndexesAndRejectOtherHosts`
- `nextAndLoopDefaultsAndSparseSharedDeltas`
- `publicPrivateAndMixEndpointsAndCredentials`
- `creationAndFailedSaveRetryUseSamePlaylistAndStableRemovalId`
- `staleReadsAndWritesDoNotReachNetwork`
- `delayedResponseIsRejectedAfterReturningToSameAccountAndInstance`

### PlaybackVisibilityTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/PlaybackVisibilityTest.kt) — Playback Visibility declared scenarios; exact cases are enumerated in the test catalog.

- `visiblePlaybackAndBufferingKeepAwakeEvenWithoutAdvancingFrames`
- `pauseIdleCompletionAndErrorsAllowScreenTimeout`
- `audioOnlyModeAllowsTimeoutAndReturningToVideoRestoresWake`
- `hiddenPlaybackAllowsTimeoutAndVisibilityRestoresWake`
- `closedMissingAndStaleMediaCannotHoldTheScreenAwake`

### PlayerGesturesTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/PlayerGesturesTest.kt) — Player Gestures declared scenarios; exact cases are enumerated in the test catalog.

- `directionsAreLockedToTheDominantAxisAndUnsupportedSwipesHaveNoAction`
- `verticalDistanceAndDirectionalFlingThresholdsAreIndependentOfViewport`
- `fullscreenRequires120DpRegardlessOfSpeedAndRejectsTopEdgeStarts`
- `sidewaysDismissalWorksInEitherDirectionAndRequires35PercentOrADirectionalFling`
- `fullscreenCollapsesToWatchBeforeWatchCollapsesToMini`
- `fullscreenWaitsForReleaseAndOnlyCompletedSwipesAnimate`
- `interruptedFullscreenGestureCannotFinishLater`
- `completedAndCanceledDragsSettleWithoutClosingOrLosingPresentation`
- `interruptionAndNavigationCancelAnInFlightDismissal`
- `reversingRestoreOrCollapseKeepsTheDisplayedFractionAndPresentation`

### PlaylistRssTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/PlaylistRssTest.kt) — Playlist Rss declared scenarios; exact cases are enumerated in the test catalog.

- `subscribedNativePlaylistIsReadOnlyAndRetainsSourcePrivacy`
- `typedSearchPreservesMixSeedsAndSendsNoVideoFilters`
- `channelPlaylistContinuationAndSortingAreEncoded`
- `subscribeAndUnsubscribeUseDedicatedRoutesWithoutDeletingTheSource`
- `ownerUpdatesReachDetailsAndAuthenticatedMixesPreserveTheirSeed`
- `rssLinksUseSelectedInstanceAndRejectPrivateOrForeignUrls`
- `rssAndXmlExportsNeverPutTheNativeTokenIntoTheSharedContent`
- `staleReadsAreDiscardedAfterAnAccountChange`
- `missingEndpointsAndOldScopesGiveSeparateExplanations`

### PreferencesTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/PreferencesTest.kt) — Preferences declared scenarios; exact cases are enumerated in the test catalog.

- `codecPreferencesRoundTripAndProduceSparseAccountPatches`
- `webPreferencesParseAndRoundTrip`
- `sparseDeltasPreserveOtherSettingsAndNestedSponsorBlock`
- `captionsUsePriorityAndAvailableTracks`
- `homeAndNavigationKeepSupportedDestinationsReachable`
- `nativeHomepageChangesPreserveTheWebsiteFeedMenu`
- `malformedPreferencesHaveUsableDefaults`
- `feedAndHistoryRespectServerPageSizesAndNotificationFilter`
- `streamProxyPreferenceReachesVideoApiAndSharedPatchesStaySparse`

### SearchAssistanceTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/SearchAssistanceTest.kt) — Search assistance history, isolation, debounce, cancellation, failures and public API contracts.

- `recentsTrimBoundDeduplicateAndExcludeLinks`
- `entryCancelSubmitAndProfilePersistenceAreIndependent`
- `debounceCancellationAndOlderResponsesCannotReplaceTheDraft`
- `failuresAndOptOutNeverPreventManualSubmission`
- `suggestionsUseTheInstanceRegionAndNoAuthorization`

### SeekAccumulatorTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/SeekAccumulatorTest.kt) — Seek Accumulator declared scenarios; exact cases are enumerated in the test catalog.

- `repeatedTapsKeepFrozenPositionAndExtendDeadline`
- `oppositeDirectionReplacesRatherThanSubtractsAndRetainsAnchor`
- `pausedPlaybackStaysPausedAndTargetsAreBounded`
- `cancellationAndMediaReplacementCannotCommitStaleSeeks`

### SessionTimelineTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/SessionTimelineTest.kt) — Session Timeline declared scenarios; exact cases are enumerated in the test catalog.

- `hlsVodCanBecomeASessionPlaylistWithoutInventingLiveState`
- `liveWindowTimingAndConfigurationStayIntact`
- `queueMetadataProducesValidSessionDurationsAtNumericBoundaries`

### SponsorBlockTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/SponsorBlockTest.kt) — Sponsor Block declared scenarios; exact cases are enumerated in the test catalog.

- `defaultsAndMalformedPreferenceFallbacks`
- `rangesFilterBadEntriesDeduplicateAndSort`
- `channelInputAcceptsOnlyCanonicalPaths`
- `inheritancePreservesFalseAndGlobalColors`
- `sparseCategoryAndChannelPatch`
- `autoMergesTouchingAndOverlappingRangesAndReplaysManually`
- `manualOverlapOffersEarliestEndAndDismissResetsAfterLeaving`
- `markerAndDisabledModesNeverSeekOrPrompt`
- `clampsDurationAndWaitsForKnownDuration`
- `sessionResetAndSettingsUpdatesHaveDistinctReplaySemantics`
- `playbackStateRoundTripRetainsActiveAndNotice`
- `publicSegmentRequestHasNoCredentialsAndPropagatesFailure`
- `staleAccountContextPreventsRequest`

### StreamControlsTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/StreamControlsTest.kt) — Stream Controls declared scenarios; exact cases are enumerated in the test catalog.

- `parsesOptionalMetadataAndNumericStrings`
- `detailsRetainAdaptiveAndProgressiveFormats`
- `duplicateAudioItagsUseLanguageAndStableVariant`
- `ranksResolutionThenFpsThenBitrateAndShowsCodecAndNumericRates`
- `qualityDefaultsMatchWebIncludingUnavailableLowResolution`
- `onlySupportedRepresentationsAreOffered`
- `explicitSelectionAndAutoAreIndependentOfAdaptiveSelectedFlags`
- `reloadMatchesIdentityAndRejectsVanishedOrAmbiguousRepresentations`
- `originalStableDubbedAndUnlabeledAudioRemainSelectable`
- `audioTiersAndFileSizeUseKnownValuesOnly`
- `explicitDubMetadataTakesPrecedenceOverManifestDefaultRole`
- `smallMenuGroupsRetainEveryRepresentationIncludingDuplicates`
- `largeMenusKeepCodecExtremesWithoutPruningTheFullCatalog`
- `equalExtremesUseOriginalOrderAndUnknownRatesDoNotBecomeLowVariants`
- `spareMenuSlotsPreferNamedOtherCodecsAlphabetically`
- `menuGroupsUseRoundedFpsAndSeparateResolutions`
- `presetsPickResolutionBeforeCodecAndRespectHighestAndLowestRanking`
- `autoModeSurvivesSingleCodecCandidateAndNewerChoicesReplaceManualState`
- `reloadMatchesVideoIdentityAndVanishedManualChoicesReturnToCapturedDefaults`

### SubscriptionsTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/SubscriptionsTest.kt) — Subscriptions declared scenarios; exact cases are enumerated in the test catalog.

- `channelNamesSortIgnoringCaseAndEqualNamesKeepDistinctIds`
- `searchMatchesNameSubstringsImmediatelyWithoutSearchingOtherFields`
- `refreshKeepsLoadedChannelsAndQueryThroughErrorsAndRetry`
- `newestRefreshWinsEvenWhenCanceledTransportFinishesLate`
- `accountServerAndGenerationChangesClearChannelsQueryAndLateErrors`
- `guestRefreshMakesNoAuthenticatedRequest`
- `duplicateTapsAreBlockedAndConfirmedChangeSurvivesRefreshFailure`
- `failedWriteRetainsMembershipAndAllowsRetryWithoutGuessingUnknownState`
- `oldAccountWriteCannotRepopulateChannelsOrErrorsAfterReset`
- `allSortsUseTheirOwnMetricAndBreakTiesByNameAndId`
- `sortChoicePersistsWithoutFetchAndSurvivesFailureAndLegacyFallback`
- `directoryParserAcceptsZeroAndUnknownButRejectsMalformedStats`
- `directoryApiRequestsStatsAndOnlyFallsBackForMissingHistoryPermission`
- `subscriptionApiUsesCapturedBearerContextForReadsAndChanges`
- `apiRejectsResponsesFromAFormerSessionAndRejectsStaleWritesBeforeSending`

### VideoGeometryTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/VideoGeometryTest.kt) — Video Geometry declared scenarios; exact cases are enumerated in the test catalog.

- `watchResizingInterpolatesFittedHeightsAndLeavesSmallerVideosAlone`
- `commentsOverrideResizingAndProgressStaysWithinBounds`
- `portraitVideosUseMoreWatchSpaceAndRespectCommentsCap`
- `squareAnamorphicUnknownAndInvalidDimensionsRemainUsable`
- `pipUsesTheActualRatioWithinPlatformLimits`

### VideoInformationTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/VideoInformationTest.kt) — Video Information declared scenarios; exact cases are enumerated in the test catalog.

- `suppliedMetadataPreservesFalseZeroAndMusicCredits`
- `oldAndMalformedOptionalFieldsRemainUnknown`

### WatchPlayerScrollTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/WatchPlayerScrollTest.kt) — Watch Player Scroll declared scenarios; exact cases are enumerated in the test catalog.

- `first24DpScrollTheListAndTheNext96DpResizeContinuously`
- `crossingTheTopRegionInOneScrollLeavesTheCorrectRemainderForTheList`
- `upwardBrowsingOnlyExpandsWhenReturningNearTheTop`
- `viewportClampingOnAShortPageCannotRestartOrReverseResizing`
- `largeFlingDeltasRespectTheLimitsAndRestoredProgressCanReverse`

### WatchedStateTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/WatchedStateTest.kt) — Watched State declared scenarios; exact cases are enumerated in the test catalog.

- `historyRevisionAdvancesForRepeatWatchesButNotPlaybackPositions`
- `snapshotParsesIndependentStateAndRejectsMalformedEntries`
- `barsMatchWebRoundingMinimumAndCompletionThresholds`
- `historyMembershipAndProgressHaveDistinctAccessibleDescriptions`
- `liveAndUnknownDurationsNeverInventPartialProgress`
- `bulkApiUsesExistingBearerContractWithoutCookies`
- `staleApiContextsCannotSendHistoryOrPositionRequests`
- `refreshCoalescesWhileKeepingExistingIndicatorsAndRetainsThemAfterFailure`
- `failedInitialReadDoesNotPreventAutomaticUpdatesOrSuccessfulRetry`
- `delayedReadCannotUndoNewHistoryAndPositionWrites`
- `changedAccountsClearImmediatelyAndCanRefreshBeforeOldResponseFinishes`
- `historyRemovalAndClearingWinAgainstOlderBulkSnapshots`
- `readDuringPendingPositionWriteKeepsTheLatestDeviceProgress`
- `guestsUseOnlyTheirScopedDevicePositionsAndNeverCallAccountApis`
- `guestCompletionRemovesProgressWithoutCreatingWatchedHistory`
- `accountCompletionRetainsWatchedBadgeAndUsesPositionDelete`
- `historyRemovalKeepsPlaybackPositionAndOnlyDeletesHistory`
- `clearHistoryRemovesBothKindsOfStateAndDropsPreviouslyQueuedSaves`
- `disabledResumeHidesProgressButKeepsRecordedHistory`
- `disablingResumeDuringWriteCannotRestoreDeviceProgress`
- `failedHistoryMutationsLeaveIndicatorsIntactAndFailedPositionKeepsLocalFallback`
- `failedDeviceClearPreservesProgressAndCanRetryWithoutStoppingObservers`

### AppThemeTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/ui/AppThemeTest.kt) — All twelve palettes: exact opaque neutral roles and enabled text/control-boundary contrast.

- `everyEnabledTextAndBoundaryRolePassesInAllTwelvePalettes`
- `accentsNeverChangeNeutralSurfacesAndUnknownIdsResolveBlue`

### AppWindowLayoutTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/ui/AppWindowLayoutTest.kt) — Contiguous viewport selection around vertical/horizontal occluding hinges and resized windows.

- `verticalHingeUsesLargerContiguousPane`
- `horizontalHingeAndResizedWindowsStayClear`
- `FlatWindowUsesWholeViewport`
- `keyboardCanMoveAnOccludedEditorToTheClearPane`
- `ordinaryWindowsKeepTheirExistingImeInsetOwner`

### PreferenceSaveControllerTest

[Source](../../android/app/src/test/java/net/wingress/mobivious/ui/PreferenceSaveControllerTest.kt) — Automatic-save ordering, newer intent preservation, debounce, retry/revert and context isolation.

- `slowResponseNeverOverwritesNewerIntentAndIndependentFieldsRemainOrdered`
- `failuresKeepIntentRetryUsesLatestAndRevertDoesNotWrite`
- `invalidTextNeverReachesSinkAndBlurOrBackgroundFlushesValidDebounce`
- `contextSwitchDiscardsSensitiveIntentsAndLateResultsCannotPublish`
- `returningToConfirmedValueWhileAnOlderWriteRunsStillRestoresIt`
- `anOlderFailureCannotTurnTheNewerQueuedValueIntoAnError`
- `revertingInvalidOrUnsentTextDoesNotSendItDuringLifecycleFlush`

## Android device tests

### AccountNavigationSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/AccountNavigationSmokeTest.kt) — Account Navigation Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `autofillSignInTargetsBothFieldsRegardlessOfFocus`
- `autofillSignupKeepsNewPasswordsAndConfirmationSeparate`
- `autofillHintsAndSecretsResetWhenSwitchingAuthenticationModes`
- `delayedAccountPreferencesKeepThePageOpenedWhileTheyLoad`
- `delayedAccountPreferencesApplyDefaultHomeWithoutNavigation`
- `delayedAccountPreferencesKeepExplicitDiscoveryAndPlayback`
- `fourRootsGuestSettingsAndSearchCancellationAndReturn`
- `contextualSignupReturnsToLibraryAndCredentialChangesRetainSignIn`
- `sessionsTokenCreationAndDeletionConfirmation`
- `realPortraitAndOtherShapesUpdateSizingFullscreenAndPipWithoutRestart`
- `decodedQueueShapeChangesAndActivityRecreationKeepTheServicePlayer`

### ActionControlsPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/ActionControlsPresentationTest.kt) — Action Controls Presentation declared scenarios; exact cases are enumerated in the test catalog.

- `nestedOverflowClosesBeforeActionAndBackDoesNotActivateParent`
- `openMenuResetsAcrossEntityAndAccountChanges`
- `selectorKeepsLabelAndValueAndCannotOpenWhileDisabled`
- `darkNarrowLayoutKeepsControlsAccessibleAtDoubleFontSize`
- `lightWideLayoutKeepsControlsAccessibleAtDoubleFontSize`

### AiFilterSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/AiFilterSmokeTest.kt) — Ai Filter Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `guestCanEditWhilePausedSaveEnableAndPauseWithoutLosingChoices`
- `fullyHiddenSearchPageStillLoadsTheNextUnfilteredPage`
- `warningsRenderAcrossThemesAndTextOnlyWithoutFetchingOriginalImages`
- `accountLibraryScopedSearchClipsAndQueueOnlyReplaceAndPreserveOccurrences`
- `backgroundAutoplaySkipsHiddenRecommendationsAndDirectPlaybackStillWorks`
- `unavailableServerExplainsCompatibilityAndKeepsVideoCardsAccessible`
- `failedAccountSaveRetainsDraftAndListStatusExplainsStaleData`

### AppControlPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/AppControlPresentationTest.kt) — Native control target, semantics, state and surface presentation scenarios. Exact cases appear in the test catalog.

- `subscriptionStateAndExplicitActionRemainClearWithLargeText`
- `settingsToolbarAndSegmentedControlsFitNarrowLargeTextAndKeepRowsReachable`

### AppFoundationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/AppFoundationTest.kt) — Native caption window transparency and dialog Back/cancel behavior. Exact cases appear in the test catalog.

- `nativeCaptionsIgnoreBlackCueWindowsAndBackgroundSpans`
- `pressCancellationAndRapidRepetitionKeepOneSemanticAction`

### AppNavigationTestActions

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/AppNavigationTestActions.kt) — Shared native root/search navigation, automatic-save flush and viewport test actions. Exact cases appear in the test catalog.

Test support/helper file; no declared `@Test` methods.

### AppSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/AppSmokeTest.kt) — App Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `failedInitialPlaybackRetryPreservesAutoplayAndPausedIntent`
- `repeatedRecommendationsRenderOnceAndKeepPlaybackUsable`
- `unifiedControlsGesturesAndFullscreen`
- `refreshPreservesPausedAndPlayingSettings`
- `hlsExplicitResumeCaptionsAndAudioOnly`
- `accountBrowsingAndServicePlayback`

### AppThemePresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/AppThemePresentationTest.kt) — Twelve appearance/accent galleries, retained edit/menu state and local preference migration. Exact cases appear in the test catalog.

- `twelvePalettesRenderOpaqueComponentsAndKeepMenusAndFocusedEdits`
- `appearanceIgnoresRetiredVisualPreferencesAndKeepsAccent`

### AppUiSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/AppUiSmokeTest.kt) — Integrated opaque browse, watch, tools and account return journeys. Exact cases appear in the test catalog.

- `directFeedsAndTopSearchPreserveTheirOrigin`
- `youOwnsGuestSettingsAndAppearance`
- `opaqueSurfacesKeepActionsReachableAcrossLightAndDarkPages`
- `systemAppearanceUsesNeutralSurfacesAndHonorsAnExplicitLightChoice`
- `watchPreservesFourAspectRatiosAndPlaybackThroughMiniSearchAndSettings`
- `wideRailAndLargeTextRemainReachable`
- `systemReducedMotionKeepsNavigationAndMinimizationUsable`
- `guestTitleContributionReturnsToWatchAfterSignInCancellation`
- `miniPlayerExposesRetryAfterARealStreamFailureWithoutLosingItsOccurrence`
- `watchActionsPanelsAndFullscreenRemainUsableAcrossAppearances`
- `watchNarrowLargeTextAndWideLayoutsKeepActionsReachable`

### AppWindowPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/AppWindowPresentationTest.kt) — Physical usable-pane bounds in LTR/RTL and native opaque control drawing on a software Canvas in both appearances. Exact methods appear in the test catalog.

- `usablePaneKeepsPhysicalBoundsWhenLayoutDirectionChanges`
- `nativeControlsDrawIntoASoftwareCanvasInBothAppearances`

### AvatarsPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/AvatarsPresentationTest.kt) — Avatars Presentation declared scenarios; exact cases are enumerated in the test catalog.

- `darkLargeFontHeaderAndAuthorKeepOneNavigationAction`
- `lightThinModeSuppressesHeaderSubscriptionCommentAndHeartImages`
- `subscriptionCommentsAndCompactAuthorUseTheirIntendedSizes`
- `narrowWatchIdentityAtLargeFontKeepsSubscribeBelowTheAuthor`

### AvatarsSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/AvatarsSmokeTest.kt) — Avatars Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `browsingChannelHeaderAndWatchReuseImagesWithoutExtraMetadataLookups`
- `channelHeaderHasAFlatThemeBackgroundWithBannerMissingBannerAndThinMode`
- `commentsRepliesAndCreatorHeartsDisplayAvatars`
- `subscriptionsHistoryAndPlaylistCreatorsUseExistingPayloads`
- `thinModeMakesNoAvatarRequestsAndFailuresKeepAuthorsUsable`

### ChannelControlsPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/ChannelControlsPresentationTest.kt) — Adaptive channel sorting, inline search focus/blur, draft restoration, selection and explicit submission checks.

- `emptySearchCollapsesOnBlurButDraftAndSubmittedQueriesRemain`
- `sortingAdaptsWhileFocusedAndKeepsTheQueryAndSelection`
- `restoredDraftStaysExpandedAndClearReturnsToTheSearchIcon`
- `sortPillsUseSmallerLabelsThanPageTabsAndKeepTheirTouchTargets`

### ChannelDescriptionPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/ChannelDescriptionPresentationTest.kt) — Channel Description Presentation declared scenarios; exact cases are enumerated in the test catalog.

- `completeTextScrollsToTheFinalLineAndCloseAndBackKeepTheChannelPage`
- `narrowDarkLargeFontSheetKeepsItsCloseButtonAndAllTextAccessible`
- `blankDescriptionsHaveNoReadButton`
- `shortDescriptionsAlsoOpenAndTheSheetCanBeSwipedAway`

### ChannelSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/ChannelSmokeTest.kt) — Channel Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `shortsHideEpochDatesAndUnknownDurationsWithoutDetailRequests`
- `channelNavigationSwitchingAndIndependentPagination`
- `channelTabsRetainTheirSortLoadedPagesAndVisiblePositionWithinTheAccount`
- `fullDescriptionUsesLoadedMetadataAndClosesWhenLeavingTheChannel`
- `streamsOnlyChannelsOpenOnStreamsAndRetryPreservesTheSelection`
- `delayedPagesCannotReplaceTheSelectedTabOrAppendToAnotherChannelRoute`
- `delayedMetadataCannotRestoreAChannelAfterLeavingIt`

### ChannelSpoilersSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/ChannelSpoilersSmokeTest.kt) — Emulator channel settings, masking, reveals, account failures, DeArrow, AI and offline acceptance.

- `guestEditorValidationPersistenceAndThumbnailFirstTap`
- `accountSparseSaveFailureRetryAndResetPreserveOtherRules`
- `delayedDeArrowCyclesAndPlaybackNotificationStayMasked`
- `timedRulesUseCachedUploadAgeAndRetainUnknownMisses`
- `duplicateOccurrencesRevealIndependently`
- `channelThumbnailTakesPrecedenceOverAiWarningAndExplicitRevealShowsOriginal`
- `accountPreferenceReadFailureKeepsSafePlaceholdersAndCanRetry`
- `clipsAndOfflineDownloadsRespectSourceChannelRules`

### ChaptersPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/ChaptersPresentationTest.kt) — Chapters Presentation declared scenarios; exact cases are enumerated in the test catalog.

- `openingRevealsCurrentChapterAndSeekingKeepsThePanelOpen`
- `restorationKeepsOpenStateAndScrollButNewOccurrencesResetIt`
- `inlineChapterAndScrubFeedbackKeepAccessibleLongRtlTitles`
- `unavailableChaptersHideTheEntryAndLoadingDisablesSeekActions`

### ChaptersRuntimeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/ChaptersRuntimeTest.kt) — Chapters Runtime declared scenarios; exact cases are enumerated in the test catalog.

- `videoWithoutDescriptionLoadsWithoutInitializingAnInvalidRegex`
- `emptyDescriptionLoadsWithoutChapters`
- `ordinaryDescriptionLoadsWithoutChapters`
- `manualChaptersLoadOnAndroid`
- `unicodeWhitespaceAndTitlesLoadOnAndroid`
- `liveVideoLoadsWithoutChapters`

### ChaptersSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/ChaptersSmokeTest.kt) — Chapters Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `selectingAndScrubbingPreservePlaybackAndMakeNoPreviewOrMetadataRequests`
- `fullWidthTimelineAndCenteredPlaybackStayInPlaceAcrossFullscreen`
- `fullscreenUsesChapterSheetAndBackClosesItBeforeLeavingFullscreen`
- `chapterDrawerSurvivesRecreationAndWatchFullscreenTransitions`
- `commentsAndChaptersReplaceOneAnotherAndWatchBackOnlyClosesTheDrawer`
- `repeatedVideoQueueOccurrencesAndMinimizationDismissChapters`
- `liveDescriptionsNeverExposeChapters`
- `pipDismissesTheDrawerAndReturningDoesNotReopenIt`

### ClipsSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/ClipsSmokeTest.kt) — Clips Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `everyClipRowTapOpensTheClipWhileOverflowKeepsItsActions`
- `clipCloseStopsPlaybackWhileBackAndSwipeOnlyMinimize`
- `playerSettingsEndWithRefreshAndKeepClipPlaybackChoices`
- `clipChannelUsesDiskCachedAvatarAndKeepsThinModeAndFailureFallback`
- `libraryAndChannelListsPaginateRestoreAndShowBothThemes`
- `realDashHlsAndMp4UseIndependentBoundsAndNeverWriteSourceProgress`
- `fullVideoContinuesAtTheSameSceneAndKeepsPlaybackChoices`
- `createPreviewFailedPublishDraftAndDeletionCompleteTheFlow`
- `editorAndClipSurviveRecreationWithoutReplayingAnIncomingLink`
- `notificationsClampToTheClipAndLegacyFailuresNeverOpenFullVideo`
- `missingScopesAndUnavailableStoryboardsRemainActionable`
- `guestSignInReturnsToCreationAndCancelRestoresPlayingIntent`
- `staleReadsAndUnavailableSourcesKeepCurrentPlaybackAndClearPrivateDrafts`
- `clipBoundsSurviveBufferRefreshFullscreenMiniPlayerAndBackground`
- `trimAccessibilityKeyboardRotationAndWideEditorKeepTheDraftUsable`
- `pictureInPictureKeepsTheRelativeTimelineAndScopedActions`

### CommentsPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/CommentsPresentationTest.kt) — Comments Presentation declared scenarios; exact cases are enumerated in the test catalog.

- `lightThemeRichContentAndMetadataAreDistinct`
- `darkThemeLargeFontsAndNarrowLayoutKeepMetadataReadable`
- `wideLayoutRetainsRichTextAndReadOnlyLikeSemantics`

### CommentsSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/CommentsSmokeTest.kt) — Comments Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `entryHidesSpoilersAndFetchesOnlyWhenOpenedAndReusesLoadedComments`
- `replyPagesAreSeparateAndBackRestoresTheMainScrollPosition`
- `paginationRetryKeepsRowsCursorAndTotalAndSortingResetsTheList`
- `initialRetryEmptyStateAndDisabledPreferenceAreExplicit`
- `delayedSortAndReplyResponsesCannotReplaceAnotherVideo`
- `richTimestampLinksSeekWithoutRestartingAndLongTextExpands`

### CommunityPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/CommunityPresentationTest.kt) — Community Presentation declared scenarios; exact cases are enumerated in the test catalog.

- `postActionsSeparateLikesAndShareAboveAFullWidthCommentsButton`
- `invalidPostIdentifiersDisableOnlyTheUnavailableActions`
- `postExpansionRestoresAndKeepsRichLinksInsideTheCard`
- `shortAndStandalonePostBodiesHaveNoExpansionControl`
- `postShareChooserContainsThePostUrlAndChannelOwner`
- `channelIdentityAndRichDescriptionLinksWorkAtNarrowLargeFontSizes`
- `galleryRemainsBrowsableWhenImagesFailAndViewerHasAnAccessibleCloseButton`
- `postCommentPanelKeepsCloseSortingAndReadOnlyMetadataAccessible`

### CommunitySmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/CommunitySmokeTest.kt) — Community Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `specializedTabsSortAndRestoreAfterSearchAndChildNavigation`
- `postsPaginationRetainsRowsAndRetriesTheSameCursor`
- `postMediaPollsQuizzesAndUnknownAttachmentsRemainReadable`
- `postCommentsSheetSortsRepliesRetriesAndIgnoresVideoCommentVisibility`
- `feedCommentsSwitchTargetsRejectStaleCardsAndResetWhenLeavingPosts`
- `longFeedPostsExpandInlineAcrossScrollingRecreationAndComments`
- `feedShareLaunchesTheAndroidChooserWithoutOpeningPostDetails`
- `feedCommentsPreserveVideoStateAndRejectLateAccountAndInstanceResponses`
- `delayedPostsAndCommentsCannotOverwriteAnotherRouteOrInstance`
- `sharedPostIntentRecreationAndPlaybackKeepTheirIndependentState`
- `recreationAfterLeavingSharedPostDoesNotReplayItsLaunchIntent`

### ContentLinksSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/ContentLinksSmokeTest.kt) — Content Links Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `sharedChannelAliasesResolveAndRetryWithoutSearching`
- `sharedHashtagsUseNativePaginationAndChannelLinksPreservePlayback`
- `richWatchInformationAndTimestampSeekKeepTheQueueOccurrence`
- `boundsPauseWithoutAdvancingAndReplayStartsAtTheRequestedTime`
- `boundedLoopAndBackgroundModeKeepBoundsInTheService`
- `recreationRetainsOverridesAndSuccessorsKeepOnlyCarriedOptions`
- `sourceOnlyLinksApplyOptionsAndBoundsToTheFirstOccurrence`

### DeArrowSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/DeArrowSmokeTest.kt) — De Arrow Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `guestSettingsAndOriginalTitleControls`
- `sharedTitlesAcrossListsAndMetadataPreservePlayback`
- `contributionsGuidelinesFailuresAndPrivateIdentity`

### DeviceProfileStorageTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/DeviceProfileStorageTest.kt) — Device Profile Storage declared scenarios; exact cases are enumerated in the test catalog.

- `guestTwoAccountsAndRestartRestoreOnlyTheirOwnDeviceSaves`
- `capturedWritesRejectSwitchesAndTokenReplacement`
- `trendingCategoryPersistsByAccountAndInstanceAndRejectsStaleWrites`
- `stableRenamesAndUsernameReuseRemainSeparate`
- `legacyRenameAndConfirmedSessionUpgradeTransferOnlyTheirOwnProfile`
- `switchingInstancesAndBackDoesNotDeleteGuestSaves`
- `expiryRetainsAccountDeviceSavesWithoutExposingThemToGuest`
- `deletionPurgesOnlySelectedAccountAndGuestOverridesPersist`
- `legacyMigrationKeepsUnownedValuesGuestAndExpiredPositionsPrivate`
- `malformedEncryptedSessionLeavesUnknownLegacyProgressPrivate`

### DeviceProfilesSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/DeviceProfilesSmokeTest.kt) — Device Profiles Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `guestAccountTransitionsRestoreSettingsAndNeverRewriteGuestSpeed`
- `existingSessionDiscoversStableIdentityAndLateGuestSavesAreRejected`
- `guestCanBlockFromVideoMenuAndManageItWithoutSigningIn`
- `guestCanCreateAndResetChannelSponsorBlockOverrides`

### DiscoverySearchSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/DiscoverySearchSmokeTest.kt) — Discovery Search Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `trendingControlsSaveGuestRegionCategoryAndRestoreAcrossTabsAndRecreation`
- `settingsCountryPickerSavesAutomaticallyAndRetainsConfirmedRegion`
- `failedAccountRegionSaveRetainsPreviousSelectionAndCanRetry`
- `mixedSearchPreservesOrderChannelBackScrollRecreationAndRetry`
- `searchTypesUseCorrectFiltersAndKeepVideoChoices`
- `hiddenMixedPageStillLoadsChannelsAndVisibilityOverrideIncludesBlockedResults`
- `unsupportedOnlyAndRepeatedPagesHaveCorrectEndBehavior`
- `delayedRequestsCannotReplaceNewSearchModeCategoryOrNavigation`
- `controlsAndMixedCardsRemainReachableOnCompactDisplays`

### DownloadsPersistenceSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/DownloadsPersistenceSmokeTest.kt) — Downloads Persistence Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `seedBackgroundTransfers`
- `restoreTransfersAndOfflineResume`

### DownloadsSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/DownloadsSmokeTest.kt) — Downloads Smoke declared scenarios; exact cases are enumerated in the test catalog.

Explicit Ignore: "Emulator 37.2.12 host gfxstream TextureResize SIGSEGV during accepted video conversion. Run on a physical device or fixed emulator."

- `savedDownloadDetailsUseALocalOpaqueSheetAndKeepTheEntry`
- `artworkTitleStatusAndPaddingOpenTheSameOfflineDownload`
- `incompleteEntriesKeepActionsAvailableWithoutStartingPlayback`
- `downloadsOverAnHourUseAnHourTimestamp`
- `dialogRequiresMediaPreservesSelectionAndCreatesDuplicates`
- `allModesAndCaptionsDecodeOfflineWithoutMediaOrMetadataRequests`
- `offlinePlaybackUsesWatchMiniFullscreenPipAndBackgroundControls`
- `failuresRetryWithoutRepeatingCompletedTracksAndDeleteIsIndependent`
- `transfersSurviveRecreationCancellationAndProfileChanges`
- `restrictedDownloadsAndStaleDialogAreRejected`
- `singleVideoAudioAndCaptionExportsUsePickerAndPreserveFiles`
- `combinedExportHasBothTracksAndDecliningConversionStartsNoExport`
- `acceptedVideoConversionPreservesResolutionFrameRateAndBothTracks`

### FeedRefreshJourneyTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/FeedRefreshJourneyTest.kt) — Root toolbar actions and retained first-page refresh across failure, empty results, navigation, Trending filters and subscription pagination/search.

- `rootToolbarsKeepSearchAndYouOwnsSettings`
- `refreshRetainsRowsReplacesPageOneAndRecoversFromFailureAndEmptyResults`
- `navigatingAwayCancelsRefreshAndLateResponsesCannotReplaceTheOtherFeed`
- `refreshKeepsTrendingFiltersAndSubscriptionSearchAndSeparatesPagination`

### FeedRefreshPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/FeedRefreshPresentationTest.kt) — Pull indicator progress, threshold/cancellation gestures, scrolling, loading guards and accessible refresh actions.

- `partialPullShowsProgressAndReleasingBelowThresholdCancels`
- `thresholdPullRefreshesOnceAndStopsWhenTheRequestFinishes`
- `scrollingTowardTheTopDoesNotRefreshUntilThereIsATopPull`
- `accessibilityRefreshSharesTheLoadingGuardAndDisabledFeedsHaveNoAction`

### HomeSubscriptionsSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/HomeSubscriptionsSmokeTest.kt) — Home Subscriptions Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `homeHighlightChangesDuringRequestsAndSurvivesNavigationAndRecreation`
- `directoryIsAccessibleAfterFeedScrollAndRestoresSearchAndChannelPositionOnBack`
- `directoryKeepsChannelsOnRefreshFailureRetriesAndReflectsUnsubscribe`
- `directoryGuestPromptAndLateReadsCannotExposeFormerAccountChannels`
- `guestWatchActionsHavePrimaryControlsAndMoreWithoutImplicitQueue`

### LibraryJourneyTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/LibraryJourneyTest.kt) — Library dashboard, playlist, history and downloads journeys across appearance and text sizes. Exact cases appear in the test catalog.

- `libraryPagesAndEditorsKeepPlaybackAndTheirOpaqueControls`
- `downloadProgressFailuresDetailsAndOfflinePlaybackRemainActionable`

### LibraryPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/LibraryPresentationTest.kt) — Continuous library rows, explicit editor actions, accessibility and responsive toolbars. Exact cases appear in the test catalog.

- `narrowLargeTextDashboardKeepsEveryDestinationReachableInBothAppearances`
- `wideDashboardKeepsLibraryRowsInOrderAndGuestDownloadsAvailable`
- `opaqueToolbarLeavesFirstAndLastRowsAccessibleAtLargeText`
- `playlistFormKeepsSaveAvailableAndPreservesPrivacyAndTrimmedTitle`

### LiveChatSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/LiveChatSmokeTest.kt) — Live Chat Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `replayOpensOnDemandAndNeverShowsFutureMessagesOrChangesPausedPlayback`
- `failedReplayRetriesItsOffsetWhilePlaybackRemainsUsable`
- `unavailableReplayAndUnsupportedVideoDoNotBreakPlayback`
- `fullscreenDocksBesideAndBackClosesChatBeforeLeavingFullscreen`
- `settingsAutomaticallySaveTextAndAppearanceWithoutASaveFooter`
- `tenPercentDockWrapsLongUnicodeMessagesAndKeepsMenuActionsReachable`
- `overlayDraftSurvivesRotationAndRecreationAndBackCancelsWithoutClosingChat`
- `overlayDragAndResizeSaveCancelWithoutSeekingOrMinimizing`
- `filtersAndTimingSaveForGuestsAndSurviveRecreation`
- `signedInFiltersAndTimingUseDedicatedApisAndSaveFailuresRetry`
- `invalidRegexIsRejectedAndOldScopesExplainHowToRestoreSync`
- `editingTimingWhileItsAccountReadIsDelayedKeepsTheNewValue`
- `chaptersAndCommentsReplaceChatAndTheSameVideoNewOccurrenceStartsClosed`
- `overlayAndDockSupportLargeFontsBothThemesAndAccessibleCloseTargets`
- `miniplayerSuspendsReplayAndRestorationRetainsTheOpenedPanel`
- `portraitFullscreenDocksBelowAndKeepsTheDecodedVideoAspect`
- `guestAndAccountSettingsStayIsolatedAndAppearanceStaysLocal`
- `pipAndBackgroundSuspendRequestsAndRestoreAtTheCurrentPosition`
- `invalidFilterTextDoesNotBlockAnIndependentTimestampChoice`
- `staleChatCallbacksCannotWriteIntoAnotherVideoOrAccount`

### MediaListPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/MediaListPresentationTest.kt) — Shared media artwork dimensions, phone/tablet density, RTL, narrow/large-text stacking and hidden-thumbnail layout checks.

- `phoneAndTabletRowsUseLargerArtworkInBothDensitiesAndDirections`
- `narrowAndLargeTextRowsStackAndThinRowsReturnTheArtworkSpace`

### MetadataPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/MetadataPresentationTest.kt) — Metadata Presentation declared scenarios; exact cases are enumerated in the test catalog.

- `compactChannelAndVerifiedWatchDetailsRemainReadableAtLargeFont`
- `roundedAndLegacyCountsNeverGainExactTotalsAndEpochDatesStayHidden`

### NavigationHubSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/NavigationHubSmokeTest.kt) — Navigation Hub Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `feedsKeepIndependentScrollAndSettingsSearchAndChannelReturnToTheirOrigin`
- `libraryHubOwnsHistoryClipsAndPlaylistsAndAccountManagementLivesInSettings`
- `guestLibraryDestinationsReturnFromAuthenticationAndKeepDownloadsPublic`
- `guestAuthenticationBackReturnsToYouOrTheSettingsAccountPage`
- `feedPreferencesAndAccountInstanceChangesInvalidateSavedTabs`
- `settingsAndTabSwitchesKeepServicePlaybackActive`
- `regionChangesDuringSearchOrChildNavigationReloadTheReturningFeed`
- `playlistSegmentsKeepTheirOwnPositionsAcrossDetailsAndGlobalSearch`
- `libraryAndFeedPresentationAdaptToThemesLargeTextNarrowAndLandscapeScreens`

### OpaqueSurfacesPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/OpaqueSurfacesPresentationTest.kt) — Opaque controls and panels remain independent of scrolling artwork and backgrounds. Exact cases appear in the test catalog.

- `ordinarySurfacesRemainOpaqueOverChangingScenery`

### PanelPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/PanelPresentationTest.kt) — Drawer header, modal scrolling and busy library-action layout and behavior scenarios.

- `narrowLargeTextHeaderKeepsNavigationActionsAndRowsReachable`
- `modalSheetKeepsHeaderPinnedAndLastOptionReachable`
- `libraryPanelKeepsBusyDismissalAndActionSemantics`

### PlaybackQueuePresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/PlaybackQueuePresentationTest.kt) — Playback Queue Presentation declared scenarios; exact cases are enumerated in the test catalog.

- `implicitPlaybackNeverShowsAQueueRegardlessOfItemCount`
- `followingDuplicateOccurrencesOnlyScrollsTheBoundedInnerList`
- `collapsedQueueStaysCollapsedOnAdvanceAndReopensAtCurrentOccurrence`
- `loadingAndRecoveryRemainVisibleWithoutDetailsAndWhileCollapsed`
- `narrowDarkLargeFontLayoutKeepsActionsAccessible`
- `light390dpLayoutKeepsActionsAccessible`

### PlaybackVisibilitySmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/PlaybackVisibilitySmokeTest.kt) — Playback Visibility Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `swipesMinimizeAndRestoreTheSameLiveSurfaceAndPreservePausedPlayback`
- `portraitBrowsingResizeKeepsTheLiveSurfaceSessionAndSelections`
- `incompleteAndCanceledDragsReturnAndBothSidewaysDirectionsStopPlayback`
- `claimedPresentationDragCancelsAccumulatedSeekAndRestoresPlayingIntent`
- `fullscreenSwipeReturnsToWatchBeforeMinimizingAndButtonsAndSemanticsMatch`
- `fullscreenTopEdgeSwipesLeavePlaybackFullscreenInBothOrientations`
- `timelineCommentsQueueAndMiniButtonsKeepPriorityOverPresentationGestures`
- `miniStateSurvivesActivityRecreationAndSystemAnimationScaleZero`
- `miniPlayerShowsMovingFramesAndPreservesPlaybackAndAudioMode`
- `fullScreenPipHiddenPlaybackAndSettingsHaveCorrectWakeOwnership`
- `miniPlayerAdvancesQueueAndCompletionReleasesWake`
- `visibleVideoOutlastsSystemScreenTimeout`
- `narrowLayoutsKeepActionsAndMiniPlayerAccessibleWithLargeTitles`

### PlayerControlsPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/PlayerControlsPresentationTest.kt) — Portrait centering, responsive and popup controls, large text/RTL, fullscreen and landscape compatibility, playback states and 25% dim rendering.

- `portraitTransportStaysCenteredAcrossPhoneSizesLargeTextRtlAndTinyPictures`
- `tinyPopupDispatchesChapterSettingsChatAndFullscreenActions`
- `landscapeRetainsItsCompactHeaderTransportFallback`
- `noSeekButtonsAndChatStaysToTheLeftOfSettings`
- `oneTwentyFivePercentDimCoversThePictureWithoutLocalControlBackings`
- `multiHourTimestampCompactsAtLargeFontsWithoutLosingChapterOrSeeking`
- `loadingAndRetryRemainInsideTheRailInShallowVideo`
- `chapterTracksScrubbingAndSupportsClickAndKeyboardWithoutDisplacingSettings`
- `playbackKeepsPlayPauseReplayDisabledAndLoadingStates`
- `videosWithoutChaptersKeepTimeAndIconsAndLiveDurationLabel`

### PlayerFeedbackPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/PlayerFeedbackPresentationTest.kt) — SponsorBlock and seek feedback positioning, popup access, actions and shared 25% dim rendering.

- `compactActionsStayAtBottomLeftAndClearTheTimelineAndOtherControls`
- `skipAndCloseRemainAccessibleAndConfirmationIsCompact`
- `seekAndSponsorActionsUseTheSharedDimWithoutLocalBackings`

### PlaylistRssSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/PlaylistRssSmokeTest.kt) — Playlist Rss Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `pendingSubscriptionAtSignInKeepsOwnedAndSubscribedPlaylists`
- `libraryGroupsAndReadOnlySubscriptionsFollowOwnerUpdates`
- `nestedPlaylistSubscriptionDoesNotOpenThePlaylist`
- `ownedPlaylistDeletionStillRequiresConfirmation`
- `signInRetainsSubscriptionIntentAndFailureCanRetry`
- `typedDiscoveryAndChannelPaginationKeepMixSeeds`
- `rssSheetsOfferCorrectLinksFilesAndClearOnAccountChange`

### QueueLibrarySmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/QueueLibrarySmokeTest.kt) — Queue Library Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `standaloneManualQueueCollapseAndNewSessionVisibility`
- `singleItemQueueWithoutRecommendationsAndImplicitContinuation`
- `invalidQueueDurationsKeepTheServiceTimelineAndPlaybackUsable`
- `playlistLaunchLinkDoesNotRestartTheQueueOnRecreation`
- `guestSaveSignInCreateAndRetryKeepsCreatedPlaylist`
- `audioMenuStartsWithoutVideoAndCarriesThroughNext`
- `duplicateOccurrencesAutomaticAdvanceAndRepeatAll`
- `localRemovalDoesNotDeleteSavedPlaylistAndCurrentFinishes`
- `backgroundQueueAndActivityRecreationUseServiceState`
- `mixAndPlaylistLinksRetainContextsAndMixDisablesAll`
- `repeatOneRestartsAndRemovedPlayingSourceStillAdvances`
- `pictureInPictureKeepsServiceOwnedAdvancement`
- `blockUndoAndAccountChangeInvalidateOldUndoAndQueue`

### ResponsiveShellSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/ResponsiveShellSmokeTest.kt) — Real emulator window widths 320/360/448/600/840/1200dp and font scales 1/1.3/2 in both appearances; visible root labels, non-overlapping targets and bar/rail layout. Exact methods appear in the test catalog.

- `fourRootsKeepLabelsTargetsAndReadingBoundsAcrossWindowAndFontMatrix`
- `keyboardTabAndEnterNavigateAndSubmitSearchExactlyOnce`
- `authenticationFieldsAndSubmitRemainReachableAtNarrowLargeTextAndWideSizes`

### SearchExperienceSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/SearchExperienceSmokeTest.kt) — Search editor, history, suggestions, filters, profile isolation and mixed-result presentation acceptance.

- `freshSearchEditCancelRecreationAndFiltersPreserveTheRightState`
- `recentAndLiveRowsSubmitOnceAndSupportEditingRemovingAndClearing`
- `suggestionFailuresAndDelayedResponsesNeverBlockOrReplaceTheCurrentQuery`
- `historyAndOptionsAreLocalToTheActiveProfileAndSurviveRecreation`
- `filtersResetAndRemovalKeepTheQueryAndVisibilityPreference`
- `mixedCardsRemainDistinctInThemesThinModeNarrowLargeTextAndLandscape`

### SearchHistorySmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/SearchHistorySmokeTest.kt) — Search History Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `iconImeAndPhysicalEnterEachSubmitExactlyOnce`
- `imeSubmissionPreservesPastedVideoTimestamps`
- `channelSearchPaginationAndClearRestoreSelectedStreams`
- `emptyScopedSubmissionRestoresTheSelectedChannelTab`
- `channelSearchOpeningBlurAndRecreationKeepSubmissionExplicit`
- `subscriptionSearchLoadsBeyondHiddenPagesAndIgnoresFeedOnlyFilters`
- `historySearchCoversLaterPagesAndDateGroupsKeepUnknownEntries`
- `historyRemovalPreservesProgressAndClearRequiresConfirmation`
- `oldHistoryServerRetainsViewingAndExplainsDisabledSearch`
- `delayedSearchCannotReplaceNewChannelTabOrAccount`
- `historyPaginationReloadsWhenServerCalendarDayChanges`

### SecondaryScreensJourneyTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/SecondaryScreensJourneyTest.kt) — Settings, account, community and other secondary-screen interaction coverage. Exact cases appear in the test catalog.

- `modalChannelSettingsKeepInputAndCloseWhenInputMethodOpens`
- `settingsFormsMenusAndAuthenticationStayReachable`
- `everySettingsCategoryUsesOpaqueSurfacesAndKeepsControlsReachable`
- `accountManagementUsesQuietFormsAndExplicitConfirmations`
- `subscriptionMenuPendingFailureAndRetryNeverMisrepresentMembership`
- `guestSubscribeCompletesAfterSignInAndReturnsToTheCreator`
- `canceledSubscribeSignInDoesNotChangeALaterAccount`

### ServiceComposeRule

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/ServiceComposeRule.kt) — Service-aware Compose/device setup and cleanup support.

Test support/helper file; no declared `@Test` methods.

### SettingsSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/SettingsSmokeTest.kt) — Automatic per-field saves and invalid/failed edits, playback/codec settings, device appearance persistence, recreation/account isolation and System mode updates.

- `outstandingChatValidationLinksToTheActualControlsAndReverts`
- `deviceAccentSavesImmediatelyAndSurvivesRecreationAndAccountChange`
- `retiredDeviceOptionsStayAbsentInAppearanceAndPlayerSettings`
- `systemAppearanceChangesPreserveAutomaticPreferencePage`
- `fullScreenNavigationAndAutomaticChangesSurviveRecreation`
- `guestPlaybackDefaultsAndLocalResumeReachPlayer`
- `failedAutomaticSaveKeepsRequestedValueAcrossNavigationRecreationAndRetry`
- `codecSettingPrecedesQualityAndSyncsInBothDirections`
- `preferredAudioSavesForGuestsAndAccountsAndRetainsFailedEdits`

### SponsorBlockSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/SponsorBlockSmokeTest.kt) — Sponsor Block Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `guestSheetColorValidationPersistenceAndMarkers`
- `manualDismissAutoOverlapReplayFullscreenAndRefresh`
- `sharedChannelOverridesFailedDraftAndReset`
- `serviceBackgroundAudioPipAndStaleCommands`
- `failureAndLivePlaybackStayUsableWithoutPrompts`

### StreamControlsSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/StreamControlsSmokeTest.kt) — Stream Controls Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `exactTracksMenusRefreshRetryAndReconnection`
- `savedQualityDefaultsSelectActualSupportedTracks`
- `preferredAudioSelectsTheFirstSegmentIncludingFallbacksRegionsAndAudioOnly`
- `progressiveAudioDisplaysItsActiveTrackWithoutAnExplicitOverride`
- `manualAudioSurvivesRefreshRetryReconnectionAndNextVideoUsesSavedDefaults`
- `codecPreferencesReachTheFirstDashSegmentAndFallbackWithinTheResolution`
- `codecAutoAndManualChoicesSurviveRefreshRetryAndReconnection`
- `nextQueueOccurrenceUsesSavedDefaultsAndMenusRetainAtMostFourPerGroup`
- `consecutiveSingleTapsAccumulateAndOppositeTapResets`
- `accumulationFreezesPlayingVideoAndCompetingActionsCancel`
- `fullscreenAndPipEntryCancelAccumulation`

### SubscriptionJourneyTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/SubscriptionJourneyTest.kt) — Subscription feed/directory, channel and related tool navigation journeys. Exact cases appear in the test catalog.

- `feedDirectoryAndCreatorSheetsRetainPlaybackAndTheirOrigin`
- `darkOpaqueFeedAndExportsRetainAccessibleControls`
- `wideFeedsUseTwoColumnsAndReducedMotionPreservesNavigation`

### SubscriptionPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/SubscriptionPresentationTest.kt) — Subscription/channel hierarchy, scoped search, native controls and large-text layout. Exact cases appear in the test catalog.

- `narrowLargeTextFeedReflowsAndKeepsItsContentBelowChrome`
- `directoryLastRowAndCountStayAccessibleWithLargeTextAndOpaqueSurfaces`
- `channelTabsScrollAndSortMenusKeepTheirSelectedSemantics`

### SubscriptionSortingSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/SubscriptionSortingSmokeTest.kt) — Subscription Sorting Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `relevanceDefaultAndAllFourSortsFilterLocallyWithCorrectDetails`
- `choiceSurvivesNewActivityAndIsScopedToInstance`
- `legacyAndPermissionFallbackKeepChoiceWhileFailuresRetainRows`
- `repeatWatchesAndMemberPreferenceRefreshStatsAndLateReadCannotSurviveSignOut`

### SubscriptionsPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/SubscriptionsPresentationTest.kt) — Subscriptions Presentation declared scenarios; exact cases are enumerated in the test catalog.

- `sortingAndDetailsFitNarrowScreensWithLargeTextAndProduceScreenshots`
- `narrowLightAndDarkDirectoriesKeepSearchPinnedAndRowsAccessible`
- `liveNameSearchAndClearWorkInThinModeAndEmptyStatesStayDistinct`

### VideoInformationPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/VideoInformationPresentationTest.kt) — Video Information Presentation declared scenarios; exact cases are enumerated in the test catalog.

- `metadataAndCreditsFitNarrowDarkLargeFontLayouts`
- `descriptionOwnsMetadataVisibilityAndGenreLinksStillWork`
- `emptyDescriptionsStillShowMetadataAndMissingMetadataAddsNoSection`
- `richDescriptionPreservesFormattingAndNativeLinkTargets`
- `plainFallbackLinksTimestampsAndHashtagsWithoutExecutingHtml`

### VisibilitySmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/VisibilitySmokeTest.kt) — Visibility Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `guestBrowsingSettingAndBadgesCoverCompactTextOnlyLayouts`
- `hiddenSearchPagesRemainReachableAndSearchOverrideCanReset`
- `cardBlockSearchOverrideAndChannelHeaderPreserveLibraryAccess`
- `managerRetainsFailedChangesAndRefreshesWebsiteState`
- `directMemberPlaybackChannelBlockingAndRecommendationsKeepPlayerRunning`
- `savedSearchOverridesAndSnapshotsAreOwnedByEachAccountAndInstance`

### VisualFixesSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/VisualFixesSmokeTest.kt) — Visual Fixes Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `watchViewportStaysFixedAcrossMiniChromeChangesAndCoversTheBottomInset`
- `captchaFocusAndTypedAnswerStayAboveNavigationAndKeyboard`

### VisualSurfaceRegressionTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/VisualSurfaceRegressionTest.kt) — Visual Surface Regression declared scenarios; exact cases are enumerated in the test catalog.

- `artworkAvailabilityPreservesAnOpenControlMenu`
- `roundedActionsLeaveTheContrastingContainerVisibleAtTheirCorners`
- `wrappedToolbarTitleMasksScrollingGlyphsAndReservesMeasuredInsets`
- `launcherSymbolsStayInsideTheSafeRegionForEveryMask`
- `multipartToolbarPlacesControlsBelowTheSafeTitle`

### WatchCommentsPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/WatchCommentsPresentationTest.kt) — Watch Comments Presentation declared scenarios; exact cases are enumerated in the test catalog.

- `opaquePanelLeavesFirstCommentReadableAndKeepsSortAndRepliesWorking`

### WatchPlayerResizePresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/WatchPlayerResizePresentationTest.kt) — Watch Player Resize Presentation declared scenarios; exact cases are enumerated in the test catalog.

- `playerTracksAnActiveDragAndOnlyExpandsNearTheTop`
- `shortPageStaysCompactAfterItsListStopsScrollingAndFlingCanExpandIt`
- `commentsMinimizationAndRecreationKeepProgressButANewOccurrenceResetsIt`
- `smallLandscapePlayerDoesNotConsumeScrollingForAnInvisibleResize`

### WatchPresentationTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/WatchPresentationTest.kt) — Filled watch action circles, one-row geometry, wrapping labels, tooltips, menus, neutral surfaces and disclosure accessibility.

- `expandedWatchUsesFlatNeutralSurfacesInBothThemes`
- `moreMenuSurvivesThemeAndWidthChanges`
- `actionsKeepVisibleLabelsAndDisabledDownloads`
- `circlesStayInOneRowAndLargeLabelsWrapWithoutClippingInRtl`
- `disclosureKeepsExpandedSemanticsAndWrapsWithLargeTextInOpaqueMode`
- `longPressShowsTheActionTooltipWithoutActivatingTheButton`

### WatchedIndicatorsSmokeTest

[Source](../../android/app/src/androidTest/java/net/wingress/mobivious/WatchedIndicatorsSmokeTest.kt) — Watched Indicators Smoke declared scenarios; exact cases are enumerated in the test catalog.

- `sharedCardsCoverListsAndCompactThumbnailFreeLayouts`
- `guestsUseLocalProgressOnlyWhenResumeIsEnabled`
- `unknownDurationAndLiveCardsKeepBadgesWithoutPartialBars`
- `historyRemovalPreservesProgressAndClearRemovesBoth`
- `playbackUpdatesInBackgroundAndCompletionKeepsTheWatchedBadge`
- `refreshFailureRetainsStateAndSignOutHidesAccountIndicators`

Inventory total: **777 declared test methods** (including ignored tests).
