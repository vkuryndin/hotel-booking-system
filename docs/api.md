# REST API — карта контроллеров

Источник: аннотации маршрутизации Java-контроллеров актуального backend. Базовый внешний адрес — `http://localhost:8080`. Маршруты, роли, request/response DTO и ограничения следует проверять в соответствующих контроллерах; таблица фиксирует реальные пути, **а не гарантирует доступность каждого метода всем ролям**.

Для защищённых операций используйте заголовок `Authorization: Bearer <JWT>`. Для создания бронирования дополнительно обязателен `Idempotency-Key` (UUID). Административные методы защищены ролью `ADMIN`, внутренние Saga-методы — служебной авторизацией и блокируются внешним Gateway.

| Сервис | Метод | Путь | Контроллер |
|---|---|---|---|
| `booking-service` | `DELETE` | `/api/users/{id}` | `AdminUserController` |
| `booking-service` | `GET` | `/api/users/{id}` | `AdminUserReadController` |
| `booking-service` | `PATCH` | `/api/users/{id}` | `AdminUserUpdateController` |
| `booking-service` | `POST` | `/api/users/{id}/reset-password` | `AdminUserUpdateController` |
| `booking-service` | `GET` | `/api/bookings/my` | `BookingController` |
| `booking-service` | `GET` | `/api/bookings/{id}` | `BookingController` |
| `booking-service` | `PATCH` | `/api/bookings/{id}/cancel` | `BookingController` |
| `booking-service` | `GET` | `/api/bookings/recovery/exhausted` | `BookingRecoveryAdminController` |
| `booking-service` | `POST` | `/api/bookings/{id}/recovery/retry` | `BookingRecoveryAdminController` |
| `booking-service` | `POST` | `/api/users/login` | `LoginController` |
| `booking-service` | `POST` | `/api/users/register` | `RegistrationController` |
| `booking-service` | `POST` | `/api/users/me/change-password` | `UserPasswordController` |
| `booking-service` | `GET` | `/api/users/token-status` | `UserPasswordController` |
| `hotel-service` | `GET` | `/api/hotels/{id}` | `HotelController` |
| `hotel-service` | `PUT` | `/api/hotels/{id}` | `HotelController` |
| `hotel-service` | `GET` | `/api/hotels/{id}/room-selection-strategy` | `HotelController` |
| `hotel-service` | `PUT` | `/api/hotels/{id}/room-selection-strategy` | `HotelController` |
| `hotel-service` | `DELETE` | `/api/hotels/{id}` | `HotelController` |
| `hotel-service` | `GET` | `/api/hotels/{hotelId}/photos/{photoId}/file` | `HotelPhotoController` |
| `hotel-service` | `POST` | `/api/hotels/{hotelId}/photos` | `HotelPhotoController` |
| `hotel-service` | `PATCH` | `/api/hotels/{hotelId}/photos/{photoId}/primary` | `HotelPhotoController` |
| `hotel-service` | `PATCH` | `/api/hotels/{hotelId}/photos/{photoId}/order` | `HotelPhotoController` |
| `hotel-service` | `DELETE` | `/api/hotels/{hotelId}/photos/{photoId}` | `HotelPhotoController` |
| `hotel-service` | `PATCH` | `/api/hotels/{hotelId}/presentation` | `HotelPresentationController` |
| `hotel-service` | `GET` | `/api/hotels/{hotelId}/statistics` | `HotelStatisticsController` |
| `hotel-service` | `GET` | `/api/rooms/{id}` | `RoomController` |
| `hotel-service` | `PATCH` | `/api/rooms/{id}/availability` | `RoomController` |
| `hotel-service` | `GET` | `/api/rooms/available` | `RoomController` |
| `hotel-service` | `PUT` | `/api/rooms/{id}` | `RoomController` |
| `hotel-service` | `DELETE` | `/api/rooms/{id}` | `RoomController` |
| `hotel-service` | `GET` | `/api/rooms/recommend` | `RoomController` |
| `hotel-service` | `PATCH` | `/api/rooms/{roomId}/details` | `RoomDetailsController` |
| `hotel-service` | `GET` | `/api/rooms/{roomId}/photos/{photoId}/file` | `RoomPhotoController` |
| `hotel-service` | `POST` | `/api/rooms/{roomId}/photos` | `RoomPhotoController` |
| `hotel-service` | `PATCH` | `/api/rooms/{roomId}/photos/{photoId}/primary` | `RoomPhotoController` |
| `hotel-service` | `PATCH` | `/api/rooms/{roomId}/photos/{photoId}/order` | `RoomPhotoController` |
| `hotel-service` | `DELETE` | `/api/rooms/{roomId}/photos/{photoId}` | `RoomPhotoController` |
| `hotel-service` | `POST` | `/api/rooms/{id}/confirm-availability` | `RoomReservationController` |
| `hotel-service` | `POST` | `/api/rooms/{id}/release` | `RoomReservationController` |

## Параметры и примеры

- `GET /api/rooms`: обязателен `hotelId`; есть фильтры `category`, `minCapacity`, `minPrice`, `maxPrice`, `available`, `amenity`, `sortBy`, `direction` и параметры `page/size`.
- `GET /api/rooms/recommend`: обязательны `hotelId`, `checkIn`, `checkOut`; дополнительный `strategy`.
- `POST /api/bookings`: тело `BookingRequest(roomId, hotelId, autoSelect, checkIn, checkOut, guestCount, strategy)`; даты в `yyyy-MM-dd`. При автоотборе `roomId` игнорируется.
- `GET /api/bookings/my` и `GET /api/bookings` возвращают страницы (`Page`) с настройками пагинации; второй метод только для ADMIN.
- `PATCH /api/bookings/{id}/cancel` отменяет бронирование; `DELETE` для данной операции **не используется**.
- `POST /api/rooms/{id}/confirm-availability` и `/release` — **внутренние операции** Booking→Hotel, не выполнять через Gateway как пользователь.

## HTTP-ошибки

Предусмотрены проверки `400` (валидация), `401` (нет/недействительный JWT), `403` (нет полномочий), `404`, `409` (конфликт бронирования, идемпотентности или ресурсов), `429` (лимит) и `503` (зависимый сервис недоступен). Точные коды/поля error JSON зависят от конкретного обработчика; для интеграции следует ориентироваться на исходный `ControllerAdvice` и тесты.

## Swagger/OpenAPI

В обоих сервисах присутствует зависимость `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`. Поскольку настройка Swagger находится на паузе, не считаем работу `/swagger-ui.html` гарантированной; после завершения задачи следует отдельно проверить `/v3/api-docs`, UI, политики JWT и сокрытие внутренних методов.
