# Компоненты и ответственность

## discovery-server (:8761)

Spring Cloud Netflix Eureka Server — реестр адресов сервисов, регистрация инстансов, динамический поиск. Не содержит бизнес-операций бронирования. При старте другим приложениям необходимо время для регистрации. Для ускорения обновления списка доступных экземпляров отключён read-only cache реестра (`eureka.server.use-read-only-response-cache=false`); обнаружение изменений при этом остаётся периодическим.

## api-gateway (:8080)

Spring Cloud Gateway Server WebFlux: маршрут `Path=/api/hotels/**,/api/rooms/**` к `lb://hotel-service`, `Path=/api/bookings/**,/api/users/**` к `lb://booking-service`. Использует Spring Security Resource Server: локально проверяет подпись и срок действия пользовательского JWT, затем через `GET /api/users/token-status` обращается к Booking Service для проверки актуальности токена (отзыв через `tokenVersion`/`tokenIdentity`). Если Booking Service недоступен, проверка завершается по принципу fail-closed: защищённые запросы отклоняются, включая запросы к Hotel Service. Gateway не хранит пользователей. В фильтрах также учитываются внутренние секреты и трассировка. Для более быстрого обновления маршрутизации задан интервал получения реестра Eureka 2 секунды и отключён кеш Spring Cloud LoadBalancer. Внутренние POST-операции подтверждения и освобождения номера запрещены для публичного вызова.

## booking-service (:8081)

Spring MVC + Data JPA + Security: регистрация и логин, выдача JWT, администрирование пользователей, бронирования. `BookingService` координирует создание/отмену бронирований и Saga; `BookingQueryService` выполняет чтение, фильтрацию и пагинацию (с проверкой прав доступа); `BookingSagaStateService` управляет переходами состояний в локальных транзакциях, `HotelServiceClient` — HTTP-вызовами в Hotel Service, `BookingSagaRecoveryService`, `ManualBookingRecoveryService` и `ExhaustedSagaMonitor` — восстановлением зависших операций. Клиент Eureka в Booking Service получает обновления реестра каждые 2 секунды. Хранилище — `jdbc:h2:mem:bookingdb`.

## hotel-service (:8082)

Хранит отели, номера, резервирование по датам и фотографии; выполняет статистику, фильтры и алгоритмы рекомендации. `HotelService` отвечает за CRUD отелей и настройку стратегии подбора, `HotelSearchService` — за чтение, фильтрацию, сортировку и пагинацию отелей. `RoomService` отвечает за CRUD и рекомендации; `RoomSearchService` — за чтение, фильтрацию, сортировку и пагинацию номеров. `RoomReservationController` открывает внутренний контракт подтверждения/освобождения, `RoomSelectionStrategy` с реализациями `LeastBookedStrategy` и `MostExpensiveStrategy` обеспечивает подбор. При отсутствии явно переданной стратегии используется настройка отеля (`LEAST_BOOKED` по умолчанию). Хранилище — `jdbc:h2:mem:hoteldb`; фото — локальные файлы (`HOTEL_PHOTOS_DIR`).

## Карта основных пакетов и классов

Пути ниже относятся к `src/main/java`. Приведены ключевые точки входа и классы с бизнес-логикой, а не полный перечень исходников.

| Модуль / пакет | Основные классы | Назначение |
|---|---|---|
| `discovery-server` · `com.hotelbooking.discovery` | `DiscoveryServerApplication` | Запуск Eureka Server. |
| `api-gateway` · `com.hotelbooking.gateway.config` | `GatewaySecurityConfig`, `GatewaySecretFilter`, `JwtRevocationFilter`, `TraceWebFilter` | Входная безопасность, контроль актуальности JWT, внутренние заголовки и `traceId`; маршруты задаются конфигурацией Gateway. |
| `booking-service` · `com.hotelbooking.booking.controller` | `RegistrationController`, `LoginController`, `BookingController`, `AdminUserController` | HTTP-контракты регистрации, входа, бронирований и управления пользователями. |
| `booking-service` · `com.hotelbooking.booking.service` | `BookingService`, `BookingQueryService`, `BookingSagaStateService`, `HotelServiceClient`, `BookingSagaRecoveryService` | Создание и чтение бронирований, состояния Saga, интеграция с Hotel Service и восстановление. |
| `booking-service` · `com.hotelbooking.booking.security` / `.config` | `ServiceTokenProvider`, `SecurityConfig`, `JwtDecoderConfig`, `HotelCircuitBreakerConfig` | Служебные JWT, правила доступа, проверка JWT и защита вызовов Hotel Service. |
| `booking-service` · `.entity` / `.repository` / `.mapper` | `Booking`, `AppUser`, `BookingRepository`, `AppUserRepository`, `BookingMapper` | JPA-модель, запросы к H2 и преобразование в DTO. |
| `hotel-service` · `com.hotelbooking.hotel.controller` / `.service` | `RoomReservationController`, `HotelService`, `HotelSearchService`, `RoomService`, `RoomSearchService` | API резервирований, CRUD, поиск и пагинация отелей и номеров. |
| `hotel-service` · `com.hotelbooking.hotel.strategy` | `RoomSelectionStrategy`, `LeastBookedStrategy`, `MostExpensiveStrategy` | Ранжирование подходящих номеров. |
| `hotel-service` · `com.hotelbooking.hotel.service` | `RoomReservationService`, `HotelPhotoService`, `RoomPhotoService`, `PhotoFileOperations` | Защита занятости дат, работа с фотографиями и согласование файлов с транзакциями. |
| `hotel-service` · `.entity` / `.repository` / `.mapper` | `Hotel`, `Room`, `HotelPhoto`, `RoomPhoto`, `HotelRepository`, `RoomRepository` | JPA-модель, метаданные фотографий и работа с H2. |

## Межсервисные границы

- Booking Service — источник истины о пользовательских бронированиях и их жизненном цикле.
- Hotel Service — источник истины о доступности слота номера на интервал дат; собственные резервирования и защита от гонок.
- Глобальных транзакций XA/2PC нет: Saga координирует последовательность локальных операций, с повтором, компенсацией и восстановлением.
- Во всех четырёх сервисах включено структурированное JSON-логирование в формате Logstash. Сквозной `traceId` связывает логи обработки запросов, межсервисных вызовов и операций Saga/Recovery.
- Контракты REST и служебных операций приведены в `docs/api.md`; транзакционная согласованность и восстановление описаны в ADR-003 и ADR-006.
