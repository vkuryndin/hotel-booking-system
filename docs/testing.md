# Тестирование

## Состав тестового набора

Тесты охватывают бизнес-логику Booking Service и Hotel Service, JWT и доступ, Saga, работу с H2, HTTP-маршруты, отказоустойчивость и конкурентные запросы. Каталог ниже перечисляет объявления тестовых методов в текущем `src/test/java`, а не количество запусков параметризованных тестов. Для фактического результата используйте отчёты JUnit.

| Группа | Классов | Объявлений тестовых методов |
|---|---:|---:|
| API Gateway | 3 | 17 |
| Booking Service (локальные тесты) | 42 | 219 |
| Hotel Service | 31 | 148 |
| E2E (Booking Service) | 14 | 118 |
| **Всего** | **90** | **502** |

Классы `ExtendedE2ESupport` и `SagaE2ESupport` — вспомогательные. `UiDataSeedManualTest` — отдельная утилита для нагрузочного заполнения H2, она **не входит** в прогон JUnit. `@ParameterizedTest` может дать несколько фактических запусков из одного объявления; E2E пропускаются в обычном `clean test`, если не активирована системная опция.

## Локальные unit, MVC, JPA и интеграционные тесты

Запуск из корня репозитория:

```powershell
.\gradlew.bat clean test
```

На macOS/Linux: `./gradlew clean test`. Результаты: `<module>/build/reports/tests/test/index.html` и JaCoCo в `<module>/build/reports/jacoco/test/html/index.html` после генерации соответствующего отчёта.

Набор проверяет создание и отмену бронирования, валидацию дат, права USER/ADMIN, обработку ошибок, ограничение частоты запросов, правила выбора номера, JPA-ограничения, блокировки, сценарии компенсации и восстановления, а также маршруты и OpenAPI. Проверки формата Java-кода выполняются отдельно:

```powershell
.\gradlew.bat checkstyleMain spotbugsMain spotlessCheck
```

Checkstyle и SpotBugs анализируют код, Spotless проверяет форматирование. Задачи `checkstyleTest` и `spotbugsTest` проверяют тестовый код отдельно; E2E являются частью исходников `src/test/java` и могут попадать в анализ тестового кода.

## Сквозные E2E

Для полного E2E-прогона должны работать четыре приложения: Eureka Server → API Gateway → Hotel Service → Booking Service. Они используют отдельные H2 in-memory базы и регистрируются в Eureka; после старта дождитесь доступности сервисов. Проверка выполняется через JUnit-конфигурацию IntelliJ IDEA:

| Поле | Значение |
|---|---|
| Название | `E2E All` |
| Тип / область | JUnit, `All in package` |
| Module | `hotel-booking-system.booking-service.test` |
| Package | `com.hotelbooking.booking.e2e` |
| VM options | `-ea -Dhotel.e2e=true` |
| Working directory | `$MODULE_WORKING_DIR$` |
| JDK | Java 17 или совместимая версия |

E2E применяют `@EnabledIfSystemProperty(named="hotel.e2e", matches="true")`; обычный запуск `clean test` не заменяет запуск `E2E All`. Набор включает гонки бронирований, нагрузку, отказ Hotel Service, Retry/Timeout/CircuitBreaker, Saga Recovery, безопасность, работу с фотографиями и стратегии. Для повторного полного прогона рекомендуется перезапуск четырёх сервисов, чтобы не переносить состояние H2, Circuit Breaker и Eureka из предыдущих прогонов.

Для сценария `HotelBookingSagaFaultE2ETest.exhaustedRecoveryAndAdminManualRequeue` необходим локальный `booking-service/secrets/saga-e2e-admin.properties`:

```properties
email=<email администратора>
password=<пароль администратора>
```

При отсутствии файла сценарий может быть пропущен. В итоговом отчёте проверяйте не только падения, но и пропущенные/прерванные сценарии. Ряд fault-тестов изменяет регистрацию сервисов в Eureka; запускать их следует в изолированном окружении.

## Ручные HTTP-проверки

Файл `test/manual/requests.http` предназначен для IntelliJ IDEA HTTP Client. Его начальные разделы выполняются последовательно на чистой H2 и сохраняют JWT/ID через `client.global`. Отдельные разделы проверяют нестандартные ошибки, отказы и ограничение частоты запросов. Сценарий входа ADMIN в файле также используется тестовой инфраструктурой E2E для чтения тестовых учётных данных.

**Rate Limiting:** регистрация ограничена 5 попытками с IP за час (включая неуспешные), вход — 20 попытками с IP за минуту. Основной регрессионный прогон и дополнительные негативные проверки выполняются раздельно. В разделе 15 файла есть инструкции по паузам: для регистрации при исчерпании лимита ориентир — 61 минута или `Retry-After` + 2 секунды, для входа — 61 секунда или `Retry-After` + 2 секунды. Эти интервалы относятся к одному экземпляру Booking Service; отключать Rate Limiting для тестирования не требуется.

Разделы с отключением/перезапуском сервисов выполняются отдельно от основной последовательности; при повторном создании ресурсов используйте новые данные и Idempotency-Key либо очищенную H2.

## Дополнительное нагрузочное заполнение интерфейса

Класс `UiDataSeedManualTest` в пакете `booking.e2e` содержит `main()` и предназначен для **ручного** запуска при работающих сервисах. Он не запускается обычным JUnit и может создать большой объём данных; используйте только отдельное тестовое окружение.

## Каталог тестовых классов и методов

Ниже перечислены все Java-классы с JUnit-тестовыми методами. Названия приведены без изменений; в скобках указан вид JUnit-аннотации, если это не обычный `@Test`.

### API Gateway

**`GatewaySecretFilterTest` — 3**  
`api-gateway/src/test/java/com/hotelbooking/gateway/config/GatewaySecretFilterTest.java`

`forgedClientSecretIsOverwrittenBeforeForwarding`, `gatewaySuppliesSecretWhenClientSentNone`, `forgedVerifiedIpIsRemovedAndActualPeerIsUsed`.

**`GatewaySecurityErrorHandlerTest` — 2**  
`api-gateway/src/test/java/com/hotelbooking/gateway/config/GatewaySecurityErrorHandlerTest.java`

`authenticationFailurePreservesBearerChallengeAndReturnsErrorJson`, `accessDeniedReturnsErrorJsonWithoutChangingForbiddenStatus`.

**`JwtRevocationFilterTest` — 12**  
`api-gateway/src/test/java/com/hotelbooking/gateway/config/JwtRevocationFilterTest.java`

`rejectedRequestsHaveUniformJsonErrorAndTraceId`, `approvedJwtStatusForwardsHotelRequest`, `revokedJwtIsRejectedAsUnauthorized`, `forcedPasswordChangeBlocksOrdinaryRequests`, `forcedPasswordChangeAllowsPasswordChangeEndpoint`, `bookingServiceOutageFailsClosed`, `noAuthenticationReturnsUnauthorizedWithoutBackendCheck`, `unrecognizedRoleCannotReachHotelOrTokenStatus`, `loginIsNotBlockedByMissingJwtOrBookingStateCheck`, `registrationIsNotBlockedByMissingJwtOrBookingStateCheck`, `adminRoleIsAccepted`, `simultaneousRequestsWithSameTokenShareStatusCall`.


### Booking Service — локальные тесты

**`HotelServiceClientRetryTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/client/HotelServiceClientRetryTest.java`

`openCircuitBreakerDoesNotRetry`, `transientNetworkFailureStillRetriesTwice`.

**`OpenApiErrorResponseTest` — 1**  
`booking-service/src/test/java/com/hotelbooking/booking/config/OpenApiErrorResponseTest.java`

`centralizesErrorSchemasWithoutChangingResponseCodesOrSpecificDescriptions`.

**`RestClientConfigTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/config/RestClientConfigTest.java`

`transportIllegalStateBecomesServiceUnavailable`, `unrelatedTransportArgumentErrorIsNotConverted`.

**`UserTokenStateWebConfigTest` — 8**  
`booking-service/src/test/java/com/hotelbooking/booking/config/UserTokenStateWebConfigTest.java`

`missingGatewaySecretIsRejectedBeforeUserLookup`, `validCurrentJwtPasses`, `missingUserRevokesJwt`, `roleOrPasswordChangeInvalidatesOldTokenVersion`, `changedIdentityInvalidatesToken`, `forcedPasswordChangeBlocksEverythingExceptChangePassword`, `serviceTokenBypassesUserDatabaseLookup`, `nonNumericSubjectIsRejected`.

**`AdminUserSearchControllerTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/controller/AdminUserSearchControllerTest.java`

`userSearchEndpointRemainsAdminOnly`, `controllerPassesOptionalQueryToExistingReadService`.

**`BookingAllAdminSecurityTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/controller/BookingAllAdminSecurityTest.java`

`allBookingsEndpointRequiresAdminRole`, `allBookingsServiceAlsoRequiresAdminRole`.

**`BookingRecoveryAdminSecurityTest` — 1**  
`booking-service/src/test/java/com/hotelbooking/booking/controller/BookingRecoveryAdminSecurityTest.java`

`adminOnlyGuardIsDeclaredForBothEndpoints`.

**`BookingRoutesMvcTest` — 22**  
`booking-service/src/test/java/com/hotelbooking/booking/controller/BookingRoutesMvcTest.java`

`everyRouteIsDispatchedToTheCorrectController` (`@ParameterizedTest`), `protectedRoutesRejectRequestsWithoutJwt` (`@ParameterizedTest`), `usersCannotInvokeAdminRoutes` (`@ParameterizedTest`), `serviceRoleCannotInvokeAdminRoutes` (`@ParameterizedTest`), `adminRoleCanReadUserProfile`, `serviceRoleCannotReadUserProfile`, `invalidBearerTokenIsRejectedBeforeController`, `missingGatewayHeaderIsRejectedEvenWithValidJwt`, `revokedAccountIsRejectedBeforeController`, `passwordChangeRequirementBlocksOtherEndpoints`, `passwordChangeEndpointRemainsAccessibleWhenChangeIsRequired`, `publicLoginWorksWithoutJwtButNotWithoutGateway`, `loginSerializesAllAccountIdentityFields`, `userSearchBindsQueryAndPageable`, `adminSearchSerializesPageContent`, `missingBookingIdempotencyKeyReturns400`, `incompleteRegistrationReturns400`, `malformedBookingStatusReturns400`, `unknownBookingReturnsDomain404`, `conflictingRegistrationReturns409`, `bookingSagaUnavailableReturns503`, `everyControllerHandlerHasAnHttpContractCase`.

**`BookingRequestValidationMvcTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/dto/request/BookingRequestValidationMvcTest.java`

`tooManyGuestsReturnValidationErrorWithTraceId`, `checkoutTodayReturnsValidationError`, `validBoundaryRequestPassesMvcValidation`.

**`BookingRequestValidationTest` — 8**  
`booking-service/src/test/java/com/hotelbooking/booking/dto/request/BookingRequestValidationTest.java`

`zeroGuestsAreRejected`, `guestCountAboveTwentyIsRejected`, `oneAndTwentyGuestsAreAccepted`, `omittedGuestCountDefaultsToOne`, `checkoutTodayOrYesterdayIsRejected`, `omittedCheckoutIsRejected`, `pastCheckinIsStillRejected`, `todayCheckinTomorrowCheckoutAreAccepted`.

**`BookingErrorHandlingTest` — 9**  
`booking-service/src/test/java/com/hotelbooking/booking/exception/BookingErrorHandlingTest.java`

`businessErrorHasPreservedStatusCodeAndTraceId`, `existingConflictCodeIsPreserved`, `validationErrorsKeepFieldErrors`, `malformedJsonHasOurStandardErrorFormat`, `missingHeaderAndUnsupportedMethodAreMapped`, `responseStatusExceptionKeepsItsHttpStatus`, `accessDeniedIsStill403`, `unexpectedErrorIs500ButItsDetailsAreNotLeaked`, `gatewayInterceptorRejectionAlsoCarriesTraceId`.

**`FailedAccountLoginLimiterTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/security/FailedAccountLoginLimiterTest.java`

`fifthFailureBlocksSameAccountButNotOtherAccount`, `expiredWindowIsUnlocked`.

**`LoginRegistrationRateLimiterCapacityTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/security/LoginRegistrationRateLimiterCapacityTest.java`

`manyUniqueIdentitiesNeverGrowBeyondLimit`, `differentWindowLengthsStillWork`.

**`PublicAuthRateLimitTest` — 7**  
`booking-service/src/test/java/com/hotelbooking/booking/security/PublicAuthRateLimitTest.java`

`loginThrottlesThirdAttemptWithRetryAfter`, `registrationHasIndependentHourlyLimit`, `limitsArePerIp`, `cannotBypassLimitsUsingSpoofedForwardedHeader`, `trustedGatewaySeparatesClientAddresses`, `untrustedClientCannotSpoofVerifiedHeader`, `otherMethodsAndRoutesRemainUnaffected`.

**`RateLimiterAdditionalUnitTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/security/RateLimiterAdditionalUnitTest.java`

`exactLimitAndNextAttemptRejected`, `independentKeysHaveIndependentWindows`, `concurrentCallsCannotAllBypassLimit`.

**`SensitiveActionRateLimitInterceptorTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/security/SensitiveActionRateLimitInterceptorTest.java`

`passwordChangeIsLimitedPerAuthenticatedUser`, `adminResetAndUpdateUseIndependentLimits`, `otherMethodsAreNotLimited`.

**`ServiceTokenProviderTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/security/ServiceTokenProviderTest.java`

`serviceTokenUsesCorrectIssuerSubjectAudienceAndRole`, `serviceTokenExpiresAfterOneMinute`, `serviceTokenDoesNotHaveUserTokenVersionOrIdentity`.

**`AdminConcurrencyJpaTest` — 1**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AdminConcurrencyJpaTest.java`

`competingDemotionsPreserveAtLeastOneAdministrator`.

**`AdminProtectionTest` — 7**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AdminProtectionTest.java`

`adminCannotDemoteSelfEvenWithMultipleAdmins`, `lastAdminCannotBeDemotedByAnotherAdmin`, `demotionOfOtherAdminWorksWhenAnotherAdminRemains`, `adminCannotDeleteSelf`, `lastAdminCannotBeDeletedByAnotherAdmin`, `otherAdminCanBeDeletedIfAtLeastOneAdminRemains`, `bookingHistoryStillPreventsUserDeletion`.

**`AdminReadCoverageUnitTest` — 4**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AdminReadCoverageUnitTest.java`

`pageTooLargeRejected`, `missingUserRejected`, `pageUsesDeterministicIdSort`, `existingUserMapped`.

**`AdminUserSearchJpaTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AdminUserSearchJpaTest.java`

`matchesUsernameFirstNameLastNameAndEmailCaseInsensitively`, `literalUnderscoreIsNotWildcard`, `paginationRestrictsResultSetAndKeepsIdOrder`.

**`AdminUserSearchServiceTest` — 4**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AdminUserSearchServiceTest.java`

`nonblankSearchIsTrimmedLowercasedAndKeepsPagination`, `wildcardsAreEscapedAndCannotExpandSearch`, `emptySearchRetainsOriginalRepositoryPath`, `pageSizeLimitAlsoAppliesWhenSearching`.

**`AdminWriteCoverageUnitTest` — 9**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AdminWriteCoverageUnitTest.java`

`duplicateUsernameRejected`, `duplicateEmailRejected`, `passwordTooLongRejected`, `concurrentUniqueViolationIsConflict`, `cannotDeleteSelf`, `missingTargetUserRejected`, `lastAdminCannotBeDeleted`, `bookingHistoryPreventsDeletion`, `deletionFlushesChanges`.

**`AppUserOptimisticLockJpaTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AppUserOptimisticLockJpaTest.java`

`staleProfileUpdateMustNotRestoreDemotedAdminOrOldTokenVersion`, `stalePasswordUpdateMustNotClearAdminPasswordResetFlag`.

**`BookingIdempotencyConcurrencyJpaTest` — 1**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingIdempotencyConcurrencyJpaTest.java`

`sameUserAndKeyCannotCreateTwoPendingBookings`.

**`BookingQueryServiceTest` — 11**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingQueryServiceTest.java`

`unknownBookingThrowsNotFound`, `anotherUsersBookingIsForbidden`, `ownerCanReadBooking`, `adminCanReadAnotherUsersBooking`, `myBookingsUseUserFilterAndStableSort`, `myBookingsFilterByStatus`, `checkoutFilterRequiresConfirmedStatus`, `confirmedCheckoutFilterUsesDedicatedRepositoryQuery`, `adminListWithoutStatusUsesAllRecords`, `adminListWithStatusUsesStatusFilter`, `maximumPageSizeIsEnforcedForBothUserAndAdmin`.

**`BookingRecoveryPaginationJpaTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingRecoveryPaginationJpaTest.java`

`pendingQueryReturnsNextPageWithoutSkippingRecords`, `cancellationQueryReturnsNextPageWithoutSkippingRecords`.

**`BookingRecoveryRaceTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingRecoveryRaceTest.java`

`recoveryWins_confirmationMustFail`, `confirmationWins_recoveryMustNotCancel`, `activeSagaMustNotBeRecoveredWhenCreatedAtIsOld`.

**`BookingRecoveryServiceTest` — 8**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingRecoveryServiceTest.java`

`pendingRecoveryCompensatesBeforeMarkingFailed`, `failedCompensationLeavesPendingForNextRecovery`, `confirmedOrFreshBookingIsSkippedByTransactionalRecheck`, `cancellationRecoveryUsesReleaseNotCompensation`, `failedCancellationReleaseLeavesCancellingForNextRecovery`, `cancellationRecoverySkipsBookingNoLongerCancelling`, `pendingRecoveryProcessesMoreThanTenCandidates`, `cancellationRecoveryProcessesMoreThanTenCandidates`.

**`BookingSagaStateJpaTest` — 13**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingSagaStateJpaTest.java`

`createPendingStoresReservationIdFingerprintAndGuestCount`, `pendingCanBeConfirmedOnceOnly`, `recoveryMarksPendingRecoveringAndDisablesLateConfirmation`, `recoverySkipsYoungPendingAndConfirmed`, `compensationRecordsFailedAndProhibitsConfirmation`, `confirmedBookingCannotBeCompensatedAsFailedCreation`, `cancelledCreationCannotBeCancelledAgainWithNewOutcome`, `onlyOwnerOrAdminCanStartCancellation`, `cancellationIsRecoverableAndEventuallyIdempotent`, `cannotFinishCancellationBeforeItStarted`, `cannotSwitchRoomAfterConfirmationOrWhileRecovering`, `sameKeyDifferentRequestIsRejected`, `duplicateUserAndIdempotencyKeyFailsAtDatabaseConstraint`.

**`BookingServiceSagaTest` — 19**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingServiceSagaTest.java`

`successfulCreationConfirmsExactlyOnceAndNeverCompensates`, `replayOfConfirmedKeyReturnsOriginalBookingWithoutAnotherReservation`, `pendingAndRecoveringKeysCannotStartAnotherSaga`, `previouslyFailedKeyCannotStartAnotherSaga`, `repeatedRoomConflictProducesSameDomainError`, `hotelConflictCompensatesAndRecordsRoomConflict`, `unavailableHotelCompensatesAndMarksFailed`, `confirmationFailureAfterHotelReservationStillCompensates`, `failedCompensationDoesNotMarkSagaCancelledSoRecoveryCanRetry`, `invalidReservationResponseIsCompensated`, `autoSelectionFallsBackAfterConflictWithoutCompensatingFirstCandidate`, `allAutoCandidatesOccupiedCompensatesLatestAttempt`, `cancellationUsesOrdinaryReleaseAndThenFinishes`, `repeatedCancellationLoadsExistingBookingWithoutCallingHotelAgain`, `failedReleaseLeavesCancellationInProgressForRecovery`, `invalidDatesAndTooLongStayAreRejectedBeforeHotelCall`, `disabledRoomIsRejectedBeforePendingState`, `notEnoughCapacityIsRejectedBeforePendingState`, `replayOfCancelledBookingReturnsActualStatus`.

**`BookingValidationCoverageUnitTest` — 10**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingValidationCoverageUnitTest.java`

`checkInIsRequired`, `checkoutIsRequired`, `checkoutMustBeAfterCheckin`, `maximumStayIs365Nights`, `roomIdMustBePositive`, `manualBookingRejectsHotelId`, `autoBookingRequiresPositiveHotelId`, `autoBookingRejectsNoRecommendations`, `manualBookingRejectsUnavailableRoom`, `manualBookingRejectsInsufficientCapacity`.

**`ExhaustedSagaMonitorTest` — 1**  
`booking-service/src/test/java/com/hotelbooking/booking/service/ExhaustedSagaMonitorTest.java`

`checksExhaustedWithoutMutatingState`.

**`HistoricalIdempotencyReplayTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/service/HistoricalIdempotencyReplayTest.java`

`successfulHistoricalReplayReturnsOriginalBookingWithoutHotelCall`, `newBookingWithPastDatesStillReturnsFieldValidationErrors`, `guestCountStillValidatedForNewBooking`.

**`JwtServiceTest` — 8**  
`booking-service/src/test/java/com/hotelbooking/booking/service/JwtServiceTest.java`

`signedTokenHasExpectedSubjectIssuerRoleAndIdentity`, `adminTokenIncludesAdminRole`, `lifetimeIsOneHourAndReportedInSeconds`, `tokenSignedByAnotherKeyIsRejected`, `modifiedPayloadIsRejectedBySignatureValidation`, `wrongIssuerIsRejected`, `expiredTokenIsRejected`, `roleAndTokenVersionChangeAreEmbeddedInNewToken`.

**`LoginIdentityResponseUnitTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/service/LoginIdentityResponseUnitTest.java`

`loginIncludesIdentityEvenWhenPasswordChangeIsRequired`, `originalFourArgumentConstructorRemainsUsable`, `failedLoginDoesNotGenerateJwtOrReturnIdentity`.

**`LoginServiceTest` — 6**  
`booking-service/src/test/java/com/hotelbooking/booking/service/LoginServiceTest.java`

`validCredentialsReturnBearerTokenAndLifetime`, `missingUserRejectsWithoutGeneratingJwt`, `wrongPasswordRejectsWithoutGeneratingJwt`, `absentAndWrongCredentialsExposeSameErrorText`, `repeatedWrongPasswordsBlockSameAccountAcrossCalls`, `forcedPasswordChangeFlagIsVisibleInLoginResponse`.

**`ManualBookingRecoveryJpaTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/service/ManualBookingRecoveryJpaTest.java`

`requeuesPendingWithoutLosingReservationIdentity`, `requeuesCancellingButPreservesStatusAndBookingId`, `rejectsManualRequeueBeforeLimitOrAfterCompletion`.

**`R4UserBookingLockUnitTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/service/R4UserBookingLockUnitTest.java`

`pendingCreationRejectsUserDeletedBeforeLock`, `adminDeletionLocksUserBeforeCheckingBookingHistory`.

**`RegistrationServiceUnitTest` — 5**  
`booking-service/src/test/java/com/hotelbooking/booking/service/RegistrationServiceUnitTest.java`

`registersNormalizedUserWithUserRoleAndEncodedPassword`, `rejectsExistingUsernameBeforeEncoding`, `rejectsExistingEmailBeforeEncoding`, `rejectsMoreThan72Utf8Bytes`, `convertsUniqueConstraintRaceIntoConflict`.

**`UserPasswordServiceUnitTest` — 5**  
`booking-service/src/test/java/com/hotelbooking/booking/service/UserPasswordServiceUnitTest.java`

`nonexistentUserIsUnauthorized`, `incorrectCurrentPasswordIsRejected`, `unchangedPasswordIsRejected`, `excessiveUtf8PasswordIsRejected`, `validPasswordChangeHashesAndSaves`.

**`UserProfileServiceUnitTest` — 6**  
`booking-service/src/test/java/com/hotelbooking/booking/service/UserProfileServiceUnitTest.java`

`readsOwnProfile`, `readMissingUserIsUnauthorized`, `emptyPatchIsRejected`, `usernameCollisionIsRejected`, `normalizationAndPhoneClearingArePersisted`, `saveConstraintCollisionIsTranslated`.


### Hotel Service

**`DemoHotelsIdempotencyJpaTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/config/DemoHotelsIdempotencyJpaTest.java`

`renamedDemoRoomIsNotRecreated`, `rollbackRemovesNewlyCopiedDemoFiles`.

**`DemoHotelsInitializerMimeTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/config/DemoHotelsInitializerMimeTest.java`

`jpegAndPngUseCorrectTypes`, `unsupportedExtensionIsRejected`.

**`HotelTokenAccessTest` — 7**  
`hotel-service/src/test/java/com/hotelbooking/hotel/config/HotelTokenAccessTest.java`

`validServiceTokenIsAcceptedWithoutGatewayHeader`, `serviceTokenWithWrongAudienceIsDenied`, `serviceTokenWithWrongSubjectIsDenied`, `userJwtWithoutTrustedGatewayHeaderIsDenied`, `rejectedHotelRequestHasUniformErrorFieldsAndTraceId`, `userJwtWithCorrectGatewayHeaderIsAccepted`, `userJwtWithForgedGatewayHeaderIsDenied`.

**`OpenApiErrorResponseTest` — 1**  
`hotel-service/src/test/java/com/hotelbooking/hotel/config/OpenApiErrorResponseTest.java`

`centralizesErrorSchemasWithoutChangingResponseCodesOrSpecificDescriptions`.

**`HotelRoutesMvcTest` — 19**  
`hotel-service/src/test/java/com/hotelbooking/hotel/controller/HotelRoutesMvcTest.java`

`everyRouteIsDispatchedAndReturnsItsDeclaredStatus` (`@ParameterizedTest`), `everyProtectedRouteRejectsUnauthenticatedRequests` (`@ParameterizedTest`), `usersCannotInvokeAdminOrServiceRoutes` (`@ParameterizedTest`), `serviceCannotInvokeAdminRoutes` (`@ParameterizedTest`), `serviceCanReadCatalogWithoutGatewayHeader` (`@ParameterizedTest`), `adminCanReadStatistics`, `userCannotBypassGatewayForHotelRead`, `serviceTokenWithWrongSubjectIsRejected`, `serviceTokenWithWrongAudienceIsRejected`, `administratorCannotUseInternalRoomReservationEndpoint`, `userCannotReleaseRoomEvenWhenGatewayAuthenticated`, `invalidBearerTokenReturns401`, `invalidHotelIsRejectedByMvcValidation`, `roomListRequiresHotelId`, `invalidRoomCategoryIsRejectedBeforeServiceInvocation`, `releaseRequiresReservationId`, `missingHotelReturnsDomain404`, `conflictingHotelCreationReturns409`, `everyControllerHandlerHasAnHttpContractCase`.

**`RoomReservationConflictHttpTest` — 1**  
`hotel-service/src/test/java/com/hotelbooking/hotel/controller/RoomReservationConflictHttpTest.java`

`duplicateReservationIdReturnsHttp409AndStableCode`.

**`HotelAmenitiesBatchJpaTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelAmenitiesBatchJpaTest.java`

`pagedHotelsLoadAmenitiesWithoutExtraQueries`, `pagedRoomsLoadAmenitiesWithoutExtraQueries`.

**`HotelCaseInsensitiveUniquenessJpaTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelCaseInsensitiveUniquenessJpaTest.java`

`differentCaseCannotCreateDuplicateHotel`, `differentAddressAllowsSameHotelName`, `updateCannotDuplicateHotelWithDifferentCase`.

**`HotelDeletionWithPhotosJpaTest` — 4**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelDeletionWithPhotosJpaTest.java`

`commitRemovesHotelPhotoRowsFilesAndDirectory`, `rollbackKeepsHotelPhotoMetadataAndFiles`, `hotelWithRoomsIsNotDeletedAndPhotosStay`, `hotelWithoutPhotosCanBeDeleted`.

**`HotelOptimisticLockJpaTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelOptimisticLockJpaTest.java`

`stalePresentationMustNotOverwriteHotelNameAndAddress`, `staleDetailsMustNotOverwritePresentation`.

**`HotelPhotoDecodedImageJpaTest` — 5**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelPhotoDecodedImageJpaTest.java`

`validJpegAndPngAreAccepted`, `truncatedPngWithCorrectHeaderIsRejectedWithoutStoredFiles`, `truncatedJpegWithCorrectHeaderIsRejectedWithoutStoredFiles`, `truncatedWebpHeaderIsRejected`, `validWebpIsAccepted`.

**`HotelPhotoReadDeleteJpaTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelPhotoReadDeleteJpaTest.java`

`obtainedResourceRemainsReadableAfterPhotoDeletion`, `missingPhysicalFileReturnsPhotoNotFound`.

**`HotelPhotoServiceConcurrencyJpaTest` — 4**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelPhotoServiceConcurrencyJpaTest.java`

`uploadRollbackRemovesFileAndMetadata`, `deleteRollbackPreservesFileAndMetadata`, `deleteCommitRemovesFileAndMetadata`, `concurrentUploadsCannotExceedTenPhotos`.

**`HotelPresentationServiceUnitTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelPresentationServiceUnitTest.java`

`noFieldsIsRejectedWithoutDbAccess`, `unknownHotelIsRejected`, `updateDelegatesToEntityAndMapper`.

**`HotelRoomSelectionStrategyTest` — 4**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelRoomSelectionStrategyTest.java`

`defaultsToLeastBooked`, `adminUpdatePersistsOnManagedEntity`, `missingHotelFails`, `nullStrategyRejected`.

**`HotelSearchServiceUnitTest` — 10**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelSearchServiceUnitTest.java`

`missingHotelById`, `searchRejectsLargePage`, `searchRejectsNegativeRating`, `searchRejectsRatingAboveFive`, `searchRejectsUnknownSort`, `searchRejectsUnknownDirection`, `searchAcceptsValidFilters`, `getHotelByIdMapsLoadedHotel`, `searchAcceptsTextQueryAndMultipleAmenities`, `searchReadMethodsRetainReadonlyTransaction`.

**`HotelServiceCoverageUnitTest` — 10**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelServiceCoverageUnitTest.java`

`missingHotelStrategy`, `missingHotelStrategyUpdate`, `getStrategyFromEntity`, `updateStrategyLocksHotel`, `createDuplicateDoesNotSave`, `createTrimsFields`, `updateMissingHotel`, `deleteMissingHotel`, `deleteHotelContainingRoomsDenied`, `deleteEmptyHotelAlsoDeletesPhotoMetadata`.

**`HotelStatisticsServiceUnitTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelStatisticsServiceUnitTest.java`

`missingHotelFailsBeforeAggregation`, `aggregatesAllCountersAndExcludesTombstones`, `nullSumIsZero`.

**`HotelStatisticsSnapshotUnitTest` — 1**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelStatisticsSnapshotUnitTest.java`

`statisticsUsesConsistentRepeatableReadSnapshot`.

**`HotelStatisticsTombstoneJpaTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelStatisticsTombstoneJpaTest.java`

`releaseBeforeReserveCreatesTombstoneWithoutCancellation`, `completedCancellationCountsAsReleasedNotAsTombstone`, `businessCountersExcludeTechnicalTombstonesInMixedScenario`.

**`RoomCreationLockUnitTest` — 4**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomCreationLockUnitTest.java`

`creationLocksHotelRowBeforeSavingRoom`, `missingHotelDoesNotWriteRoom`, `duplicateRoomStillUsesExistingConflictHandling`, `deletingRoomWithPhotosRemovesMetadataBeforeRoomDeletion`.

**`RoomDetailsServiceUnitTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomDetailsServiceUnitTest.java`

`emptyPatchRejectedBeforeDatabaseAccess`, `unknownRoomRejected`, `updateUsesLockedReadAndMapsEntity`.

**`RoomPhotoServiceJpaTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomPhotoServiceJpaTest.java`

`uploadListReadSelectPrimaryAndDelete`, `invalidImageRejected`, `deletingRoomWithPhotosRemovesDatabaseRowsAndDiskFiles`.

**`RoomReservationCompensationTest` — 1**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationCompensationTest.java`

`failedBookingCompensationRestoresCounter`.

**`RoomReservationConcurrencyJpaTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationConcurrencyJpaTest.java`

`twoSimultaneousDifferentReservationIdsCannotBookOneRoom`, `sameReservationIdForDifferentRoomsReturnsConflict`, `concurrentReleasesForDifferentRoomsUseOneTombstone`.

**`RoomReservationDuplicateKeyTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationDuplicateKeyTest.java`

`uniqueKeyViolationBecomesReservationIdConflict`, `foreignKeyViolationIsNotDisguisedAsReservationConflict`.

**`RoomReservationReplayJpaTest` — 7**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationReplayJpaTest.java`

`guestCountIsPersistedAndIdenticalReplayDoesNotBookAgain`, `sameIdAndDatesButDifferentGuestCountReturns409Conflict`, `newReservationWithPastCheckInIsRejected`, `alreadyExistingHistoricalReservationCanBeReplayed`, `releasedTombstoneKeepsNullGuestCountAndBlocksReserve`, `internalHttpEndpointReturns200ForReplayAnd409ForChangedGuests`, `internalHttpEndpointRejectsPastCheckInWith400`.

**`RoomReservationRulesJpaTest` — 15**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationRulesJpaTest.java`

`successfulReservationCreatesTwoNightsAndCountsOnce`, `repeatedReserveSameIdIsIdempotent`, `sameIdCannotBeReusedWithDifferentDatesOrRoom`, `overlappingDatesInSameRoomAreRejected`, `checkoutExclusiveSoAdjacentBookingsAreAllowed`, `differentRoomsCanBeBookedOverIdenticalDates`, `confirmedCancellationFreesNightsAndDecrementsCount`, `compensationRestoresCountAndIsIdempotent`, `lateCompensationAfterNormalReleaseDoesNotDoubleDecrementCount`, `releaseBeforeReserveCreatesTombstone`, `releasingOtherRoomReservationIsRejected`, `unavailableRoomCannotBeReserved`, `capacityValidation`, `invalidDateRangeOrMoreThan365Nights`, `roomIsAvailableForPeriodAgainAfterRelease`.

**`RoomSearchServiceCoverageUnitTest` — 11**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomSearchServiceCoverageUnitTest.java`

`missingRoomGet`, `roomSearchRejectsHugePage`, `roomSearchRequiresHotelId`, `roomSearchRequiresExistingHotel`, `roomSearchRejectsInvalidCapacity`, `roomSearchRejectsNegativeMinPrice`, `roomSearchRejectsNegativeMaxPrice`, `roomSearchRejectsInvertedPriceRange`, `roomSearchRejectsUnknownSort`, `roomSearchRejectsBadDirection`, `roomSearchUsesPaginationAndSort`.

**`RoomSelectionPerHotelTest` — 1**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomSelectionPerHotelTest.java`

`missingOverrideUsesHotelStrategy`.

**`RoomServiceCoverageUnitTest` — 10**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomServiceCoverageUnitTest.java`

`createRoomRequiresExistingHotel`, `createRoomConvertsUniqueConstraint`, `missingRoomDelete`, `roomWithReservationCannotBeDeleted`, `deleteRoomRemovesPhotosThenRow`, `deleteDatabaseConflictIsResourceInUse`, `recommendationRejectsInvalidHotel`, `recommendationRejectsPastDate`, `recommendationRejectsLongStay`, `recommendationRejectsZeroGuests`.


### Booking Service — E2E

**`AdminPhotosStrategyExtendedE2ETest` — 8**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/AdminPhotosStrategyExtendedE2ETest.java`

`nonAdminCannotCreateHotelsOrRooms`, `anonymousCannotMutateHotel`, `administratorCreatesHotelAndRoomsAndCanReadThem`, `strategyIsPerHotelAndAdminOnly`, `hotelPhotoLifecycleAndAuthorization`, `roomPhotoLifecycleAndAuthorization`, `unsupportedImageMustBeRejected`, `deletingRoomRemovesAssociatedPhotoMetadata`.

**`AuthenticationSecurityExtendedE2ETest` — 5**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/AuthenticationSecurityExtendedE2ETest.java`

`wrongPasswordRejectedAndValidPasswordWorks`, `distributedAttemptsAgainstOneAccountAreBounded`, `garbageJwtAndMissingJwtCannotReadProtectedApi`, `ordinaryUserCannotReadAdminUsers`, `sameJwtConcurrentChecksDoNotReturn5xx`.

**`ExtendedE2ESupportFixtureTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ExtendedE2ESupportFixtureTest.java`

`findsManualHttpFileFromProjectAndModuleDirectories`, `extractsAdminLoginInsteadOfUserLogin`, `currentManualHttpFixtureHasReadableAdminLogin`.

**`HotelBookingLoadE2ETest` — 11**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/HotelBookingLoadE2ETest.java`

`parallelAuthenticatedRequests100`, `catalogRead5000`, `competingForOneRoom200`, `sameIdempotencyKey200`, `parallelAutoSelect200`, `parallelCancellation100`, `overlappingAndAdjacentDates200`, `mixedTraffic2000`, `parallelSecurity500`, `eventualSagaAndCountersConverge`, `parallelPhotoReads1000VerifySha256`.

**`HotelBookingSagaFaultE2ETest` — 4**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/HotelBookingSagaFaultE2ETest.java`

`hotelOutOfService200BookingsFailWithoutFalseConfirmation`, `cancellation100DuringOutageEventuallyCompletes`, `bookedRoomIsNotLostDuringReadAndCreationOutage`, `exhaustedRecoveryAndAdminManualRequeue`.

**`HotelBookingSagaLoadE2ETest` — 6**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/HotelBookingSagaLoadE2ETest.java`

`changedPayloadWithSameIdempotencyKey100`, `reserveCancelRebook300Cycles`, `cancelVsNewBookingRace200Requests`, `fullyBookedHotelAutoSelect500`, `exactTimesBookedAfter250ReservationsAndCancellation`, `mixedFiveMinuteSagaSoak`.

**`HotelBookingSmokeE2ETest` — 1**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/HotelBookingSmokeE2ETest.java`

`userCanRegisterLoginAndOpenCatalog`.

**`ResilienceBookingE2ETest` — 9**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceBookingE2ETest.java`

`bookingAndCancelRestoreCounter`, `simultaneous200AttemptsSameRoomCannotDoubleBook`, `hundredReplaysSameKeyProduceSingleReservation`, `differentPayloadSameKeyCannotCreateSecondBooking`, `invalidDateRangeRejectedWithoutIncrementingCounter`, `zeroGuestCountRejected`, `excessiveGuestCountRejected`, `noKeyBookingMustBeRejected`, `hundredParallelCounterReadsAgree`.

**`ResilienceBoundaryE2ETest` — 15**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceBoundaryE2ETest.java`

`missingHotelReturns404`, `missingRoomReturns404`, `missingBookingReturns404`, `invalidRoomCategoryReturnsClientError`, `invalidRoomSortReturnsClientError`, `invalidRecommendationDateReturnsClientError`, `userCannotDeleteRoom`, `anonymousCannotDeleteRoom`, `anonymousCannotResetStrategy`, `userCannotDeleteHotel`, `invalidStrategyRejected`, `hotelsAndRoomsHaveValidPagination`, `photosForMissingHotelReturn404`, `photosForMissingRoomReturn404`, `unknownHotelStatisticsReturn404`.

**`ResilienceMediaE2ETest` — 15**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceMediaE2ETest.java`

`hotelPhotosStartEmpty`, `roomPhotosStartEmpty`, `hotelPhotoUploadAndExactDownload`, `roomPhotoUploadAndExactDownload`, `userCannotUploadHotelPhoto`, `userCannotUploadRoomPhoto`, `anonymousCannotUploadPhoto`, `invalidHotelImageIsRejected`, `invalidRoomImageIsRejected`, `deletedHotelPhotoNotDownloadable`, `deletedRoomPhotoNotDownloadable`, `userCannotDeleteHotelPhoto`, `userCannotDeleteRoomPhoto`, `concurrentDownloadsKeepBytesIntact`, `roomDeleteWithPhotoHasNoDanglingRecord`.

**`ResilienceReadLoadE2ETest` — 10**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceReadLoadE2ETest.java`

`twoHundredRoomListReadsRespond200`, `twoHundredMyBookingsReadsRespond200`, `alternatingUserTokensKeepDataPrivate`, `repeatedAdminListIsAccessible`, `malformedJsonHotelCreationFailsWith400`, `blankHotelNameIsRejected`, `incompleteLoginDoesNotReturnToken`, `unsupportedContentTypeDoesNotReturn500`, `userCannotCreateRoomEvenWithValidHotel`, `strategyReadRemainsAvailableAfterRepeatedCatalogReads`.

**`ResilienceSecurityE2ETest` — 19**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceSecurityE2ETest.java`

`anonymousProfileRejected`, `anonymousBookingsRejected`, `anonymousHotelMutationRejected`, `malformedJwtCannotOpenProfile`, `malformedJwtCannotListBookings`, `malformedJwtCannotReadHotelCatalog`, `userCannotListAllUsers`, `userCannotListAllBookings`, `userCannotCreateHotel`, `userCannotModifyStrategy`, `authenticatedCatalogRespondsNormally`, `authenticatedOwnBookingsRespondNormally`, `twoHundredConcurrentProfileReadsHaveNoErrors`, `twoHundredConcurrentCatalogReadsHaveNoErrors`, `twoHundredInvalidTokensNeverSucceed`, `wrongPasswordNeverIssuesToken`, `nonexistentAccountNeverIssuesToken`, `invalidJsonCannotAuthenticate`, `unknownRouteMustNotLeakServerFailure`.

**`ResilienceStrategyE2ETest` — 10**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceStrategyE2ETest.java`

`newHotelDefaultsToLeastBooked`, `adminCanSetMostExpensive`, `adminCanRestoreLeastBooked`, `differentHotelsKeepIndependentStrategies`, `strategyStatePersistsAcrossSeparateHttpRequests`, `userCannotSetMostExpensive`, `anonymousCannotSetStrategy`, `invalidStrategyDoesNotOverwriteCurrent`, `concurrentReadAfterUpdateIsConsistent`, `switchBetweenModesLeavesNoUnexpectedValue`.

**`TwoHundredUserAuthenticationE2ETest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/TwoHundredUserAuthenticationE2ETest.java`

`twoHundredDistinctUsersLoginAtOnce`, `twoHundredSimultaneousWrongPasswordsCannotCauseServerFailure`.
