# Тестирование: запуск, проверяемые сценарии и полный каталог

> Актуальность каталога: исходный архив `hotel-booking-system(20261031-191715).zip` от 31.10.2026. Перечень сформирован по фактическим Java-файлам в `src/test/java` и объявлениям JUnit `@Test`/`@ParameterizedTest`/`@RepeatedTest`/`@TestFactory`/`@TestTemplate`. **Это каталог тестов из исходников, не отчёт о выполнении.**

## 1. Сводка

| Модуль | Тестовых классов | Объявлений тестовых методов |
|---|---:|---:|
| API Gateway | 2 | 14 |
| Booking Service (включая E2E) | 48 | 287 |
| Hotel Service | 27 | 124 |
| **Итого** | **77** | **425** |

Из них E2E: **13 классов, 115 методов** в `booking-service/src/test/java/com/hotelbooking/booking/e2e/`. Остальные **64 классов, 310 методов** — unit, JPA, MVC, security и другие локальные/интеграционные проверки. Классификация точнее определяется аннотациями каждого класса; название `UnitTest` / `JpaTest` помогает ориентироваться, но не заменяет анализ контекста Spring.

**Граница подсчёта:** каждый метод считается один раз. Параметризованные тесты и `@RepeatedTest` могут выполняться несколько раз; `@Nested` и условия запуска также влияют на фактический отчёт JUnit. Вспомогательные `ExtendedE2ESupport.java`, `SagaE2ESupport.java` не считаются тестовыми классами, поскольку не содержат тестовых методов. Сводка не является показателем % покрытия кода или количеством успешно выполненных проверок.

## 2. Автоматический запуск локальных тестов

**Windows PowerShell**, в корне проекта:

```powershell
.\gradlew.bat clean test
```

**macOS/Linux**:

```bash
chmod +x ./gradlew
./gradlew clean test
```

В модулях API Gateway, Booking Service и Hotel Service используется JUnit Platform, а задача Gradle `test` связана с генерацией отчёта JaCoCo `jacocoTestReport`. Отчёты находятся в `<module>/build/reports/tests/test/index.html`, покрытие — `<module>/build/reports/jacoco/test/html/index.html` после выполнения соответствующих задач.

E2E-классы помечены `@EnabledIfSystemProperty(named="hotel.e2e",matches="true")`: **обычный `clean test` не равен полному E2E-прогону**. Для E2E нужны запущенные сетевые сервисы и отдельная конфигурация.

## 3. Сквозные проверки (E2E)

E2E обращаются к реальному REST API; предварительно должны работать **Eureka → API Gateway → Hotel Service → Booking Service**, сервисы должны быть зарегистрированы. Чувствительные параметры (JWT keystore, `GATEWAY_INTERNAL_SECRET`, демо-учётные данные) остаются локальными и не коммитятся в Git.

### Конфигурация `E2E All` в IntelliJ IDEA

В **Run → Edit Configurations → + → JUnit**:

| Поле | Значение |
|---|---|
| Name | `E2E All` |
| JRE | Java 17 |
| Use classpath of module | `hotel-booking-system.booking-service.test` |
| Test kind | `All in package` |
| Package | `com.hotelbooking.booking.e2e` |
| VM options | `-ea -Dhotel.e2e=true` |
| Working directory | `$MODULE_WORKING_DIR$` |

При необходимости включить VM options через **Modify options**. Конфигурация использует сетевые вызовы и не должна запускаться без локальных сервисов. Некоторые сценарии создают нагрузку (до сотен конкурентных запросов) или переводят Hotel Service в `OUT_OF_SERVICE` на время проверки; запускать их на отдельной тестовой среде. Перед повторным полным прогоном рекомендуется чистый перезапуск четырёх сервисов, чтобы устранить состояние, оставшееся от предыдущих прогонов.

Для fault-сценария `HotelBookingSagaFaultE2ETest.exhaustedRecoveryAndAdminManualRequeue` нужен локальный `booking-service/secrets/saga-e2e-admin.properties` со значениями `email` и `password` администратора. Без него сценарий может быть **пропущен**, а не считаться успешным. Нужен один экземпляр Hotel Service для сценариев изменения статуса в Eureka. Проверять в отчёте JUnit не только FAILED, но и ABORTED/SKIPPED.

```properties
email=<адрес администратора>
password=<пароль администратора>
```

## 4. Ручная проверка REST API и Swagger

Swagger UI доступен у работающих Booking Service `http://localhost:8081/swagger-ui.html` и Hotel Service `http://localhost:8082/swagger-ui.html`. Через **Servers** выбирайте `http://localhost:8080` (API Gateway); защищённые операции требуют JWT через **Authorize**. Описания HTTP-кодов находятся в разделе **Responses**, а фактический ответ запуска — в **Server response**.

| Проверка | Ожидаемый результат |
|---|---|
| Регистрация и вход | Пользователь создан, выдан JWT |
| Отсутствующий/недействительный JWT | `401` |
| USER вызывает административный метод | `403` |
| ADMIN создаёт отель и номер | Успешное создание и чтение |
| Подбор `LEAST_BOOKED` | Из доступных номеров первыми выдаются менее забронированные |
| Ручное бронирование свободного номера | `201`, статус `CONFIRMED` |
| Повторное бронирование той же ночи/номера | `409 ROOM_ALREADY_BOOKED` |
| Повтор с тем же `Idempotency-Key` | Не создаёт новую бронь |
| Отмена подтверждённой брони | Освобождает ночи, корректирует `timesBooked` однократно |
| Пагинация и фильтры | Предсказуемые страницы, согласованная сортировка |
| Фото | Только допустимый формат/размер, права на запись и доступ по API |
| Сбой Hotel Service | Ошибка/повтор/recovery согласно сценарию, без ложного `CONFIRMED` |

Примеры ручных HTTP-запросов содержатся в `requests.http`, `idempotency-tests.http` и других `.http` файлах проекта. Для бронирования задавайте актуальные будущие даты.

## 5. Полный каталог автоматических тестов

Показаны **все 425 метода**. Для каждого класса приведены назначение, точное имя метода из исходника и вид JUnit-аннотации. Имя метода является точным идентификатором сценария; ожидаемый результат задаётся соответствующей проверкой (`assert*`, `verify`, HTTP-кодами и т.д.) в реализации теста. Каталог намеренно не утверждает, что каждый тест был выполнен именно при построении документации.

### 5.1. API Gateway — фильтры и безопасность (2 классов, 14 методов)

#### `GatewaySecretFilterTest` — 3

Доверенные заголовки Gateway: секрет межсервисного доступа и фактический IP клиента.  
Исходник: `api-gateway/src/test/java/com/hotelbooking/gateway/config/GatewaySecretFilterTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `forgedClientSecretIsOverwrittenBeforeForwarding()` | `@Test` |
| 2 | `gatewaySuppliesSecretWhenClientSentNone()` | `@Test` |
| 3 | `forgedVerifiedIpIsRemovedAndActualPeerIsUsed()` | `@Test` |

#### `JwtRevocationFilterTest` — 11

Проверка JWT на Gateway: отзыв токена, версия, смена пароля, сбои Booking Service и авторизация.  
Исходник: `api-gateway/src/test/java/com/hotelbooking/gateway/config/JwtRevocationFilterTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `approvedJwtStatusForwardsHotelRequest()` | `@Test` |
| 2 | `revokedJwtIsRejectedAsUnauthorized()` | `@Test` |
| 3 | `forcedPasswordChangeBlocksOrdinaryRequests()` | `@Test` |
| 4 | `forcedPasswordChangeAllowsPasswordChangeEndpoint()` | `@Test` |
| 5 | `bookingServiceOutageFailsClosed()` | `@Test` |
| 6 | `noAuthenticationReturnsUnauthorizedWithoutBackendCheck()` | `@Test` |
| 7 | `unrecognizedRoleCannotReachHotelOrTokenStatus()` | `@Test` |
| 8 | `loginIsNotBlockedByMissingJwtOrBookingStateCheck()` | `@Test` |
| 9 | `registrationIsNotBlockedByMissingJwtOrBookingStateCheck()` | `@Test` |
| 10 | `adminRoleIsAccepted()` | `@Test` |
| 11 | `simultaneousRequestsWithSameTokenShareStatusCall()` | `@Test` |

### 5.2. Booking Service — unit, интеграционные, MVC и JPA (35 классов, 172 методов)

#### `AdminConcurrencyJpaTest` — 1

Конкурирующие изменения ролей администратора и защита последнего ADMIN.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/AdminConcurrencyJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `competingDemotionsPreserveAtLeastOneAdministrator()` | `@Test` |

#### `AdminProtectionTest` — 7

Запрет самоудаления/самопонижения и удаление последнего ADMIN.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/AdminProtectionTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `adminCannotDemoteSelfEvenWithMultipleAdmins()` | `@Test` |
| 2 | `lastAdminCannotBeDemotedByAnotherAdmin()` | `@Test` |
| 3 | `demotionOfOtherAdminWorksWhenAnotherAdminRemains()` | `@Test` |
| 4 | `adminCannotDeleteSelf()` | `@Test` |
| 5 | `lastAdminCannotBeDeletedByAnotherAdmin()` | `@Test` |
| 6 | `otherAdminCanBeDeletedIfAtLeastOneAdminRemains()` | `@Test` |
| 7 | `bookingHistoryStillPreventsUserDeletion()` | `@Test` |

#### `AdminReadCoverageUnitTest` — 4

Пагинация и получение пользователей администратором.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/AdminReadCoverageUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `pageTooLargeRejected()` | `@Test` |
| 2 | `missingUserRejected()` | `@Test` |
| 3 | `pageUsesDeterministicIdSort()` | `@Test` |
| 4 | `existingUserMapped()` | `@Test` |

#### `AdminWriteCoverageUnitTest` — 9

Административное создание, обновление и удаление пользователей.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/AdminWriteCoverageUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `duplicateUsernameRejected()` | `@Test` |
| 2 | `duplicateEmailRejected()` | `@Test` |
| 3 | `passwordTooLongRejected()` | `@Test` |
| 4 | `concurrentUniqueViolationIsConflict()` | `@Test` |
| 5 | `cannotDeleteSelf()` | `@Test` |
| 6 | `missingTargetUserRejected()` | `@Test` |
| 7 | `lastAdminCannotBeDeleted()` | `@Test` |
| 8 | `bookingHistoryPreventsDeletion()` | `@Test` |
| 9 | `deletionFlushesChanges()` | `@Test` |

#### `AppUserOptimisticLockJpaTest` — 2

Optimistic locking пользовательских записей при смене роли и пароля.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/AppUserOptimisticLockJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `staleProfileUpdateMustNotRestoreDemotedAdminOrOldTokenVersion()` | `@Test` |
| 2 | `stalePasswordUpdateMustNotClearAdminPasswordResetFlag()` | `@Test` |

#### `BookingAllAdminSecurityTest` — 2

Доступ ADMIN к общему списку бронирований.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/controller/BookingAllAdminSecurityTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `allBookingsEndpointRequiresAdminRole()` | `@Test` |
| 2 | `allBookingsServiceAlsoRequiresAdminRole()` | `@Test` |

#### `BookingErrorHandlingTest` — 9

Единый формат HTTP-ошибок, traceId и коды.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/exception/BookingErrorHandlingTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `businessErrorHasPreservedStatusCodeAndTraceId()` | `@Test` |
| 2 | `existingConflictCodeIsPreserved()` | `@Test` |
| 3 | `validationErrorsKeepFieldErrors()` | `@Test` |
| 4 | `malformedJsonHasOurStandardErrorFormat()` | `@Test` |
| 5 | `missingHeaderAndUnsupportedMethodAreMapped()` | `@Test` |
| 6 | `responseStatusExceptionKeepsItsHttpStatus()` | `@Test` |
| 7 | `accessDeniedIsStill403()` | `@Test` |
| 8 | `unexpectedErrorIs500ButItsDetailsAreNotLeaked()` | `@Test` |
| 9 | `gatewayInterceptorRejectionAlsoCarriesTraceId()` | `@Test` |

#### `BookingIdempotencyConcurrencyJpaTest` — 1

Уникальность idempotency key при конкурентном создании бронирований.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/BookingIdempotencyConcurrencyJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `sameUserAndKeyCannotCreateTwoPendingBookings()` | `@Test` |

#### `BookingRecoveryAdminSecurityTest` — 1

Ограничения административных операций восстановления Saga.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/controller/BookingRecoveryAdminSecurityTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `adminOnlyGuardIsDeclaredForBothEndpoints()` | `@Test` |

#### `BookingRecoveryPaginationJpaTest` — 2

Постраничная выборка незавершённых Saga для восстановления.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/BookingRecoveryPaginationJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `pendingQueryReturnsNextPageWithoutSkippingRecords()` | `@Test` |
| 2 | `cancellationQueryReturnsNextPageWithoutSkippingRecords()` | `@Test` |

#### `BookingRecoveryRaceTest` — 3

Гонки между подтверждением, компенсированием и recovery.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/BookingRecoveryRaceTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `recoveryWins_confirmationMustFail()` | `@Test` |
| 2 | `confirmationWins_recoveryMustNotCancel()` | `@Test` |
| 3 | `activeSagaMustNotBeRecoveredWhenCreatedAtIsOld()` | `@Test` |

#### `BookingRecoveryServiceTest` — 8

Фоновое восстановление создания и отмены бронирования.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/BookingRecoveryServiceTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `pendingRecoveryCompensatesBeforeMarkingFailed()` | `@Test` |
| 2 | `failedCompensationLeavesPendingForNextRecovery()` | `@Test` |
| 3 | `confirmedOrFreshBookingIsSkippedByTransactionalRecheck()` | `@Test` |
| 4 | `cancellationRecoveryUsesReleaseNotCompensation()` | `@Test` |
| 5 | `failedCancellationReleaseLeavesCancellingForNextRecovery()` | `@Test` |
| 6 | `cancellationRecoverySkipsBookingNoLongerCancelling()` | `@Test` |
| 7 | `pendingRecoveryProcessesMoreThanTenCandidates()` | `@Test` |
| 8 | `cancellationRecoveryProcessesMoreThanTenCandidates()` | `@Test` |

#### `BookingRequestValidationMvcTest` — 3

Валидация HTTP-запроса бронирования через MVC.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/dto/request/BookingRequestValidationMvcTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `tooManyGuestsReturnValidationErrorWithTraceId()` | `@Test` |
| 2 | `checkoutTodayReturnsValidationError()` | `@Test` |
| 3 | `validBoundaryRequestPassesMvcValidation()` | `@Test` |

#### `BookingRequestValidationTest` — 8

Проверка допустимых дат, количества гостей и обязательных полей.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/dto/request/BookingRequestValidationTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `zeroGuestsAreRejected()` | `@Test` |
| 2 | `guestCountAboveTwentyIsRejected()` | `@Test` |
| 3 | `oneAndTwentyGuestsAreAccepted()` | `@Test` |
| 4 | `omittedGuestCountDefaultsToOne()` | `@Test` |
| 5 | `checkoutTodayOrYesterdayIsRejected()` | `@Test` |
| 6 | `omittedCheckoutIsRejected()` | `@Test` |
| 7 | `pastCheckinIsStillRejected()` | `@Test` |
| 8 | `todayCheckinTomorrowCheckoutAreAccepted()` | `@Test` |

#### `BookingSagaStateJpaTest` — 13

Переходы состояния бронирования/Saga, версия и корреляция.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/BookingSagaStateJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `createPendingStoresReservationIdFingerprintAndGuestCount()` | `@Test` |
| 2 | `pendingCanBeConfirmedOnceOnly()` | `@Test` |
| 3 | `recoveryMarksPendingRecoveringAndDisablesLateConfirmation()` | `@Test` |
| 4 | `recoverySkipsYoungPendingAndConfirmed()` | `@Test` |
| 5 | `compensationRecordsFailedAndProhibitsConfirmation()` | `@Test` |
| 6 | `confirmedBookingCannotBeCompensatedAsFailedCreation()` | `@Test` |
| 7 | `cancelledCreationCannotBeCancelledAgainWithNewOutcome()` | `@Test` |
| 8 | `onlyOwnerOrAdminCanStartCancellation()` | `@Test` |
| 9 | `cancellationIsRecoverableAndEventuallyIdempotent()` | `@Test` |
| 10 | `cannotFinishCancellationBeforeItStarted()` | `@Test` |
| 11 | `cannotSwitchRoomAfterConfirmationOrWhileRecovering()` | `@Test` |
| 12 | `sameKeyDifferentRequestIsRejected()` | `@Test` |
| 13 | `duplicateUserAndIdempotencyKeyFailsAtDatabaseConstraint()` | `@Test` |

#### `BookingServiceSagaTest` — 18

Оркестрация создания, отмены, replay, компенсации и ошибок Hotel Service.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/BookingServiceSagaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `successfulCreationConfirmsExactlyOnceAndNeverCompensates()` | `@Test` |
| 2 | `replayOfConfirmedKeyReturnsOriginalBookingWithoutAnotherReservation()` | `@Test` |
| 3 | `pendingAndRecoveringKeysCannotStartAnotherSaga()` | `@Test` |
| 4 | `previouslyFailedKeyCannotStartAnotherSaga()` | `@Test` |
| 5 | `repeatedRoomConflictProducesSameDomainError()` | `@Test` |
| 6 | `hotelConflictCompensatesAndRecordsRoomConflict()` | `@Test` |
| 7 | `unavailableHotelCompensatesAndMarksFailed()` | `@Test` |
| 8 | `confirmationFailureAfterHotelReservationStillCompensates()` | `@Test` |
| 9 | `failedCompensationDoesNotMarkSagaCancelledSoRecoveryCanRetry()` | `@Test` |
| 10 | `invalidReservationResponseIsCompensated()` | `@Test` |
| 11 | `autoSelectionFallsBackAfterConflictWithoutCompensatingFirstCandidate()` | `@Test` |
| 12 | `allAutoCandidatesOccupiedCompensatesLatestAttempt()` | `@Test` |
| 13 | `cancellationUsesOrdinaryReleaseAndThenFinishes()` | `@Test` |
| 14 | `failedReleaseLeavesCancellationInProgressForRecovery()` | `@Test` |
| 15 | `invalidDatesAndTooLongStayAreRejectedBeforeHotelCall()` | `@Test` |
| 16 | `disabledRoomIsRejectedBeforePendingState()` | `@Test` |
| 17 | `notEnoughCapacityIsRejectedBeforePendingState()` | `@Test` |
| 18 | `replayOfCancelledBookingReturnsActualStatus()` | `@Test` |

#### `BookingValidationCoverageUnitTest` — 10

Бизнес-валидация бронирований и выбора номера.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/BookingValidationCoverageUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `checkInIsRequired()` | `@Test` |
| 2 | `checkoutIsRequired()` | `@Test` |
| 3 | `checkoutMustBeAfterCheckin()` | `@Test` |
| 4 | `maximumStayIs365Nights()` | `@Test` |
| 5 | `roomIdMustBePositive()` | `@Test` |
| 6 | `manualBookingRejectsHotelId()` | `@Test` |
| 7 | `autoBookingRequiresPositiveHotelId()` | `@Test` |
| 8 | `autoBookingRejectsNoRecommendations()` | `@Test` |
| 9 | `manualBookingRejectsUnavailableRoom()` | `@Test` |
| 10 | `manualBookingRejectsInsufficientCapacity()` | `@Test` |

#### `ExhaustedSagaMonitorTest` — 1

Поиск исчерпавших попытки Saga без модификации состояния.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/ExhaustedSagaMonitorTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `checksExhaustedWithoutMutatingState()` | `@Test` |

#### `FailedAccountLoginLimiterTest` — 2

Блокировка после неудачных логинов.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/security/FailedAccountLoginLimiterTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `fifthFailureBlocksSameAccountButNotOtherAccount()` | `@Test` |
| 2 | `expiredWindowIsUnlocked()` | `@Test` |

#### `HistoricalIdempotencyReplayTest` — 3

Повтор исторического запроса и валидация новых бронирований.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/HistoricalIdempotencyReplayTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `successfulHistoricalReplayReturnsOriginalBookingWithoutHotelCall()` | `@Test` |
| 2 | `newBookingWithPastDatesStillReturnsFieldValidationErrors()` | `@Test` |
| 3 | `guestCountStillValidatedForNewBooking()` | `@Test` |

#### `HotelServiceClientRetryTest` — 2

Повторы вызовов Hotel Service и Circuit Breaker.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/client/HotelServiceClientRetryTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `openCircuitBreakerDoesNotRetry()` | `@Test` |
| 2 | `transientNetworkFailureStillRetriesTwice()` | `@Test` |

#### `JwtServiceTest` — 8

Выдача и проверка пользовательского JWT и криптографическая защита.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/JwtServiceTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `signedTokenHasExpectedSubjectIssuerRoleAndIdentity()` | `@Test` |
| 2 | `adminTokenIncludesAdminRole()` | `@Test` |
| 3 | `lifetimeIsOneHourAndReportedInSeconds()` | `@Test` |
| 4 | `tokenSignedByAnotherKeyIsRejected()` | `@Test` |
| 5 | `modifiedPayloadIsRejectedBySignatureValidation()` | `@Test` |
| 6 | `wrongIssuerIsRejected()` | `@Test` |
| 7 | `expiredTokenIsRejected()` | `@Test` |
| 8 | `roleAndTokenVersionChangeAreEmbeddedInNewToken()` | `@Test` |

#### `LoginRegistrationRateLimiterCapacityTest` — 2

Ограничение памяти и ёмкости rate limiter.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/security/LoginRegistrationRateLimiterCapacityTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `manyUniqueIdentitiesNeverGrowBeyondLimit()` | `@Test` |
| 2 | `differentWindowLengthsStillWork()` | `@Test` |

#### `LoginServiceTest` — 6

Проверка учётных данных и выдача JWT.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/LoginServiceTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `validCredentialsReturnBearerTokenAndLifetime()` | `@Test` |
| 2 | `missingUserRejectsWithoutGeneratingJwt()` | `@Test` |
| 3 | `wrongPasswordRejectsWithoutGeneratingJwt()` | `@Test` |
| 4 | `absentAndWrongCredentialsExposeSameErrorText()` | `@Test` |
| 5 | `repeatedWrongPasswordsBlockSameAccountAcrossCalls()` | `@Test` |
| 6 | `forcedPasswordChangeFlagIsVisibleInLoginResponse()` | `@Test` |

#### `ManualBookingRecoveryJpaTest` — 3

Ручная повторная постановка Saga с сохранением идентичности.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/ManualBookingRecoveryJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `requeuesPendingWithoutLosingReservationIdentity()` | `@Test` |
| 2 | `requeuesCancellingButPreservesStatusAndBookingId()` | `@Test` |
| 3 | `rejectsManualRequeueBeforeLimitOrAfterCompletion()` | `@Test` |

#### `PublicAuthRateLimitTest` — 7

Ограничения частоты регистрации/входа и доверенные IP-заголовки.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/security/PublicAuthRateLimitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `loginThrottlesThirdAttemptWithRetryAfter()` | `@Test` |
| 2 | `registrationHasIndependentHourlyLimit()` | `@Test` |
| 3 | `limitsArePerIp()` | `@Test` |
| 4 | `cannotBypassLimitsUsingSpoofedForwardedHeader()` | `@Test` |
| 5 | `trustedGatewaySeparatesClientAddresses()` | `@Test` |
| 6 | `untrustedClientCannotSpoofVerifiedHeader()` | `@Test` |
| 7 | `otherMethodsAndRoutesRemainUnaffected()` | `@Test` |

#### `R4UserBookingLockUnitTest` — 2

Блокировка пользователя при создании брони и удалении учётной записи.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/R4UserBookingLockUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `pendingCreationRejectsUserDeletedBeforeLock()` | `@Test` |
| 2 | `adminDeletionLocksUserBeforeCheckingBookingHistory()` | `@Test` |

#### `RateLimiterAdditionalUnitTest` — 3

Границы квот и независимость ключей rate limiter.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/security/RateLimiterAdditionalUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `exactLimitAndNextAttemptRejected()` | `@Test` |
| 2 | `independentKeysHaveIndependentWindows()` | `@Test` |
| 3 | `concurrentCallsCannotAllBypassLimit()` | `@Test` |

#### `RegistrationServiceUnitTest` — 5

Регистрация, нормализация данных и защита от дублей.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/RegistrationServiceUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `registersNormalizedUserWithUserRoleAndEncodedPassword()` | `@Test` |
| 2 | `rejectsExistingUsernameBeforeEncoding()` | `@Test` |
| 3 | `rejectsExistingEmailBeforeEncoding()` | `@Test` |
| 4 | `rejectsMoreThan72Utf8Bytes()` | `@Test` |
| 5 | `convertsUniqueConstraintRaceIntoConflict()` | `@Test` |

#### `RestClientConfigTest` — 2

Преобразование транспортных ошибок при REST-вызовах.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/config/RestClientConfigTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `transportIllegalStateBecomesServiceUnavailable()` | `@Test` |
| 2 | `unrelatedTransportArgumentErrorIsNotConverted()` | `@Test` |

#### `SensitiveActionRateLimitInterceptorTest` — 3

Лимиты чувствительных операций (пароль, администрирование).  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/security/SensitiveActionRateLimitInterceptorTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `passwordChangeIsLimitedPerAuthenticatedUser()` | `@Test` |
| 2 | `adminResetAndUpdateUseIndependentLimits()` | `@Test` |
| 3 | `otherMethodsAreNotLimited()` | `@Test` |

#### `ServiceTokenProviderTest` — 3

Подпись, claims и срок действия сервисного JWT.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/security/ServiceTokenProviderTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `serviceTokenUsesCorrectIssuerSubjectAudienceAndRole()` | `@Test` |
| 2 | `serviceTokenExpiresAfterOneMinute()` | `@Test` |
| 3 | `serviceTokenDoesNotHaveUserTokenVersionOrIdentity()` | `@Test` |

#### `UserPasswordServiceUnitTest` — 5

Смена пароля пользователя и валидация.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/UserPasswordServiceUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `nonexistentUserIsUnauthorized()` | `@Test` |
| 2 | `incorrectCurrentPasswordIsRejected()` | `@Test` |
| 3 | `unchangedPasswordIsRejected()` | `@Test` |
| 4 | `excessiveUtf8PasswordIsRejected()` | `@Test` |
| 5 | `validPasswordChangeHashesAndSaves()` | `@Test` |

#### `UserProfileServiceUnitTest` — 6

Чтение и изменение собственного профиля.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/service/UserProfileServiceUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `readsOwnProfile()` | `@Test` |
| 2 | `readMissingUserIsUnauthorized()` | `@Test` |
| 3 | `emptyPatchIsRejected()` | `@Test` |
| 4 | `usernameCollisionIsRejected()` | `@Test` |
| 5 | `normalizationAndPhoneClearingArePersisted()` | `@Test` |
| 6 | `saveConstraintCollisionIsTranslated()` | `@Test` |

#### `UserTokenStateWebConfigTest` — 8

Состояние пользовательского JWT и проверка внутреннего секрета.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/config/UserTokenStateWebConfigTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `missingGatewaySecretIsRejectedBeforeUserLookup()` | `@Test` |
| 2 | `validCurrentJwtPasses()` | `@Test` |
| 3 | `missingUserRevokesJwt()` | `@Test` |
| 4 | `roleOrPasswordChangeInvalidatesOldTokenVersion()` | `@Test` |
| 5 | `changedIdentityInvalidatesToken()` | `@Test` |
| 6 | `forcedPasswordChangeBlocksEverythingExceptChangePassword()` | `@Test` |
| 7 | `serviceTokenBypassesUserDatabaseLookup()` | `@Test` |
| 8 | `nonNumericSubjectIsRejected()` | `@Test` |

### 5.3. Hotel Service — unit, интеграционные, HTTP и JPA (27 классов, 124 методов)

#### `DemoHotelsIdempotencyJpaTest` — 2

Идемпотентная инициализация демоданных и очистка при rollback.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/config/DemoHotelsIdempotencyJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `renamedDemoRoomIsNotRecreated()` | `@Test` |
| 2 | `rollbackRemovesNewlyCopiedDemoFiles()` | `@Test` |

#### `DemoHotelsInitializerMimeTest` — 2

Определение MIME-типа демонстрационных изображений.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/config/DemoHotelsInitializerMimeTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `jpegAndPngUseCorrectTypes()` | `@Test` |
| 2 | `unsupportedExtensionIsRejected()` | `@Test` |

#### `HotelAmenitiesBatchJpaTest` — 2

Проверки пакетной загрузки amenities без N+1.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelAmenitiesBatchJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `pagedHotelsLoadAmenitiesWithoutNPlusOne()` | `@Test` |
| 2 | `pagedRoomsLoadAmenitiesWithoutNPlusOne()` | `@Test` |

#### `HotelCaseInsensitiveUniquenessJpaTest` — 3

Уникальность отеля с учётом регистра.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelCaseInsensitiveUniquenessJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `differentCaseCannotCreateDuplicateHotel()` | `@Test` |
| 2 | `differentAddressAllowsSameHotelName()` | `@Test` |
| 3 | `updateCannotDuplicateHotelWithDifferentCase()` | `@Test` |

#### `HotelDeletionWithPhotosJpaTest` — 4

Согласованное удаление отелей и фотографий при commit/rollback.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelDeletionWithPhotosJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `commitRemovesHotelPhotoRowsFilesAndDirectory()` | `@Test` |
| 2 | `rollbackKeepsHotelPhotoMetadataAndFiles()` | `@Test` |
| 3 | `hotelWithRoomsIsNotDeletedAndPhotosStay()` | `@Test` |
| 4 | `hotelWithoutPhotosCanBeDeleted()` | `@Test` |

#### `HotelOptimisticLockJpaTest` — 2

Optimistic locking изменений отеля.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelOptimisticLockJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `stalePresentationMustNotOverwriteHotelNameAndAddress()` | `@Test` |
| 2 | `staleDetailsMustNotOverwritePresentation()` | `@Test` |

#### `HotelPhotoDecodedImageJpaTest` — 5

Проверка реально декодируемых JPEG/PNG/WebP.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelPhotoDecodedImageJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `validJpegAndPngAreAccepted()` | `@Test` |
| 2 | `truncatedPngWithCorrectHeaderIsRejectedWithoutStoredFiles()` | `@Test` |
| 3 | `truncatedJpegWithCorrectHeaderIsRejectedWithoutStoredFiles()` | `@Test` |
| 4 | `truncatedWebpHeaderIsRejected()` | `@Test` |
| 5 | `validWebpIsAccepted()` | `@Test` |

#### `HotelPhotoReadDeleteJpaTest` — 2

Чтение/удаление фото и физические файлы.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelPhotoReadDeleteJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `obtainedResourceRemainsReadableAfterPhotoDeletion()` | `@Test` |
| 2 | `missingPhysicalFileReturnsPhotoNotFound()` | `@Test` |

#### `HotelPhotoServiceConcurrencyJpaTest` — 4

Параллельная загрузка фото и транзакционность файлов.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelPhotoServiceConcurrencyJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `uploadRollbackRemovesFileAndMetadata()` | `@Test` |
| 2 | `deleteRollbackPreservesFileAndMetadata()` | `@Test` |
| 3 | `deleteCommitRemovesFileAndMetadata()` | `@Test` |
| 4 | `concurrentUploadsCannotExceedTenPhotos()` | `@Test` |

#### `HotelPresentationServiceUnitTest` — 3

Частичное обновление представления отеля.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelPresentationServiceUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `noFieldsIsRejectedWithoutDbAccess()` | `@Test` |
| 2 | `unknownHotelIsRejected()` | `@Test` |
| 3 | `updateDelegatesToEntityAndMapper()` | `@Test` |

#### `HotelRoomSelectionStrategyTest` — 4

Стратегия подбора номера в модели отеля.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelRoomSelectionStrategyTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `defaultsToLeastBooked()` | `@Test` |
| 2 | `adminUpdatePersistsOnManagedEntity()` | `@Test` |
| 3 | `missingHotelFails()` | `@Test` |
| 4 | `nullStrategyRejected()` | `@Test` |

#### `HotelServiceCoverageUnitTest` — 17

Покрытие сервиса отелей: создание, поиск, конфликты, стратегия.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelServiceCoverageUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `missingHotelById()` | `@Test` |
| 2 | `missingHotelStrategy()` | `@Test` |
| 3 | `missingHotelStrategyUpdate()` | `@Test` |
| 4 | `getStrategyFromEntity()` | `@Test` |
| 5 | `updateStrategyLocksHotel()` | `@Test` |
| 6 | `createDuplicateDoesNotSave()` | `@Test` |
| 7 | `createTrimsFields()` | `@Test` |
| 8 | `updateMissingHotel()` | `@Test` |
| 9 | `deleteMissingHotel()` | `@Test` |
| 10 | `deleteHotelContainingRoomsDenied()` | `@Test` |
| 11 | `deleteEmptyHotelAlsoDeletesPhotoMetadata()` | `@Test` |
| 12 | `searchRejectsLargePage()` | `@Test` |
| 13 | `searchRejectsNegativeRating()` | `@Test` |
| 14 | `searchRejectsRatingAboveFive()` | `@Test` |
| 15 | `searchRejectsUnknownSort()` | `@Test` |
| 16 | `searchRejectsUnknownDirection()` | `@Test` |
| 17 | `searchAcceptsValidFilters()` | `@Test` |

#### `HotelStatisticsServiceUnitTest` — 3

Агрегация статистики отеля, исключение tombstone.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelStatisticsServiceUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `missingHotelFailsBeforeAggregation()` | `@Test` |
| 2 | `aggregatesAllCountersAndExcludesTombstones()` | `@Test` |
| 3 | `nullSumIsZero()` | `@Test` |

#### `HotelStatisticsSnapshotUnitTest` — 1

Консистентный снимок статистики.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelStatisticsSnapshotUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `statisticsUsesConsistentRepeatableReadSnapshot()` | `@Test` |

#### `HotelStatisticsTombstoneJpaTest` — 3

Tombstone и корректный учёт резервирований в статистике.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/HotelStatisticsTombstoneJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `releaseBeforeReserveIsTombstoneAndNotARealCancellation()` | `@Test` |
| 2 | `completedCancellationCountsAsReleasedNotAsTombstone()` | `@Test` |
| 3 | `businessCountersExcludeTechnicalTombstonesInMixedScenario()` | `@Test` |

#### `HotelTokenAccessTest` — 6

JWT и доверенный Gateway в Hotel Service.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/config/HotelTokenAccessTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `validServiceTokenIsAcceptedWithoutGatewayHeader()` | `@Test` |
| 2 | `serviceTokenWithWrongAudienceIsDenied()` | `@Test` |
| 3 | `serviceTokenWithWrongSubjectIsDenied()` | `@Test` |
| 4 | `userJwtWithoutTrustedGatewayHeaderIsDenied()` | `@Test` |
| 5 | `userJwtWithCorrectGatewayHeaderIsAccepted()` | `@Test` |
| 6 | `userJwtWithForgedGatewayHeaderIsDenied()` | `@Test` |

#### `RoomCreationLockUnitTest` — 4

Блокировка отеля при создании номера и удаление связанных фото.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomCreationLockUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `creationLocksHotelRowBeforeSavingRoom()` | `@Test` |
| 2 | `missingHotelDoesNotWriteRoom()` | `@Test` |
| 3 | `duplicateRoomStillUsesExistingConflictHandling()` | `@Test` |
| 4 | `deletingRoomWithPhotosRemovesMetadataBeforeRoomDeletion()` | `@Test` |

#### `RoomDetailsServiceUnitTest` — 3

Частичное обновление данных номера.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomDetailsServiceUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `emptyPatchRejectedBeforeDatabaseAccess()` | `@Test` |
| 2 | `unknownRoomRejected()` | `@Test` |
| 3 | `updateUsesLockedReadAndMapsEntity()` | `@Test` |

#### `RoomPhotoServiceJpaTest` — 3

Жизненный цикл фото номера и проверка формата.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomPhotoServiceJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `uploadListReadSelectPrimaryAndDelete()` | `@Test` |
| 2 | `invalidImageRejected()` | `@Test` |
| 3 | `deletingRoomWithPhotosRemovesDatabaseRowsAndDiskFiles()` | `@Test` |

#### `RoomReservationCompensationTest` — 1

Компенсация неуспешного бронирования и счётчик.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationCompensationTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `failedBookingCompensationRestoresCounter()` | `@Test` |

#### `RoomReservationConcurrencyJpaTest` — 3

Гонки резервирования и release.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationConcurrencyJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `twoSimultaneousDifferentReservationIdsCannotBookOneRoom()` | `@Test` |
| 2 | `sameReservationIdForDifferentRoomsReturnsConflict()` | `@Test` |
| 3 | `concurrentReleasesForDifferentRoomsUseOneTombstone()` | `@Test` |

#### `RoomReservationConflictHttpTest` — 1

HTTP-ошибка при дублировании ID резервирования.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/controller/RoomReservationConflictHttpTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `duplicateReservationIdReturnsHttp409AndStableCode()` | `@Test` |

#### `RoomReservationDuplicateKeyTest` — 2

Точность обработки конфликтов уникальности и FK.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationDuplicateKeyTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `uniqueKeyViolationBecomesReservationIdConflict()` | `@Test` |
| 2 | `foreignKeyViolationIsNotDisguisedAsReservationConflict()` | `@Test` |

#### `RoomReservationReplayJpaTest` — 7

Replay резервирования, проверка числа гостей и tombstone.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationReplayJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `guestCountIsPersistedAndIdenticalReplayDoesNotBookAgain()` | `@Test` |
| 2 | `sameIdAndDatesButDifferentGuestCountReturns409Conflict()` | `@Test` |
| 3 | `newReservationWithPastCheckInIsRejected()` | `@Test` |
| 4 | `alreadyExistingHistoricalReservationCanBeReplayed()` | `@Test` |
| 5 | `releasedTombstoneKeepsNullGuestCountAndBlocksReserve()` | `@Test` |
| 6 | `internalHttpEndpointReturns200ForReplayAnd409ForChangedGuests()` | `@Test` |
| 7 | `internalHttpEndpointRejectsPastCheckInWith400()` | `@Test` |

#### `RoomReservationRulesJpaTest` — 15

Резервирование по ночам, даты, идемпотентность и конфликты.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomReservationRulesJpaTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `successfulReservationCreatesTwoNightsAndCountsOnce()` | `@Test` |
| 2 | `repeatedReserveSameIdIsIdempotent()` | `@Test` |
| 3 | `sameIdCannotBeReusedWithDifferentDatesOrRoom()` | `@Test` |
| 4 | `overlappingDatesInSameRoomAreRejected()` | `@Test` |
| 5 | `checkoutExclusiveSoAdjacentBookingsAreAllowed()` | `@Test` |
| 6 | `differentRoomsCanBeBookedOverIdenticalDates()` | `@Test` |
| 7 | `confirmedCancellationFreesNightsAndDecrementsCount()` | `@Test` |
| 8 | `compensationRestoresCountAndIsIdempotent()` | `@Test` |
| 9 | `lateCompensationAfterNormalReleaseDoesNotDoubleDecrementCount()` | `@Test` |
| 10 | `releaseBeforeReserveCreatesTombstone()` | `@Test` |
| 11 | `releasingOtherRoomReservationIsRejected()` | `@Test` |
| 12 | `unavailableRoomCannotBeReserved()` | `@Test` |
| 13 | `capacityValidation()` | `@Test` |
| 14 | `invalidDateRangeOrMoreThan365Nights()` | `@Test` |
| 15 | `roomIsAvailableForPeriodAgainAfterRelease()` | `@Test` |

#### `RoomSelectionPerHotelTest` — 1

Выбор стратегии, установленной на уровне отеля.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomSelectionPerHotelTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `missingOverrideUsesHotelStrategy()` | `@Test` |

#### `RoomServiceCoverageUnitTest` — 21

Покрытие сервиса номеров: CRUD, конфликты, детали и подбор.  
Исходник: `hotel-service/src/test/java/com/hotelbooking/hotel/service/RoomServiceCoverageUnitTest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `createRoomRequiresExistingHotel()` | `@Test` |
| 2 | `createRoomConvertsUniqueConstraint()` | `@Test` |
| 3 | `missingRoomGet()` | `@Test` |
| 4 | `missingRoomDelete()` | `@Test` |
| 5 | `roomWithReservationCannotBeDeleted()` | `@Test` |
| 6 | `deleteRoomRemovesPhotosThenRow()` | `@Test` |
| 7 | `deleteDatabaseConflictIsResourceInUse()` | `@Test` |
| 8 | `roomSearchRejectsHugePage()` | `@Test` |
| 9 | `roomSearchRequiresHotelId()` | `@Test` |
| 10 | `roomSearchRequiresExistingHotel()` | `@Test` |
| 11 | `roomSearchRejectsInvalidCapacity()` | `@Test` |
| 12 | `roomSearchRejectsNegativeMinPrice()` | `@Test` |
| 13 | `roomSearchRejectsNegativeMaxPrice()` | `@Test` |
| 14 | `roomSearchRejectsInvertedPriceRange()` | `@Test` |
| 15 | `roomSearchRejectsUnknownSort()` | `@Test` |
| 16 | `roomSearchRejectsBadDirection()` | `@Test` |
| 17 | `roomSearchUsesPaginationAndSort()` | `@Test` |
| 18 | `recommendationRejectsInvalidHotel()` | `@Test` |
| 19 | `recommendationRejectsPastDate()` | `@Test` |
| 20 | `recommendationRejectsLongStay()` | `@Test` |
| 21 | `recommendationRejectsZeroGuests()` | `@Test` |

### 5.4. Booking Service — E2E, отказоустойчивость и нагрузка (13 классов, 115 методов)

#### `AdminPhotosStrategyExtendedE2ETest` — 8

Администрирование отелей, номеров, фотографий и стратегий через реальные HTTP-запросы.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/AdminPhotosStrategyExtendedE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `nonAdminCannotCreateHotelsOrRooms()` | `@Test` |
| 2 | `anonymousCannotMutateHotel()` | `@Test` |
| 3 | `administratorCreatesHotelAndRoomsAndCanReadThem()` | `@Test` |
| 4 | `strategyIsPerHotelAndAdminOnly()` | `@Test` |
| 5 | `hotelPhotoLifecycleAndAuthorization()` | `@Test` |
| 6 | `roomPhotoLifecycleAndAuthorization()` | `@Test` |
| 7 | `unsupportedImageMustBeRejected()` | `@Test` |
| 8 | `deletingRoomRemovesAssociatedPhotoMetadata()` | `@Test` |

#### `AuthenticationSecurityExtendedE2ETest` — 5

Проверка аутентификации, JWT, прав и ограничителей при параллельных запросах.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/AuthenticationSecurityExtendedE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `wrongPasswordRejectedAndValidPasswordWorks()` | `@Test` |
| 2 | `distributedAttemptsAgainstOneAccountAreBounded()` | `@Test` |
| 3 | `garbageJwtAndMissingJwtCannotReadProtectedApi()` | `@Test` |
| 4 | `ordinaryUserCannotReadAdminUsers()` | `@Test` |
| 5 | `sameJwtConcurrentChecksDoNotReturn5xx()` | `@Test` |

#### `HotelBookingLoadE2ETest` — 11

Нагрузочные и конкурентные HTTP-сценарии бронирования, чтения и отмены.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/HotelBookingLoadE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `parallelAuthenticatedRequests100()` | `@Test` |
| 2 | `catalogRead5000()` | `@Test` |
| 3 | `competingForOneRoom200()` | `@Test` |
| 4 | `sameIdempotencyKey200()` | `@Test` |
| 5 | `parallelAutoSelect200()` | `@Test` |
| 6 | `parallelCancellation100()` | `@Test` |
| 7 | `overlappingAndAdjacentDates200()` | `@Test` |
| 8 | `mixedTraffic2000()` | `@Test` |
| 9 | `parallelSecurity500()` | `@Test` |
| 10 | `eventualSagaAndCountersConverge()` | `@Test` |
| 11 | `parallelPhotoReads1000VerifySha256()` | `@Test` |

#### `HotelBookingSagaFaultE2ETest` — 4

Отказы Hotel Service и восстановление Saga, включая ручную повторную постановку.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/HotelBookingSagaFaultE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `hotelOutOfService200BookingsFailWithoutFalseConfirmation()` | `@Test` |
| 2 | `cancellation100DuringOutageEventuallyCompletes()` | `@Test` |
| 3 | `bookedRoomIsNotLostDuringReadAndCreationOutage()` | `@Test` |
| 4 | `exhaustedRecoveryAndAdminManualRequeue()` | `@Test` |

#### `HotelBookingSagaLoadE2ETest` — 6

Стресс-нагрузка Saga, повторные запросы и гонки отмены/создания.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/HotelBookingSagaLoadE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `changedPayloadWithSameIdempotencyKey100()` | `@Test` |
| 2 | `reserveCancelRebook300Cycles()` | `@Test` |
| 3 | `cancelVsNewBookingRace200Requests()` | `@Test` |
| 4 | `fullyBookedHotelAutoSelect500()` | `@Test` |
| 5 | `exactTimesBookedAfter250ReservationsAndCancellation()` | `@Test` |
| 6 | `mixedFiveMinuteSagaSoak()` | `@Test` |

#### `HotelBookingSmokeE2ETest` — 1

Сквозной smoke-сценарий регистрации, входа и каталога.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/HotelBookingSmokeE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `userCanRegisterLoginAndOpenCatalog()` | `@Test` |

#### `ResilienceBookingE2ETest` — 9

Сквозные проверки идемпотентности, занятости, отмены и счётчика.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceBookingE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `bookingAndCancelRestoreCounter()` | `@Test` |
| 2 | `simultaneous200AttemptsSameRoomCannotDoubleBook()` | `@Test` |
| 3 | `hundredReplaysSameKeyProduceSingleReservation()` | `@Test` |
| 4 | `differentPayloadSameKeyCannotCreateSecondBooking()` | `@Test` |
| 5 | `invalidDateRangeRejectedWithoutIncrementingCounter()` | `@Test` |
| 6 | `zeroGuestCountRejected()` | `@Test` |
| 7 | `excessiveGuestCountRejected()` | `@Test` |
| 8 | `noKeyBookingMustBeRejected()` | `@Test` |
| 9 | `hundredParallelCounterReadsAgree()` | `@Test` |

#### `ResilienceBoundaryE2ETest` — 15

Границы HTTP API: несуществующие ресурсы и неверные значения.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceBoundaryE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `missingHotelReturns404()` | `@Test` |
| 2 | `missingRoomReturns404()` | `@Test` |
| 3 | `missingBookingReturns404()` | `@Test` |
| 4 | `invalidRoomCategoryReturnsClientError()` | `@Test` |
| 5 | `invalidRoomSortReturnsClientError()` | `@Test` |
| 6 | `invalidRecommendationDateReturnsClientError()` | `@Test` |
| 7 | `userCannotDeleteRoom()` | `@Test` |
| 8 | `anonymousCannotDeleteRoom()` | `@Test` |
| 9 | `anonymousCannotResetStrategy()` | `@Test` |
| 10 | `userCannotDeleteHotel()` | `@Test` |
| 11 | `invalidStrategyRejected()` | `@Test` |
| 12 | `hotelsAndRoomsHaveValidPagination()` | `@Test` |
| 13 | `photosForMissingHotelReturn404()` | `@Test` |
| 14 | `photosForMissingRoomReturn404()` | `@Test` |
| 15 | `unknownHotelStatisticsReturn404()` | `@Test` |

#### `ResilienceMediaE2ETest` — 15

Сквозные сценарии загрузки, чтения, удаления и защиты фото.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceMediaE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `hotelPhotosStartEmpty()` | `@Test` |
| 2 | `roomPhotosStartEmpty()` | `@Test` |
| 3 | `hotelPhotoUploadAndExactDownload()` | `@Test` |
| 4 | `roomPhotoUploadAndExactDownload()` | `@Test` |
| 5 | `userCannotUploadHotelPhoto()` | `@Test` |
| 6 | `userCannotUploadRoomPhoto()` | `@Test` |
| 7 | `anonymousCannotUploadPhoto()` | `@Test` |
| 8 | `invalidHotelImageIsRejected()` | `@Test` |
| 9 | `invalidRoomImageIsRejected()` | `@Test` |
| 10 | `deletedHotelPhotoNotDownloadable()` | `@Test` |
| 11 | `deletedRoomPhotoNotDownloadable()` | `@Test` |
| 12 | `userCannotDeleteHotelPhoto()` | `@Test` |
| 13 | `userCannotDeleteRoomPhoto()` | `@Test` |
| 14 | `concurrentDownloadsKeepBytesIntact()` | `@Test` |
| 15 | `roomDeleteWithPhotoHasNoDanglingRecord()` | `@Test` |

#### `ResilienceReadLoadE2ETest` — 10

Нагрузочное чтение, разграничение данных и ошибки входных JSON.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceReadLoadE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `twoHundredRoomListReadsRespond200()` | `@Test` |
| 2 | `twoHundredMyBookingsReadsRespond200()` | `@Test` |
| 3 | `alternatingUserTokensKeepDataPrivate()` | `@Test` |
| 4 | `repeatedAdminListIsAccessible()` | `@Test` |
| 5 | `malformedJsonHotelCreationFailsWith400()` | `@Test` |
| 6 | `blankHotelNameIsRejected()` | `@Test` |
| 7 | `incompleteLoginDoesNotReturnToken()` | `@Test` |
| 8 | `unsupportedContentTypeDoesNotReturn500()` | `@Test` |
| 9 | `userCannotCreateRoomEvenWithValidHotel()` | `@Test` |
| 10 | `strategyReadRemainsAvailableAfterRepeatedCatalogReads()` | `@Test` |

#### `ResilienceSecurityE2ETest` — 19

Сквозная защита маршрутов, пользователей и ролей.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceSecurityE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `anonymousProfileRejected()` | `@Test` |
| 2 | `anonymousBookingsRejected()` | `@Test` |
| 3 | `anonymousHotelMutationRejected()` | `@Test` |
| 4 | `malformedJwtCannotOpenProfile()` | `@Test` |
| 5 | `malformedJwtCannotListBookings()` | `@Test` |
| 6 | `malformedJwtCannotReadHotelCatalog()` | `@Test` |
| 7 | `userCannotListAllUsers()` | `@Test` |
| 8 | `userCannotListAllBookings()` | `@Test` |
| 9 | `userCannotCreateHotel()` | `@Test` |
| 10 | `userCannotModifyStrategy()` | `@Test` |
| 11 | `authenticatedCatalogRespondsNormally()` | `@Test` |
| 12 | `authenticatedOwnBookingsRespondNormally()` | `@Test` |
| 13 | `twoHundredConcurrentProfileReadsHaveNoErrors()` | `@Test` |
| 14 | `twoHundredConcurrentCatalogReadsHaveNoErrors()` | `@Test` |
| 15 | `twoHundredInvalidTokensNeverSucceed()` | `@Test` |
| 16 | `wrongPasswordNeverIssuesToken()` | `@Test` |
| 17 | `nonexistentAccountNeverIssuesToken()` | `@Test` |
| 18 | `invalidJsonCannotAuthenticate()` | `@Test` |
| 19 | `unknownRouteMustNotLeakServerFailure()` | `@Test` |

#### `ResilienceStrategyE2ETest` — 10

Стратегии подбора номеров через API: права, изоляция по отелю, изменения.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/ResilienceStrategyE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `newHotelDefaultsToLeastBooked()` | `@Test` |
| 2 | `adminCanSetMostExpensive()` | `@Test` |
| 3 | `adminCanRestoreLeastBooked()` | `@Test` |
| 4 | `differentHotelsKeepIndependentStrategies()` | `@Test` |
| 5 | `strategyStatePersistsAcrossSeparateHttpRequests()` | `@Test` |
| 6 | `userCannotSetMostExpensive()` | `@Test` |
| 7 | `anonymousCannotSetStrategy()` | `@Test` |
| 8 | `invalidStrategyDoesNotOverwriteCurrent()` | `@Test` |
| 9 | `concurrentReadAfterUpdateIsConsistent()` | `@Test` |
| 10 | `switchBetweenModesLeavesNoUnexpectedValue()` | `@Test` |

#### `TwoHundredUserAuthenticationE2ETest` — 2

Конкурентная аутентификация 200 пользователей и неправильные пароли.  
Исходник: `booking-service/src/test/java/com/hotelbooking/booking/e2e/TwoHundredUserAuthenticationE2ETest.java`.

| № | Тестовый метод | Аннотация |
|---:|---|---|
| 1 | `twoHundredDistinctUsersLoginAtOnce()` | `@Test` |
| 2 | `twoHundredSimultaneousWrongPasswordsCannotCauseServerFailure()` | `@Test` |

## 6. Как интерпретировать результаты

- `PASSED` — выполненный метод завершился успешно; `FAILED` — обнаружено несоответствие или ошибка окружения; `SKIPPED`/`ABORTED` — сценарий **не был проверен полностью**.
- Для `@ParameterizedTest` один метод может давать несколько записей в отчёте; для E2E с `hotel.e2e` выключенным методы пропускаются.
- Нагрузочные сценарии следует анализировать отдельно: число успешных операций, неожиданные `5xx`, конкурентность, состояние Saga и итоговый `timesBooked`.
- Перед сравнением результатов с документацией нужно смотреть отчёт **текущего запуска**. Данный перечень автоматически получен из исходников и не является свидетельством прохождения 425 проверок.

## 7. Ручные и скриптовые сценарии (вне JUnit-каталога)

Эти файлы присутствуют в корне проекта, но **не входят в подсчёт 425 JUnit-методов**:

| Файл | Назначение | Запуск/условия |
|---|---|---|
| `requests.http` | Основные CRUD, security, booking, Saga, валидация и дополнительные проверки API | IntelliJ HTTP Client, запущенные сервисы |
| `idempotency-tests.http` | Replay и конфликты `Idempotency-Key` при бронировании | HTTP Client, актуальные `roomId` и даты |
| `stage12-photos.http` | Загрузка, чтение, управление фото, проверка прав и форматов | HTTP Client, подготовленные локальные файлы изображений |
| `tokenrequests.http` | Управление токенами при сбросе/смене пароля | HTTP Client, ADMIN и тестовый USER |
| `run-parallel-booking-test.cmd` | Запуск PowerShell-набора параллельных бронирований | Windows и запущенные сервисы |
| `parallel-booking-test-fixed.ps1` | Шесть сценариев конкуренции: ручной выбор, автоматический подбор, идемпотентность и пересечения | PowerShell 5.1+; резервирует реальные тестовые номера |

Ресурсы `booking-service/src/test/resources/e2e-load/test-photo.jpg` и `hotel-service/src/test/resources/stage30-valid.webp` — **тестовые данные**, не отдельные тесты. Списки проверок внутри `.http` могут иметь комментарии/сценарии без автоматических JUnit-assertions: они приводятся как дополнительный источник для ручного прогона.

## 8. Связанные документы

- [Модель данных](database.md) — таблицы и ограничения H2/JPA.
- [REST API](api.md) — публичные эндпойнты.
- [Модули системы](modules.md) — назначение микросервисов.
- [Реестр ADR](adr/README.md) — принятые архитектурные решения.
