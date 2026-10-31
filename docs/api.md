# REST API — карта контроллеров

Основной адрес для клиентских запросов — `http://localhost:8080` (API Gateway). Методы ниже соответствуют REST-контроллерам Booking Service и Hotel Service. Таблица включает также служебные маршруты, которые **не являются публичным клиентским API** и недоступны через Gateway.

Для защищённых операций используйте заголовок `Authorization: Bearer <JWT>`. Для создания бронирования дополнительно обязателен `Idempotency-Key` (UUID). Административные методы защищены ролью `ADMIN`, внутренние Saga-методы — служебной авторизацией и блокируются внешним Gateway.

| Сервис | Метод | Путь | Контроллер |
|---|---|---|---|
| `booking-service` | `POST` | `/api/users` | `AdminUserController` |
| `booking-service` | `GET` | `/api/users` | `AdminUserReadController` |
| `booking-service` | `GET` | `/api/users/me` | `UserProfileController` |
| `booking-service` | `PATCH` | `/api/users/me` | `UserProfileController` |
| `booking-service` | `POST` | `/api/bookings` | `BookingController` |
| `booking-service` | `GET` | `/api/bookings` | `BookingController` |
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
| `booking-service` | `GET` | `/api/users/token-status` | `UserPasswordController` (**внутренний; через Gateway запрещён**) |
| `hotel-service` | `POST` | `/api/hotels` | `HotelController` |
| `hotel-service` | `GET` | `/api/hotels` | `HotelController` |
| `hotel-service` | `POST` | `/api/rooms` | `RoomController` |
| `hotel-service` | `GET` | `/api/rooms` | `RoomController` |
| `hotel-service` | `GET` | `/api/hotels/{hotelId}/photos` | `HotelPhotoController` |
| `hotel-service` | `GET` | `/api/rooms/{roomId}/photos` | `RoomPhotoController` |
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
- `GET /api/users/token-status` — **внутренняя проверка действительности пользовательского JWT**, вызываемая Gateway напрямую в Booking Service с JWT и `X-Gateway-Internal-Secret`. В `GatewaySecurityConfig` внешние запросы к этому пути явно запрещены (`denyAll`), даже если операция видна в Swagger Booking Service. Клиентам вызывать её не нужно.
- `POST /api/rooms/{id}/confirm-availability` и `/release` — **внутренние операции** Booking→Hotel, не выполнять через Gateway как пользователь.

## HTTP-ошибки

Предусмотрены проверки `400` (валидация), `401` (нет/недействительный JWT), `403` (нет полномочий), `404`, `409` (конфликт бронирования, идемпотентности или ресурсов), `429` (лимит) и `503` (зависимый сервис недоступен). Точные коды/поля error JSON зависят от конкретного обработчика; для интеграции следует ориентироваться на исходный `ControllerAdvice` и тесты.

## Swagger/OpenAPI

Booking Service и Hotel Service используют `springdoc-openapi-starter-webmvc-ui:3.1.1` и собственные `OpenApiConfig` с описанием API и схемой `bearerAuth` (HTTP Bearer JWT). Контроллеры распределены по группам через `@Tag`; регистрация и вход отмечены как операции без JWT. Служебный `RoomReservationController` исключён из спецификации через `@Hidden`.

| Сервис | Swagger UI | JSON-спецификация |
|---|---|---|
| Booking | `http://localhost:8081/swagger-ui.html` | `http://localhost:8081/v3/api-docs` |
| Hotel | `http://localhost:8082/swagger-ui.html` | `http://localhost:8082/v3/api-docs` |

Для выполнения защищённого запроса в Swagger UI нажмите **Authorize**, вставьте пользовательский JWT и используйте **Try it out → Execute**. UI доступен без токена; доступ к операциям регулируется Spring Security.
