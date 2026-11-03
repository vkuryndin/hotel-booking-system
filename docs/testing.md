# Тестирование

## Состав тестового набора

Тесты охватывают бизнес-логику Booking Service и Hotel Service, JWT и доступ, Saga, работу с H2, HTTP-маршруты, отказоустойчивость и конкурентные запросы. Каталог ниже перечисляет объявления тестовых методов в текущем `src/test/java`. Параметризованные тесты могут выполняться несколько раз для одного объявления. Для фактического результата используйте отчёты JUnit.

| Группа | Классов | Объявлений тестовых методов |
|---|---:|---:|
| API Gateway | 3 | 17 |
| Booking Service (локальные тесты) | 42 | 221 |
| Hotel Service | 31 | 149 |
| E2E (Booking Service) | 14 | 118 |
| **Всего** | **90** | **505** |

Классы `ExtendedE2ESupport` и `SagaE2ESupport` — вспомогательные. `UiDataSeedManualTest` — отдельная утилита для нагрузочного заполнения H2, она **не входит** в прогон JUnit. `@ParameterizedTest` может дать несколько фактических запусков из одного объявления; E2E пропускаются в обычном `clean test`, если не активирована системная опция.

## Локальные unit, MVC, JPA и интеграционные тесты

Запуск из корня репозитория:

```powershell
.\gradlew.bat clean test
```

На macOS/Linux: `./gradlew clean test`. Результаты: `<module>/build/reports/tests/test/index.html` и JaCoCo в `<module>/build/reports/jacoco/test/html/index.html` после генерации соответствующего отчёта.

Набор проверяет создание и отмену бронирования, валидацию дат, права USER/ADMIN, обработку ошибок, ограничение частоты запросов, правила выбора номера, JPA-ограничения, блокировки, сценарии компенсации и восстановления, а также маршруты и OpenAPI. Проверки формата Java-кода выполняются отдельно:

```powershell
.\gradlew.bat checkstyleMain spotbugsMain spotlessJavaCheck
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

**Rate Limiting:** регистрация ограничена 5 попытками с IP за час (включая неуспешные), вход — 20 попытками с IP за минуту. Основной регрессионный прогон и дополнительные негативные проверки выполняются раздельно. В разделе 13 файла есть инструкции по паузам: для регистрации при исчерпании лимита ориентир — 61 минута или `Retry-After` + 2 секунды, для входа — 61 секунда или `Retry-After` + 2 секунды. Эти интервалы относятся к одному экземпляру Booking Service; отключать Rate Limiting для тестирования не требуется.

Разделы с отключением/перезапуском сервисов выполняются отдельно от основной последовательности; при повторном создании ресурсов используйте новые данные и Idempotency-Key либо очищенную H2.

## Дополнительное нагрузочное заполнение интерфейса

Класс `UiDataSeedManualTest` в пакете `booking.e2e` содержит `main()` и предназначен для **ручного** запуска при работающих сервисах. Он не запускается обычным JUnit и может создать большой объём данных; используйте только отдельное тестовое окружение.

## Каталог тестовых классов и методов

Ниже перечислены все Java-классы с JUnit-тестовыми методами, краткое назначение каждого класса и имена его тестовых методов. Названия приведены без изменений; в скобках указан вид JUnit-аннотации, если это не обычный `@Test`.

### API Gateway

**`GatewaySecretFilterTest` — 3**  
`api-gateway/src/test/java/com/hotelbooking/gateway/config/GatewaySecretFilterTest.java`

**Назначение:** Проверяет подстановку доверенного внутреннего секрета Gateway и защиту IP-заголовков от подмены клиентом.

`forgedClientSecretIsOverwrittenBeforeForwarding`, `gatewaySuppliesSecretWhenClientSentNone`, `forgedVerifiedIpIsRemovedAndActualPeerIsUsed`.

**`GatewaySecurityErrorHandlerTest` — 2**  
`api-gateway/src/test/java/com/hotelbooking/gateway/config/GatewaySecurityErrorHandlerTest.java`

**Назначение:** Проверяет формат JSON-ошибок 401/403 и сохранение Bearer-заголовка при отказе в доступе.

`authenticationFailurePreservesBearerChallengeAndReturnsErrorJson`, `accessDeniedReturnsErrorJsonWithoutChangingForbiddenStatus`.

**`JwtRevocationFilterTest` — 12**  
`api-gateway/src/test/java/com/hotelbooking/gateway/config/JwtRevocationFilterTest.java`

**Назначение:** Проверяет статус и отзыв JWT, принудительную смену пароля, разграничение ролей, fail-closed и совместное выполнение проверок одинакового токена.

`rejectedRequestsHaveUniformJsonErrorAndTraceId`, `approvedJwtStatusForwardsHotelRequest`, `revokedJwtIsRejectedAsUnauthorized`, `forcedPasswordChangeBlocksOrdinaryRequests`, `forcedPasswordChangeAllowsPasswordChangeEndpoint`, `bookingServiceOutageFailsClosed`, `noAuthenticationReturnsUnauthorizedWithoutBackendCheck`, `unrecognizedRoleCannotReachHotelOrTokenStatus`, `loginIsNotBlockedByMissingJwtOrBookingStateCheck`, `registrationIsNotBlockedByMissingJwtOrBookingStateCheck`, `adminRoleIsAccepted`, `simultaneousRequestsWithSameTokenShareStatusCall`.


### Booking Service — локальные тесты

**`HotelServiceClientRetryTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/client/HotelServiceClientRetryTest.java`

**Назначение:** Проверяет повтор сетевых вызовов Hotel Service при временной ошибке и прекращение повторов при открытом Circuit Breaker.

`openCircuitBreakerDoesNotRetry`, `transientNetworkFailureStillRetriesTwice`.

**`OpenApiErrorResponseTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/config/OpenApiErrorResponseTest.java`

**Назначение:** Проверяет модель ErrorResponse в OpenAPI Booking Service, разрешение ссылок на схему и скрытие служебного маршрута проверки JWT.

`centralizesErrorSchemasWithoutChangingResponseCodesOrSpecificDescriptions`, `errorResponseReferenceResolvesToSerializedSchemaWithActualFields`, `internalGatewayTokenStatusIsNotPublishedInSwagger`.

**`RestClientConfigTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/config/RestClientConfigTest.java`

**Назначение:** Проверяет преобразование транспортных сбоев межсервисного клиента в ожидаемую доменную ошибку доступности.

`transportIllegalStateBecomesServiceUnavailable`, `unrelatedTransportArgumentErrorIsNotConverted`.

**`UserTokenStateWebConfigTest` — 8**  
`booking-service/src/test/java/com/hotelbooking/booking/config/UserTokenStateWebConfigTest.java`

**Назначение:** Проверяет актуальность пользовательского JWT, отзыв через версию и идентичность токена, смену пароля и защиту внутреннего маршрута.

`missingGatewaySecretIsRejectedBeforeUserLookup`, `validCurrentJwtPasses`, `missingUserRevokesJwt`, `roleOrPasswordChangeInvalidatesOldTokenVersion`, `changedIdentityInvalidatesToken`, `forcedPasswordChangeBlocksEverythingExceptChangePassword`, `serviceTokenBypassesUserDatabaseLookup`, `nonNumericSubjectIsRejected`.

**`AdminUserSearchControllerTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/controller/AdminUserSearchControllerTest.java`

**Назначение:** Проверяет доступ ADMIN к поиску пользователей и передачу поискового запроса в сервис чтения.

`userSearchEndpointRemainsAdminOnly`, `controllerPassesOptionalQueryToExistingReadService`.

**`BookingAllAdminSecurityTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/controller/BookingAllAdminSecurityTest.java`

**Назначение:** Проверяет ограничение списка всех бронирований ролью ADMIN на уровне контроллера и сервиса.

`allBookingsEndpointRequiresAdminRole`, `allBookingsServiceAlsoRequiresAdminRole`.

**`BookingRecoveryAdminSecurityTest` — 1**  
`booking-service/src/test/java/com/hotelbooking/booking/controller/BookingRecoveryAdminSecurityTest.java`

**Назначение:** Проверяет защиту административных операций восстановления Saga по роли.

`adminOnlyGuardIsDeclaredForBothEndpoints`.

**`BookingRoutesMvcTest` — 22**  
`booking-service/src/test/java/com/hotelbooking/booking/controller/BookingRoutesMvcTest.java`

**Назначение:** Проверяет маршрутизацию HTTP-запросов Booking Service, требования JWT и ролей, валидацию и контракт кодов ответов.

`everyRouteIsDispatchedToTheCorrectController` (`@ParameterizedTest`), `protectedRoutesRejectRequestsWithoutJwt` (`@ParameterizedTest`), `usersCannotInvokeAdminRoutes` (`@ParameterizedTest`), `serviceRoleCannotInvokeAdminRoutes` (`@ParameterizedTest`), `adminRoleCanReadUserProfile`, `serviceRoleCannotReadUserProfile`, `invalidBearerTokenIsRejectedBeforeController`, `missingGatewayHeaderIsRejectedEvenWithValidJwt`, `revokedAccountIsRejectedBeforeController`, `passwordChangeRequirementBlocksOtherEndpoints`, `passwordChangeEndpointRemainsAccessibleWhenChangeIsRequired`, `publicLoginWorksWithoutJwtButNotWithoutGateway`, `loginSerializesAllAccountIdentityFields`, `userSearchBindsQueryAndPageable`, `adminSearchSerializesPageContent`, `missingBookingIdempotencyKeyReturns400`, `incompleteRegistrationReturns400`, `malformedBookingStatusReturns400`, `unknownBookingReturnsDomain404`, `conflictingRegistrationReturns409`, `bookingSagaUnavailableReturns503`, `everyControllerHandlerHasAnHttpContractCase`.

**`BookingRequestValidationMvcTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/dto/request/BookingRequestValidationMvcTest.java`

**Назначение:** Проверяет MVC-валидацию дат и количества гостей и структуру сообщения об ошибке с traceId.

`tooManyGuestsReturnValidationErrorWithTraceId`, `checkoutTodayReturnsValidationError`, `validBoundaryRequestPassesMvcValidation`.

**`BookingRequestValidationTest` — 8**  
`booking-service/src/test/java/com/hotelbooking/booking/dto/request/BookingRequestValidationTest.java`

**Назначение:** Проверяет граничные значения дат и числа гостей, обязательные поля и значения по умолчанию.

`zeroGuestsAreRejected`, `guestCountAboveTwentyIsRejected`, `oneAndTwentyGuestsAreAccepted`, `omittedGuestCountDefaultsToOne`, `checkoutTodayOrYesterdayIsRejected`, `omittedCheckoutIsRejected`, `pastCheckinIsStillRejected`, `todayCheckinTomorrowCheckoutAreAccepted`.

**`BookingErrorHandlingTest` — 9**  
`booking-service/src/test/java/com/hotelbooking/booking/exception/BookingErrorHandlingTest.java`

**Назначение:** Проверяет единый формат HTTP-ошибок, статусы, коды, ошибки полей и отсутствие утечки внутренних деталей.

`businessErrorHasPreservedStatusCodeAndTraceId`, `existingConflictCodeIsPreserved`, `validationErrorsKeepFieldErrors`, `malformedJsonHasOurStandardErrorFormat`, `missingHeaderAndUnsupportedMethodAreMapped`, `responseStatusExceptionKeepsItsHttpStatus`, `accessDeniedIsStill403`, `unexpectedErrorIs500ButItsDetailsAreNotLeaked`, `gatewayInterceptorRejectionAlsoCarriesTraceId`.

**`FailedAccountLoginLimiterTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/security/FailedAccountLoginLimiterTest.java`

**Назначение:** Проверяет блокировку многократных неверных входов для одной учётной записи и истечение окна ограничений.

`fifthFailureBlocksSameAccountButNotOtherAccount`, `expiredWindowIsUnlocked`.

**`LoginRegistrationRateLimiterCapacityTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/security/LoginRegistrationRateLimiterCapacityTest.java`

**Назначение:** Проверяет ограничение объёма in-memory лимитера при большом числе уникальных ключей и работу окон разной длины.

`manyUniqueIdentitiesNeverGrowBeyondLimit`, `differentWindowLengthsStillWork`.

**`PublicAuthRateLimitTest` — 7**  
`booking-service/src/test/java/com/hotelbooking/booking/security/PublicAuthRateLimitTest.java`

**Назначение:** Проверяет лимиты регистрации и входа по IP, Retry-After и защиту от подмены заголовков адреса.

`loginThrottlesThirdAttemptWithRetryAfter`, `registrationHasIndependentHourlyLimit`, `limitsArePerIp`, `cannotBypassLimitsUsingSpoofedForwardedHeader`, `trustedGatewaySeparatesClientAddresses`, `untrustedClientCannotSpoofVerifiedHeader`, `otherMethodsAndRoutesRemainUnaffected`.

**`RateLimiterAdditionalUnitTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/security/RateLimiterAdditionalUnitTest.java`

**Назначение:** Проверяет точную границу лимита запросов, независимость ключей и конкурентное выполнение.

`exactLimitAndNextAttemptRejected`, `independentKeysHaveIndependentWindows`, `concurrentCallsCannotAllBypassLimit`.

**`SensitiveActionRateLimitInterceptorTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/security/SensitiveActionRateLimitInterceptorTest.java`

**Назначение:** Проверяет лимиты смены и сброса пароля и других чувствительных операций по пользователям и маршрутам.

`passwordChangeIsLimitedPerAuthenticatedUser`, `adminResetAndUpdateUseIndependentLimits`, `otherMethodsAreNotLimited`.

**`ServiceTokenProviderTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/security/ServiceTokenProviderTest.java`

**Назначение:** Проверяет служебный JWT: издателя, аудиторию, роль, срок действия и отсутствие пользовательских полей.

`serviceTokenUsesCorrectIssuerSubjectAudienceAndRole`, `serviceTokenExpiresAfterOneMinute`, `serviceTokenDoesNotHaveUserTokenVersionOrIdentity`.

**`AdminConcurrencyJpaTest` — 1**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AdminConcurrencyJpaTest.java`

**Назначение:** Проверяет сохранение хотя бы одного администратора при конкурентных изменениях ролей.

`competingDemotionsPreserveAtLeastOneAdministrator`.

**`AdminProtectionTest` — 7**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AdminProtectionTest.java`

**Назначение:** Проверяет запреты самоудаления, самопонижения, удаления последнего администратора и пользователя с бронированиями.

`adminCannotDemoteSelfEvenWithMultipleAdmins`, `lastAdminCannotBeDemotedByAnotherAdmin`, `demotionOfOtherAdminWorksWhenAnotherAdminRemains`, `adminCannotDeleteSelf`, `lastAdminCannotBeDeletedByAnotherAdmin`, `otherAdminCanBeDeletedIfAtLeastOneAdminRemains`, `bookingHistoryStillPreventsUserDeletion`.

**`AdminReadCoverageUnitTest` — 4**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AdminReadCoverageUnitTest.java`

**Назначение:** Проверяет чтение пользователей администратором, пагинацию, сортировку и обработку отсутствующих записей.

`pageTooLargeRejected`, `missingUserRejected`, `pageUsesDeterministicIdSort`, `existingUserMapped`.

**`AdminUserSearchJpaTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AdminUserSearchJpaTest.java`

**Назначение:** Проверяет поиск пользователей по полям без учёта регистра, экранирование спецсимволов и пагинацию JPA.

`matchesUsernameFirstNameLastNameAndEmailCaseInsensitively`, `literalUnderscoreIsNotWildcard`, `paginationRestrictsResultSetAndKeepsIdOrder`.

**`AdminUserSearchServiceTest` — 4**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AdminUserSearchServiceTest.java`

**Назначение:** Проверяет нормализацию поискового запроса, экранирование шаблонов и ограничение размера страницы.

`nonblankSearchIsTrimmedLowercasedAndKeepsPagination`, `wildcardsAreEscapedAndCannotExpandSearch`, `emptySearchRetainsOriginalRepositoryPath`, `pageSizeLimitAlsoAppliesWhenSearching`.

**`AdminWriteCoverageUnitTest` — 9**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AdminWriteCoverageUnitTest.java`

**Назначение:** Проверяет административное изменение пользователей, конфликты уникальности и ограничения удаления.

`duplicateUsernameRejected`, `duplicateEmailRejected`, `passwordTooLongRejected`, `concurrentUniqueViolationIsConflict`, `cannotDeleteSelf`, `missingTargetUserRejected`, `lastAdminCannotBeDeleted`, `bookingHistoryPreventsDeletion`, `deletionFlushesChanges`.

**`AppUserOptimisticLockJpaTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/service/AppUserOptimisticLockJpaTest.java`

**Назначение:** Проверяет защиту прав администратора, версии токена и флага смены пароля от устаревших JPA-обновлений.

`staleProfileUpdateMustNotRestoreDemotedAdminOrOldTokenVersion`, `stalePasswordUpdateMustNotClearAdminPasswordResetFlag`.

**`BookingIdempotencyConcurrencyJpaTest` — 1**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingIdempotencyConcurrencyJpaTest.java`

**Назначение:** Проверяет уникальность пары пользователь–Idempotency-Key при конкурентном создании бронирований.

`sameUserAndKeyCannotCreateTwoPendingBookings`.

**`BookingQueryServiceTest` — 11**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingQueryServiceTest.java`

**Назначение:** Проверяет права доступа к бронированиям, фильтры по статусу и датам, сортировку и пагинацию.

`unknownBookingThrowsNotFound`, `anotherUsersBookingIsForbidden`, `ownerCanReadBooking`, `adminCanReadAnotherUsersBooking`, `myBookingsUseUserFilterAndStableSort`, `myBookingsFilterByStatus`, `checkoutFilterRequiresConfirmedStatus`, `confirmedCheckoutFilterUsesDedicatedRepositoryQuery`, `adminListWithoutStatusUsesAllRecords`, `adminListWithStatusUsesStatusFilter`, `maximumPageSizeIsEnforcedForBothUserAndAdmin`.

**`BookingRecoveryPaginationJpaTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingRecoveryPaginationJpaTest.java`

**Назначение:** Проверяет постраничный обход PENDING и CANCELLING без пропуска записей для восстановления.

`pendingQueryReturnsNextPageWithoutSkippingRecords`, `cancellationQueryReturnsNextPageWithoutSkippingRecords`.

**`BookingRecoveryRaceTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingRecoveryRaceTest.java`

**Назначение:** Проверяет гонки между подтверждением бронирования и Recovery и защиту активной Saga от преждевременной обработки.

`recoveryWins_confirmationMustFail`, `confirmationWins_recoveryMustNotCancel`, `activeSagaMustNotBeRecoveredWhenCreatedAtIsOld`.

**`BookingRecoveryServiceTest` — 8**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingRecoveryServiceTest.java`

**Назначение:** Проверяет восстановление незавершённого создания и отмены, компенсацию, повтор после ошибок и обход больших наборов.

`pendingRecoveryCompensatesBeforeMarkingFailed`, `failedCompensationLeavesPendingForNextRecovery`, `confirmedOrFreshBookingIsSkippedByTransactionalRecheck`, `cancellationRecoveryUsesReleaseNotCompensation`, `failedCancellationReleaseLeavesCancellingForNextRecovery`, `cancellationRecoverySkipsBookingNoLongerCancelling`, `pendingRecoveryProcessesMoreThanTenCandidates`, `cancellationRecoveryProcessesMoreThanTenCandidates`.

**`BookingSagaStateJpaTest` — 13**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingSagaStateJpaTest.java`

**Назначение:** Проверяет сохранение идентичности Saga, допустимые переходы состояний, права отмены и ограничения идемпотентности JPA.

`createPendingStoresReservationIdFingerprintAndGuestCount`, `pendingCanBeConfirmedOnceOnly`, `recoveryMarksPendingRecoveringAndDisablesLateConfirmation`, `recoverySkipsYoungPendingAndConfirmed`, `compensationRecordsFailedAndProhibitsConfirmation`, `confirmedBookingCannotBeCompensatedAsFailedCreation`, `cancelledCreationCannotBeCancelledAgainWithNewOutcome`, `onlyOwnerOrAdminCanStartCancellation`, `cancellationIsRecoverableAndEventuallyIdempotent`, `cannotFinishCancellationBeforeItStarted`, `cannotSwitchRoomAfterConfirmationOrWhileRecovering`, `sameKeyDifferentRequestIsRejected`, `duplicateUserAndIdempotencyKeyFailsAtDatabaseConstraint`.

**`BookingServiceSagaTest` — 19**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingServiceSagaTest.java`

**Назначение:** Проверяет создание, повторы, подбор, конфликты, ошибки Hotel Service, компенсацию, отмену и состояния Saga.

`successfulCreationConfirmsExactlyOnceAndNeverCompensates`, `replayOfConfirmedKeyReturnsOriginalBookingWithoutAnotherReservation`, `pendingAndRecoveringKeysCannotStartAnotherSaga`, `previouslyFailedKeyCannotStartAnotherSaga`, `repeatedRoomConflictProducesSameDomainError`, `hotelConflictCompensatesAndRecordsRoomConflict`, `unavailableHotelCompensatesAndMarksFailed`, `confirmationFailureAfterHotelReservationStillCompensates`, `failedCompensationDoesNotMarkSagaCancelledSoRecoveryCanRetry`, `invalidReservationResponseIsCompensated`, `autoSelectionFallsBackAfterConflictWithoutCompensatingFirstCandidate`, `allAutoCandidatesOccupiedCompensatesLatestAttempt`, `cancellationUsesOrdinaryReleaseAndThenFinishes`, `repeatedCancellationLoadsExistingBookingWithoutCallingHotelAgain`, `failedReleaseLeavesCancellationInProgressForRecovery`, `invalidDatesAndTooLongStayAreRejectedBeforeHotelCall`, `disabledRoomIsRejectedBeforePendingState`, `notEnoughCapacityIsRejectedBeforePendingState`, `replayOfCancelledBookingReturnsActualStatus`.

**`BookingValidationCoverageUnitTest` — 10**  
`booking-service/src/test/java/com/hotelbooking/booking/service/BookingValidationCoverageUnitTest.java`

**Назначение:** Проверяет правила дат, длительности, вместимости и допустимых полей ручного и автоматического бронирования.

`checkInIsRequired`, `checkoutIsRequired`, `checkoutMustBeAfterCheckin`, `maximumStayIs365Nights`, `roomIdMustBePositive`, `manualBookingRejectsHotelId`, `autoBookingRequiresPositiveHotelId`, `autoBookingRejectsNoRecommendations`, `manualBookingRejectsUnavailableRoom`, `manualBookingRejectsInsufficientCapacity`.

**`ExhaustedSagaMonitorTest` — 1**  
`booking-service/src/test/java/com/hotelbooking/booking/service/ExhaustedSagaMonitorTest.java`

**Назначение:** Проверяет обнаружение исчерпавших попытки Saga без изменения их состояния.

`checksExhaustedWithoutMutatingState`.

**`HistoricalIdempotencyReplayTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/service/HistoricalIdempotencyReplayTest.java`

**Назначение:** Проверяет повтор ранее созданного бронирования с историческими датами и валидацию новых запросов.

`successfulHistoricalReplayReturnsOriginalBookingWithoutHotelCall`, `newBookingWithPastDatesStillReturnsFieldValidationErrors`, `guestCountStillValidatedForNewBooking`.

**`JwtServiceTest` — 8**  
`booking-service/src/test/java/com/hotelbooking/booking/service/JwtServiceTest.java`

**Назначение:** Проверяет выпуск пользовательского JWT, подпись, поля, срок действия и отклонение недействительных токенов.

`signedTokenHasExpectedSubjectIssuerRoleAndIdentity`, `adminTokenIncludesAdminRole`, `lifetimeIsOneHourAndReportedInSeconds`, `tokenSignedByAnotherKeyIsRejected`, `modifiedPayloadIsRejectedBySignatureValidation`, `wrongIssuerIsRejected`, `expiredTokenIsRejected`, `roleAndTokenVersionChangeAreEmbeddedInNewToken`.

**`LoginIdentityResponseUnitTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/service/LoginIdentityResponseUnitTest.java`

**Назначение:** Проверяет поля личности в ответе входа, совместимость DTO и отсутствие JWT при неудачном входе.

`loginIncludesIdentityEvenWhenPasswordChangeIsRequired`, `originalFourArgumentConstructorRemainsUsable`, `failedLoginDoesNotGenerateJwtOrReturnIdentity`.

**`LoginServiceTest` — 6**  
`booking-service/src/test/java/com/hotelbooking/booking/service/LoginServiceTest.java`

**Назначение:** Проверяет успешный и неуспешный вход, защиту от перечисления пользователей и блокировку после неверных паролей.

`validCredentialsReturnBearerTokenAndLifetime`, `missingUserRejectsWithoutGeneratingJwt`, `wrongPasswordRejectsWithoutGeneratingJwt`, `absentAndWrongCredentialsExposeSameErrorText`, `repeatedWrongPasswordsBlockSameAccountAcrossCalls`, `forcedPasswordChangeFlagIsVisibleInLoginResponse`.

**`ManualBookingRecoveryJpaTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/service/ManualBookingRecoveryJpaTest.java`

**Назначение:** Проверяет административную повторную постановку Saga в Recovery с сохранением идентичности и состояния.

`requeuesPendingWithoutLosingReservationIdentity`, `requeuesCancellingButPreservesStatusAndBookingId`, `rejectsManualRequeueBeforeLimitOrAfterCompletion`.

**`R4UserBookingLockUnitTest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/service/R4UserBookingLockUnitTest.java`

**Назначение:** Проверяет порядок блокировки пользователя при создании бронирования и удалении аккаунта.

`pendingCreationRejectsUserDeletedBeforeLock`, `adminDeletionLocksUserBeforeCheckingBookingHistory`.

**`RegistrationServiceUnitTest` — 5**  
`booking-service/src/test/java/com/hotelbooking/booking/service/RegistrationServiceUnitTest.java`

**Назначение:** Проверяет регистрацию, нормализацию данных, хеширование пароля и конфликты уникальности.

`registersNormalizedUserWithUserRoleAndEncodedPassword`, `rejectsExistingUsernameBeforeEncoding`, `rejectsExistingEmailBeforeEncoding`, `rejectsMoreThan72Utf8Bytes`, `convertsUniqueConstraintRaceIntoConflict`.

**`UserPasswordServiceUnitTest` — 5**  
`booking-service/src/test/java/com/hotelbooking/booking/service/UserPasswordServiceUnitTest.java`

**Назначение:** Проверяет смену пароля, проверку исходного секрета и ограничения нового пароля.

`nonexistentUserIsUnauthorized`, `incorrectCurrentPasswordIsRejected`, `unchangedPasswordIsRejected`, `excessiveUtf8PasswordIsRejected`, `validPasswordChangeHashesAndSaves`.

**`UserProfileServiceUnitTest` — 6**  
`booking-service/src/test/java/com/hotelbooking/booking/service/UserProfileServiceUnitTest.java`

**Назначение:** Проверяет чтение и изменение профиля, нормализацию и обработку конфликтов данных.

`readsOwnProfile`, `readMissingUserIsUnauthorized`, `emptyPatchIsRejected`, `usernameCollisionIsRejected`, `normalizationAndPhoneClearingArePersisted`, `saveConstraintCollisionIsTranslated`.


### Hotel Service

**`DemoHotelsIdempotencyJpaTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/config/DemoHotelsIdempotencyJpaTest.java`

**Назначение:** Проверяет повторную инициализацию демоданных и очистку скопированных фотографий при откате.

`renamedDemoRoomIsNotRecreated`, `rollbackRemovesNewlyCopiedDemoFiles`.

**`DemoHotelsInitializerMimeTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/config/DemoHotelsInitializerMimeTest.java`

**Назначение:** Проверяет MIME-типы демофотографий и отказ при неподдерживаемом расширении.

`jpegAndPngUseCorrectTypes`, `unsupportedExtensionIsRejected`.

**`HotelTokenAccessTest` — 7**  
`hotel-service/src/test/java/com/hotelbooking/hotel/config/HotelTokenAccessTest.java`

**Назначение:** Проверяет служебный JWT, внутренний секрет Gateway, запрет поддельных заголовков и формат отказа в доступе.

`validServiceTokenIsAcceptedWithoutGatewayHeader`, `serviceTokenWithWrongAudienceIsDenied`, `serviceTokenWithWrongSubjectIsDenied`, `userJwtWithoutTrustedGatewayHeaderIsDenied`, `rejectedHotelRequestHasUniformErrorFieldsAndTraceId`, `userJwtWithCorrectGatewayHeaderIsAccepted`, `userJwtWithForgedGatewayHeaderIsDenied`.

**`OpenApiErrorResponseTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/config/OpenApiErrorResponseTest.java`

**Назначение:** Проверяет модель ErrorResponse в OpenAPI Hotel Service, разрешение ссылок на схему и сохранение кодов ответов.

`centralizesErrorSchemasWithoutChangingResponseCodesOrSpecificDescriptions`, `errorResponseReferenceResolvesToSerializedSchemaWithActualFields`.

**`HotelRoutesMvcTest` — 19**  
`hotel-service/src/test/java/com/hotelbooking/hotel/controller/HotelRoutesMvcTest.java`

**Назначение:** Проверяет маршруты Hotel Service, права USER/ADMIN/SERVICE, валидацию и HTTP-коды контроллеров.

`everyRouteIsDispatchedAndReturnsItsDeclaredStatus` (`@ParameterizedTest`), `everyProtectedRouteRejectsUnauthenticatedRequests` (`@ParameterizedTest`), `usersCannotInvokeAdminOrServiceRoutes` (`@ParameterizedTest`), `serviceCannotInvokeAdminRoutes` (`@ParameterizedTest`), `serviceCanReadCatalogWithoutGatewayHeader` (`@ParameterizedTest`), `adminCanReadStatistics`, `userCannotBypassGatewayForHotelRead`, `serviceTokenWithWrongSubjectIsRejected`, `serviceTokenWithWrongAudienceIsRejected`, `administratorCannotUseInternalRoomReservationEndpoint`, `userCannotReleaseRoomEvenWhenGatewayAuthenticated`, `invalidBearerTokenReturns401`, `invalidHotelIsRejectedByMvcValidation`, `roomListRequiresHotelId`, `invalidRoomCategoryIsRejectedBeforeServiceInvocation`, `releaseRequiresReservationId`, `missingHotelReturnsDomain404`, `conflictingHotelCreationReturns409`, `everyControllerHandlerHasAnHttpContractCase`.

**`RoomReservationConflictHttpTest` — 1**  
`hotel-service/src/test/java/com/hotelbooking/hotel/controller/RoomReservationConflictHttpTest.java`

**Назначение:** Проверяет HTTP 409 и стабильный код ошибки при повторном использовании идентификатора резервирования.

`duplicateReservationIdReturnsHttp409AndStableCode`.

**`HotelAmenitiesBatchJpaTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelAmenitiesBatchJpaTest.java`

**Назначение:** Проверяет пакетную загрузку удобств при постраничном чтении отелей и номеров без лишних запросов.

`pagedHotelsLoadAmenitiesWithoutExtraQueries`, `pagedRoomsLoadAmenitiesWithoutExtraQueries`.

**`HotelCaseInsensitiveUniquenessJpaTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelCaseInsensitiveUniquenessJpaTest.java`

**Назначение:** Проверяет уникальность отеля по имени и адресу без учёта регистра при создании и изменении.

`differentCaseCannotCreateDuplicateHotel`, `differentAddressAllowsSameHotelName`, `updateCannotDuplicateHotelWithDifferentCase`.

**`HotelDeletionWithPhotosJpaTest` — 4**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelDeletionWithPhotosJpaTest.java`

**Назначение:** Проверяет удаление отеля и фотографий, сохранение файлов при откате и запрет удаления с номерами.

`commitRemovesHotelPhotoRowsFilesAndDirectory`, `rollbackKeepsHotelPhotoMetadataAndFiles`, `hotelWithRoomsIsNotDeletedAndPhotosStay`, `hotelWithoutPhotosCanBeDeleted`.

**`HotelOptimisticLockJpaTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelOptimisticLockJpaTest.java`

**Назначение:** Проверяет optimistic locking при конкурентном изменении названия, адреса и описания отеля.

`stalePresentationMustNotOverwriteHotelNameAndAddress`, `staleDetailsMustNotOverwritePresentation`.

**`HotelPhotoDecodedImageJpaTest` — 5**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelPhotoDecodedImageJpaTest.java`

**Назначение:** Проверяет фактическое декодирование JPEG, PNG, WebP и отклонение повреждённых изображений.

`validJpegAndPngAreAccepted`, `truncatedPngWithCorrectHeaderIsRejectedWithoutStoredFiles`, `truncatedJpegWithCorrectHeaderIsRejectedWithoutStoredFiles`, `truncatedWebpHeaderIsRejected`, `validWebpIsAccepted`.

**`HotelPhotoReadDeleteJpaTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelPhotoReadDeleteJpaTest.java`

**Назначение:** Проверяет безопасное чтение уже полученной фотографии после удаления и обработку отсутствующего файла.

`obtainedResourceRemainsReadableAfterPhotoDeletion`, `missingPhysicalFileReturnsPhotoNotFound`.

**`HotelPhotoServiceConcurrencyJpaTest` — 4**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelPhotoServiceConcurrencyJpaTest.java`

**Назначение:** Проверяет файловую компенсацию при коммите и откате загрузки/удаления и лимит фотографий при гонках.

`uploadRollbackRemovesFileAndMetadata`, `deleteRollbackPreservesFileAndMetadata`, `deleteCommitRemovesFileAndMetadata`, `concurrentUploadsCannotExceedTenPhotos`.

**`HotelPresentationServiceUnitTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelPresentationServiceUnitTest.java`

**Назначение:** Проверяет обновление описания и представления отеля, пустые изменения и отсутствующие записи.

`noFieldsIsRejectedWithoutDbAccess`, `unknownHotelIsRejected`, `updateDelegatesToEntityAndMapper`.

**`HotelRoomSelectionStrategyTest` — 4**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelRoomSelectionStrategyTest.java`

**Назначение:** Проверяет стратегию по умолчанию, административное изменение и ошибки недопустимых значений.

`defaultsToLeastBooked`, `adminUpdatePersistsOnManagedEntity`, `missingHotelFails`, `nullStrategyRejected`.

**`HotelSearchServiceUnitTest` — 10**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelSearchServiceUnitTest.java`

**Назначение:** Проверяет поиск и фильтры отелей, сортировку, пагинацию и read-only транзакции.

`missingHotelById`, `searchRejectsLargePage`, `searchRejectsNegativeRating`, `searchRejectsRatingAboveFive`, `searchRejectsUnknownSort`, `searchRejectsUnknownDirection`, `searchAcceptsValidFilters`, `getHotelByIdMapsLoadedHotel`, `searchAcceptsTextQueryAndMultipleAmenities`, `searchReadMethodsRetainReadonlyTransaction`.

**`HotelServiceCoverageUnitTest` — 10**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelServiceCoverageUnitTest.java`

**Назначение:** Проверяет создание, изменение и удаление отелей, управление стратегией и защиту зависимых данных.

`missingHotelStrategy`, `missingHotelStrategyUpdate`, `getStrategyFromEntity`, `updateStrategyLocksHotel`, `createDuplicateDoesNotSave`, `createTrimsFields`, `updateMissingHotel`, `deleteMissingHotel`, `deleteHotelContainingRoomsDenied`, `deleteEmptyHotelAlsoDeletesPhotoMetadata`.

**`HotelStatisticsServiceUnitTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelStatisticsServiceUnitTest.java`

**Назначение:** Проверяет агрегацию статистики отеля, нулевые результаты и исключение tombstone из бизнес-показателей.

`missingHotelFailsBeforeAggregation`, `aggregatesAllCountersAndExcludesTombstones`, `nullSumIsZero`.

**`HotelStatisticsSnapshotUnitTest` — 1**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelStatisticsSnapshotUnitTest.java`

**Назначение:** Проверяет согласованность статистики в рамках транзакции с уровнем REPEATABLE_READ.

`statisticsUsesConsistentRepeatableReadSnapshot`.

**`HotelStatisticsTombstoneJpaTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelStatisticsTombstoneJpaTest.java`

**Назначение:** Проверяет отделение технических tombstone от фактических отмен и бронирований в статистике.

`releaseBeforeReserveCreatesTombstoneWithoutCancellation`, `completedCancellationCountsAsReleasedNotAsTombstone`, `businessCountersExcludeTechnicalTombstonesInMixedScenario`.

**`RoomCreationLockUnitTest` — 4**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomCreationLockUnitTest.java`

**Назначение:** Проверяет блокировку отеля при создании номера, конфликты и удаление номера с фотографиями.

`creationLocksHotelRowBeforeSavingRoom`, `missingHotelDoesNotWriteRoom`, `duplicateRoomStillUsesExistingConflictHandling`, `deletingRoomWithPhotosRemovesMetadataBeforeRoomDeletion`.

**`RoomDetailsServiceUnitTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomDetailsServiceUnitTest.java`

**Назначение:** Проверяет изменение сведений о номере, блокируемое чтение и обработку пустых изменений.

`emptyPatchRejectedBeforeDatabaseAccess`, `unknownRoomRejected`, `updateUsesLockedReadAndMapsEntity`.

**`RoomPhotoServiceJpaTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomPhotoServiceJpaTest.java`

**Назначение:** Проверяет полный жизненный цикл фотографий номера, основное фото, валидацию и удаление файлов.

`uploadListReadSelectPrimaryAndDelete`, `invalidImageRejected`, `deletingRoomWithPhotosRemovesDatabaseRowsAndDiskFiles`.

**`RoomReservationCompensationTest` — 1**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationCompensationTest.java`

**Назначение:** Проверяет восстановление счётчика бронирований при компенсации неудачного резервирования.

`failedBookingCompensationRestoresCounter`.

**`RoomReservationConcurrencyJpaTest` — 3**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationConcurrencyJpaTest.java`

**Назначение:** Проверяет конкурентные резервирования и освобождения, уникальность reservationId и один tombstone.

`twoSimultaneousDifferentReservationIdsCannotBookOneRoom`, `sameReservationIdForDifferentRoomsReturnsConflict`, `concurrentReleasesForDifferentRoomsUseOneTombstone`.

**`RoomReservationDuplicateKeyTest` — 2**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationDuplicateKeyTest.java`

**Назначение:** Проверяет различение конфликта уникального ключа и нарушения внешнего ключа в JPA.

`uniqueKeyViolationBecomesReservationIdConflict`, `foreignKeyViolationIsNotDisguisedAsReservationConflict`.

**`RoomReservationReplayJpaTest` — 7**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationReplayJpaTest.java`

**Назначение:** Проверяет идемпотентный replay резервирования, изменения параметров, исторические даты и освобождённый tombstone.

`guestCountIsPersistedAndIdenticalReplayDoesNotBookAgain`, `sameIdAndDatesButDifferentGuestCountReturns409Conflict`, `newReservationWithPastCheckInIsRejected`, `alreadyExistingHistoricalReservationCanBeReplayed`, `releasedTombstoneKeepsNullGuestCountAndBlocksReserve`, `internalHttpEndpointReturns200ForReplayAnd409ForChangedGuests`, `internalHttpEndpointRejectsPastCheckInWith400`.

**`RoomReservationRulesJpaTest` — 15**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationRulesJpaTest.java`

**Назначение:** Проверяет занятость по ночам, пересечения дат, граничные интервалы, освобождение и идемпотентность.

`successfulReservationCreatesTwoNightsAndCountsOnce`, `repeatedReserveSameIdIsIdempotent`, `sameIdCannotBeReusedWithDifferentDatesOrRoom`, `overlappingDatesInSameRoomAreRejected`, `checkoutExclusiveSoAdjacentBookingsAreAllowed`, `differentRoomsCanBeBookedOverIdenticalDates`, `confirmedCancellationFreesNightsAndDecrementsCount`, `compensationRestoresCountAndIsIdempotent`, `lateCompensationAfterNormalReleaseDoesNotDoubleDecrementCount`, `releaseBeforeReserveCreatesTombstone`, `releasingOtherRoomReservationIsRejected`, `unavailableRoomCannotBeReserved`, `capacityValidation`, `invalidDateRangeOrMoreThan365Nights`, `roomIsAvailableForPeriodAgainAfterRelease`.

**`RoomSearchServiceCoverageUnitTest` — 11**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomSearchServiceCoverageUnitTest.java`

**Назначение:** Проверяет поиск номеров, фильтры по цене и вместимости, сортировку и ограничения пагинации.

`missingRoomGet`, `roomSearchRejectsHugePage`, `roomSearchRequiresHotelId`, `roomSearchRequiresExistingHotel`, `roomSearchRejectsInvalidCapacity`, `roomSearchRejectsNegativeMinPrice`, `roomSearchRejectsNegativeMaxPrice`, `roomSearchRejectsInvertedPriceRange`, `roomSearchRejectsUnknownSort`, `roomSearchRejectsBadDirection`, `roomSearchUsesPaginationAndSort`.

**`RoomSelectionPerHotelTest` — 1**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomSelectionPerHotelTest.java`

**Назначение:** Проверяет применение стратегии, установленной для отеля, при отсутствии переопределения.

`missingOverrideUsesHotelStrategy`.

**`RoomServiceCoverageUnitTest` — 10**  
`hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomServiceCoverageUnitTest.java`

**Назначение:** Проверяет создание, удаление и подбор номеров, зависимые фотографии и валидацию запроса.

`createRoomRequiresExistingHotel`, `createRoomConvertsUniqueConstraint`, `missingRoomDelete`, `roomWithReservationCannotBeDeleted`, `deleteRoomRemovesPhotosThenRow`, `deleteDatabaseConflictIsResourceInUse`, `recommendationRejectsInvalidHotel`, `recommendationRejectsPastDate`, `recommendationRejectsLongStay`, `recommendationRejectsZeroGuests`.


### Booking Service — E2E

**`AdminPhotosStrategyExtendedE2ETest` — 8**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/AdminPhotosStrategyExtendedE2ETest.java`

**Назначение:** Проверяет административное управление отелями, номерами, стратегиями и фотографиями через полный HTTP-маршрут. Контролирует права доступа и очистку метаданных.

`nonAdminCannotCreateHotelsOrRooms`, `anonymousCannotMutateHotel`, `administratorCreatesHotelAndRoomsAndCanReadThem`, `strategyIsPerHotelAndAdminOnly`, `hotelPhotoLifecycleAndAuthorization`, `roomPhotoLifecycleAndAuthorization`, `unsupportedImageMustBeRejected`, `deletingRoomRemovesAssociatedPhotoMetadata`.

**`AuthenticationSecurityExtendedE2ETest` — 5**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/AuthenticationSecurityExtendedE2ETest.java`

**Назначение:** Проверяет регистрацию/вход, неверные пароли, JWT, роли и конкурентные проверки токенов через запущенные сервисы.

`wrongPasswordRejectedAndValidPasswordWorks`, `distributedAttemptsAgainstOneAccountAreBounded`, `garbageJwtAndMissingJwtCannotReadProtectedApi`, `ordinaryUserCannotReadAdminUsers`, `sameJwtConcurrentChecksDoNotReturn5xx`.

**`ExtendedE2ESupportFixtureTest` — 3**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ExtendedE2ESupportFixtureTest.java`

**Назначение:** Проверяет поиск ручного файла HTTP-сценариев и извлечение из него действующих тестовых данных для входа ADMIN.

`findsManualHttpFileFromProjectAndModuleDirectories`, `extractsAdminLoginInsteadOfUserLogin`, `currentManualHttpFixtureHasReadableAdminLogin`.

**`HotelBookingLoadE2ETest` — 11**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/HotelBookingLoadE2ETest.java`

**Назначение:** Проверяет параллельные запросы, каталоги, конкуренцию за номер, идемпотентность, отмены и согласование счётчиков. Включает длительные нагрузочные и фото-сценарии.

`parallelAuthenticatedRequests100`, `catalogRead5000`, `competingForOneRoom200`, `sameIdempotencyKey200`, `parallelAutoSelect200`, `parallelCancellation100`, `overlappingAndAdjacentDates200`, `mixedTraffic2000`, `parallelSecurity500`, `eventualSagaAndCountersConverge`, `parallelPhotoReads1000VerifySha256`.

**`HotelBookingSagaFaultE2ETest` — 4**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/HotelBookingSagaFaultE2ETest.java`

**Назначение:** Проверяет Saga при недоступности Hotel Service: ошибочные ответы, отмену, сохранение резервирования и ручной Recovery. Некоторые сценарии требуют отдельной настройки ADMIN.

`hotelOutOfService200BookingsFailWithoutFalseConfirmation`, `cancellation100DuringOutageEventuallyCompletes`, `bookedRoomIsNotLostDuringReadAndCreationOutage`, `exhaustedRecoveryAndAdminManualRequeue`.

**`HotelBookingSagaLoadE2ETest` — 6**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/HotelBookingSagaLoadE2ETest.java`

**Назначение:** Проверяет Saga под нагрузкой: replay, многократные циклы reserve/cancel, гонки отмены, автоподбор и точность timesBooked.

`changedPayloadWithSameIdempotencyKey100`, `reserveCancelRebook300Cycles`, `cancelVsNewBookingRace200Requests`, `fullyBookedHotelAutoSelect500`, `exactTimesBookedAfter250ReservationsAndCancellation`, `mixedFiveMinuteSagaSoak`.

**`HotelBookingSmokeE2ETest` — 1**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/HotelBookingSmokeE2ETest.java`

**Назначение:** Проверяет базовый пользовательский путь: регистрация, вход и получение каталога отелей.

`userCanRegisterLoginAndOpenCatalog`.

**`ResilienceBookingE2ETest` — 9**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceBookingE2ETest.java`

**Назначение:** Проверяет создание/отмену, конфликты при параллельных запросах, идемпотентность и неизменность счётчиков при ошибках.

`bookingAndCancelRestoreCounter`, `simultaneous200AttemptsSameRoomCannotDoubleBook`, `hundredReplaysSameKeyProduceSingleReservation`, `differentPayloadSameKeyCannotCreateSecondBooking`, `invalidDateRangeRejectedWithoutIncrementingCounter`, `zeroGuestCountRejected`, `excessiveGuestCountRejected`, `noKeyBookingMustBeRejected`, `hundredParallelCounterReadsAgree`.

**`ResilienceBoundaryE2ETest` — 15**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceBoundaryE2ETest.java`

**Назначение:** Проверяет ошибки 404/4xx, валидацию параметров, ограничения ролей и пагинацию в API отелей и номеров.

`missingHotelReturns404`, `missingRoomReturns404`, `missingBookingReturns404`, `invalidRoomCategoryReturnsClientError`, `invalidRoomSortReturnsClientError`, `invalidRecommendationDateReturnsClientError`, `userCannotDeleteRoom`, `anonymousCannotDeleteRoom`, `anonymousCannotResetStrategy`, `userCannotDeleteHotel`, `invalidStrategyRejected`, `hotelsAndRoomsHaveValidPagination`, `photosForMissingHotelReturn404`, `photosForMissingRoomReturn404`, `unknownHotelStatisticsReturn404`.

**`ResilienceMediaE2ETest` — 15**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceMediaE2ETest.java`

**Назначение:** Проверяет загрузку, чтение и удаление фотографий, доступ по ролям, отклонение некорректных файлов и сохранность байтов при конкуренции.

`hotelPhotosStartEmpty`, `roomPhotosStartEmpty`, `hotelPhotoUploadAndExactDownload`, `roomPhotoUploadAndExactDownload`, `userCannotUploadHotelPhoto`, `userCannotUploadRoomPhoto`, `anonymousCannotUploadPhoto`, `invalidHotelImageIsRejected`, `invalidRoomImageIsRejected`, `deletedHotelPhotoNotDownloadable`, `deletedRoomPhotoNotDownloadable`, `userCannotDeleteHotelPhoto`, `userCannotDeleteRoomPhoto`, `concurrentDownloadsKeepBytesIntact`, `roomDeleteWithPhotoHasNoDanglingRecord`.

**`ResilienceReadLoadE2ETest` — 10**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceReadLoadE2ETest.java`

**Назначение:** Проверяет чтение каталога и бронирований под нагрузкой, изоляцию данных пользователей и корректные ошибки при неверных запросах.

`twoHundredRoomListReadsRespond200`, `twoHundredMyBookingsReadsRespond200`, `alternatingUserTokensKeepDataPrivate`, `repeatedAdminListIsAccessible`, `malformedJsonHotelCreationFailsWith400`, `blankHotelNameIsRejected`, `incompleteLoginDoesNotReturnToken`, `unsupportedContentTypeDoesNotReturn500`, `userCannotCreateRoomEvenWithValidHotel`, `strategyReadRemainsAvailableAfterRepeatedCatalogReads`.

**`ResilienceSecurityE2ETest` — 19**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceSecurityE2ETest.java`

**Назначение:** Проверяет защищённые маршруты, поддельные и отсутствующие JWT, права ролей, неудачный вход и параллельный доступ.

`anonymousProfileRejected`, `anonymousBookingsRejected`, `anonymousHotelMutationRejected`, `malformedJwtCannotOpenProfile`, `malformedJwtCannotListBookings`, `malformedJwtCannotReadHotelCatalog`, `userCannotListAllUsers`, `userCannotListAllBookings`, `userCannotCreateHotel`, `userCannotModifyStrategy`, `authenticatedCatalogRespondsNormally`, `authenticatedOwnBookingsRespondNormally`, `twoHundredConcurrentProfileReadsHaveNoErrors`, `twoHundredConcurrentCatalogReadsHaveNoErrors`, `twoHundredInvalidTokensNeverSucceed`, `wrongPasswordNeverIssuesToken`, `nonexistentAccountNeverIssuesToken`, `invalidJsonCannotAuthenticate`, `unknownRouteMustNotLeakServerFailure`.

**`ResilienceStrategyE2ETest` — 10**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceStrategyE2ETest.java`

**Назначение:** Проверяет стратегии подбора по умолчанию и по отелям, права администратора и сохранение настроек при повторных вызовах.

`newHotelDefaultsToLeastBooked`, `adminCanSetMostExpensive`, `adminCanRestoreLeastBooked`, `differentHotelsKeepIndependentStrategies`, `strategyStatePersistsAcrossSeparateHttpRequests`, `userCannotSetMostExpensive`, `anonymousCannotSetStrategy`, `invalidStrategyDoesNotOverwriteCurrent`, `concurrentReadAfterUpdateIsConsistent`, `switchBetweenModesLeavesNoUnexpectedValue`.

**`TwoHundredUserAuthenticationE2ETest` — 2**  
`booking-service/src/test/java/com/hotelbooking/booking/e2e/TwoHundredUserAuthenticationE2ETest.java`

**Назначение:** Проверяет одновременный вход двухсот разных пользователей и устойчивость к двумстам неверным паролям.

`twoHundredDistinctUsersLoginAtOnce`, `twoHundredSimultaneousWrongPasswordsCannotCauseServerFailure`.
