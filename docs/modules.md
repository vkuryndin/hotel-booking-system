# Компоненты и ответственность

## discovery-server (:8761)

Spring Cloud Netflix Eureka Server — реестр адресов сервисов, регистрация инстансов, динамический поиск. Не содержит бизнес-операций бронирования. При старте другим приложениям необходимо время для регистрации.

## api-gateway (:8080)

Spring Cloud Gateway Server WebFlux: маршрут `Path=/api/hotels/**,/api/rooms/**` к `lb://hotel-service`, `Path=/api/bookings/**,/api/users/**` к `lb://booking-service`. Использует Spring Security Resource Server. В конфигурациях/фильтрах учитываются внутренние секреты и трассировка; Gateway не является хранилищем пользователей. Внутренние POST-операции подтверждения и освобождения номера запрещены для публичного вызова.

## booking-service (:8081)

Spring MVC + Data JPA + Security: регистрация и логин, выдача JWT, администрирование пользователей, бронирования. `BookingService` управляет запросом; `BookingSagaStateService` — состоянием, `HotelServiceClient` — HTTP-вызовами в Hotel Service, `BookingSagaRecoveryService`, `ManualBookingRecoveryService` и `ExhaustedSagaMonitor` — восстановлением зависших операций. Хранилище — `jdbc:h2:mem:bookingdb`.

## hotel-service (:8082)

Хранит отели, номера, резервирование по датам и фотографии; выполняет статистику, фильтры и алгоритмы рекомендации. `RoomReservationController` открывает внутренний контракт подтверждения/освобождения, `RoomSelectionStrategy` с реализациями `LeastBookedStrategy` и `MostExpensiveStrategy` обеспечивает подбор. Хранилище — `jdbc:h2:mem:hoteldb`; фото — локальные файлы (`HOTEL_PHOTOS_DIR`).

## Межсервисные границы

- Booking Service — источник истины о пользовательских бронированиях и их жизненном цикле.
- Hotel Service — источник истины о доступности слота номера на интервал дат; собственные резервирования и защита от гонок.
- Глобальных транзакций XA/2PC нет: Saga координирует последовательность локальных операций, с повтором, компенсацией и восстановлением.
- При изменении контракта сначала проверяйте DTO и тесты обеих сторон, затем API Gateway и документацию.
