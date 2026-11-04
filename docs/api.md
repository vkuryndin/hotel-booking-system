# REST API

Клиентский API доступен через API Gateway по адресу `http://localhost:8080`. Пользовательские маршруты обрабатывает Booking Service, маршруты отелей и номеров — Hotel Service. Оба сервиса самостоятельно валидируют JWT; права `ADMIN` дополнительно защищены на уровне методов. В таблицах ниже `?` обозначает необязательное поле или параметр.

## Авторизация и общие соглашения

- Для защищённых операций используется `Authorization: Bearer <JWT>`, срок действия пользовательского токена — 1 час. Регистрация и вход открыты без JWT.
- При создании бронирования обязателен заголовок `Idempotency-Key` со значением UUID. Повтор с тем же ключом и теми же данными не должен создавать вторую бронь.
- Даты `checkIn` и `checkOut` передаются в формате `YYYY-MM-DD`; интервал ночей — `[checkIn, checkOut)`, дата выезда не включается.
- Все `page` имеют нумерацию с нуля. Параметры `size` и `sort` используются с правилами `Pageable` Spring Data; размер страницы по умолчанию — 20 там, где указан `@PageableDefault(size=20)`.
- `available` — административный признак номера. Реальная занятость дат определяется по резервированиям.
- Служебные маршруты подтверждения/освобождения слота и проверки статуса JWT не предназначены для внешних клиентов и блокируются Gateway.

## Карта операций

Таблицы приводят объявленные контроллерами операции. Столбец «Ответы» содержит документированные коды операции; дополнительные сетевые ошибки Gateway возможны до попадания запроса в сервис.

### Booking Service

| Метод | Путь | Доступ | Параметры / тело | Ответы HTTP |
|---|---|---|---|---|
| `POST` | `/api/users` | ADMIN | JSON: `email`, `username`, `password`, `role`, `firstName`, `lastName`; `phone?` | 201, 400, 401, 403, 409 |
| `GET` | `/api/users` | ADMIN | `q?`, `page?`, `size?`, `sort?` | 200, 400, 401, 403 |
| `GET` | `/api/users/me` | USER / ADMIN | — | 200, 401, 403 |
| `PATCH` | `/api/users/me` | USER / ADMIN | JSON: `username?`, `phone?`, `firstName?`, `lastName?` | 200, 400, 401, 403, 409 |
| `POST` | `/api/bookings` | USER / ADMIN | JSON: `autoSelect`, `roomId?`, `hotelId?`, `checkIn`, `checkOut`, `guestCount?`, `strategy?`; заголовок `Idempotency-Key` (UUID) | 201, 400, 401, 403, 404, 409, 503 |
| `GET` | `/api/bookings` | ADMIN | `status?`, `page?`, `size?`, `sort?` | 200, 400, 401, 403 |
| `DELETE` | `/api/users/{id}` | ADMIN | `id` | 200, 400, 401, 403, 404, 409 |
| `GET` | `/api/users/{id}` | ADMIN | `id` | 200, 400, 401, 403, 404 |
| `PATCH` | `/api/users/{id}` | ADMIN | `id`; JSON: `email?`, `username?`, `role?`, `phone?`, `firstName?`, `lastName?` | 200, 400, 401, 403, 404, 409, 429 |
| `POST` | `/api/users/{id}/reset-password` | ADMIN | `id`; JSON: `newPassword` | 200, 400, 401, 403, 404, 429 |
| `GET` | `/api/bookings/my` | USER / ADMIN | `status?`, `checkOutFrom?` (ISO), `page?`, `size?`, `sort?` | 200, 400, 401, 403 |
| `GET` | `/api/bookings/{id}` | USER — своё / ADMIN — любое | `id` | 200, 400, 401, 403, 404 |
| `PATCH` | `/api/bookings/{id}/cancel` | USER — своё / ADMIN — любое | `id` | 200, 400, 401, 403, 404, 409, 503 |
| `GET` | `/api/bookings/recovery/exhausted` | ADMIN | Без параметров; список исчерпанных восстановлений | 200, 401, 403 |
| `POST` | `/api/bookings/{id}/recovery/retry` | ADMIN | `id` | 202, 400, 401, 403, 404, 409 |
| `POST` | `/api/users/login` | Публичный | JSON: `email`, `password` | 200, 400, 401, 429 |
| `POST` | `/api/users/register` | Публичный | JSON: `email`, `password`, `username`, `firstName`, `lastName`; `phone?` | 201, 400, 409, 429 |
| `POST` | `/api/users/me/change-password` | USER / ADMIN | JSON: `currentPassword`, `newPassword` | 200, 400, 401, 403, 429 |
| `GET` | `/api/users/token-status` | Внутренний Gateway → Booking | JWT + `X-Gateway-Internal-Secret`; через Gateway запрещён | 204, 401, 403 |

### Hotel Service

| Метод | Путь | Доступ | Параметры / тело | Ответы HTTP |
|---|---|---|---|---|
| `POST` | `/api/hotels` | ADMIN | JSON: `name`, `address` | 201, 400, 401, 403, 409 |
| `GET` | `/api/hotels` | USER / ADMIN | `name?`, `address?`, `minRating?`, `amenity?`, `q?`, `amenities?`, `sortBy?`, `direction?`, `page?`, `size?` | 200, 400, 401, 403 |
| `POST` | `/api/rooms` | ADMIN | JSON: `hotelId`, `number`; `category?`, `capacity?`, `pricePerNight?`, `amenities?` | 201, 400, 401, 403, 404, 409 |
| `GET` | `/api/rooms` | USER / ADMIN | `hotelId`, `category?`, `minCapacity?`, `minPrice?`, `maxPrice?`, `available?`, `amenity?`, `sortBy?`, `direction?`, `page?`, `size?` | 200, 400, 401, 403, 404 |
| `GET` | `/api/hotels/{hotelId}/photos` | USER / ADMIN | `hotelId` | 200, 400, 401, 403, 404 |
| `GET` | `/api/rooms/{roomId}/photos` | USER / ADMIN | `roomId` | 200, 400, 401, 403, 404 |
| `GET` | `/api/hotels/{id}` | USER / ADMIN | `id` | 200, 400, 401, 403, 404 |
| `PUT` | `/api/hotels/{id}` | ADMIN | `id`; JSON: `name`, `address` | 200, 400, 401, 403, 404, 409 |
| `GET` | `/api/hotels/{id}/room-selection-strategy` | USER / ADMIN | `id` | 200, 400, 401, 403, 404 |
| `PUT` | `/api/hotels/{id}/room-selection-strategy` | ADMIN | `id`; JSON: `strategy` | 200, 400, 401, 403, 404 |
| `DELETE` | `/api/hotels/{id}` | ADMIN | `id` | 204, 400, 401, 403, 404, 409 |
| `GET` | `/api/hotels/{hotelId}/photos/{photoId}/file` | USER / ADMIN | `hotelId`, `photoId`; выдача `image/*` | 200, 400, 401, 403, 404 |
| `POST` | `/api/hotels/{hotelId}/photos` | ADMIN | `hotelId`; `multipart/form-data`: поле `file` | 201, 400, 401, 403, 404, 413, 415 |
| `PATCH` | `/api/hotels/{hotelId}/photos/{photoId}/primary` | ADMIN | `hotelId`, `photoId` | 200, 400, 401, 403, 404 |
| `PATCH` | `/api/hotels/{hotelId}/photos/{photoId}/order` | ADMIN | `hotelId`, `photoId`, `sortOrder` (query) | 200, 400, 401, 403, 404 |
| `DELETE` | `/api/hotels/{hotelId}/photos/{photoId}` | ADMIN | `hotelId`, `photoId` | 204, 400, 401, 403, 404 |
| `PATCH` | `/api/hotels/{hotelId}/presentation` | ADMIN | `hotelId`; JSON: `description?`, `rating?`, `amenities?`, `checkInTime?`, `checkOutTime?` | 200, 400, 401, 403, 404 |
| `GET` | `/api/hotels/{hotelId}/statistics` | ADMIN | `hotelId` | 200, 400, 401, 403, 404 |
| `GET` | `/api/rooms/{id}` | USER / ADMIN | `id` | 200, 400, 401, 403, 404 |
| `PATCH` | `/api/rooms/{id}/availability` | ADMIN | `id`; JSON: `available` | 200, 400, 401, 403, 404, 409 |
| `GET` | `/api/rooms/available` | USER / ADMIN | `hotelId`, `page?`, `size?`, `sort?` | 200, 400, 401, 403, 404 |
| `PUT` | `/api/rooms/{id}` | ADMIN | `id`; JSON: `number` | 200, 400, 401, 403, 404, 409 |
| `DELETE` | `/api/rooms/{id}` | ADMIN | `id` | 204, 400, 401, 403, 404, 409 |
| `GET` | `/api/rooms/recommend` | USER / ADMIN | `hotelId`, `checkIn`, `checkOut`; `strategy?`, `guestCount?` | 200, 400, 401, 403, 404 |
| `PATCH` | `/api/rooms/{roomId}/details` | ADMIN | `roomId`; JSON: `category?`, `capacity?`, `pricePerNight?`, `amenities?` | 200, 400, 401, 403, 404 |
| `GET` | `/api/rooms/{roomId}/photos/{photoId}/file` | USER / ADMIN | `roomId`, `photoId`; выдача `image/*` | 200, 400, 401, 403, 404 |
| `POST` | `/api/rooms/{roomId}/photos` | ADMIN | `roomId`; `multipart/form-data`: `file` | 201, 400, 401, 403, 404, 413, 415 |
| `PATCH` | `/api/rooms/{roomId}/photos/{photoId}/primary` | ADMIN | `roomId`, `photoId` | 200, 400, 401, 403, 404 |
| `PATCH` | `/api/rooms/{roomId}/photos/{photoId}/order` | ADMIN | `roomId`, `photoId`, `sortOrder` (query) | 200, 400, 401, 403, 404 |
| `DELETE` | `/api/rooms/{roomId}/photos/{photoId}` | ADMIN | `roomId`, `photoId` | 204, 400, 401, 403, 404 |
| `POST` | `/api/rooms/{id}/confirm-availability` | Внутренний Booking → Hotel | `id`; service JWT; JSON: `reservationId`, `checkIn`, `checkOut`, `guestCount?` | 200, 400, 401, 403, 409 |
| `POST` | `/api/rooms/{id}/release` | Внутренний Booking → Hotel | `id`; service JWT; JSON: `reservationId`, `compensation` | 200, 400, 401, 403, 409 |

## Параметры поиска и сортировки

### Отели — `GET /api/hotels`

| Параметр | Значение |
|---|---|
| `name`, `address` | Поиск по названию или адресу |
| `q` | Поиск по названию либо адресу одним запросом |
| `minRating` | Нижняя граница рейтинга |
| `amenity` | Одно удобство из `WIFI`, `PARKING`, `POOL`, `GYM`, `RESTAURANT` |
| `amenities` | Список удобств, которые должны присутствовать одновременно |
| `sortBy`, `direction` | Поле сортировки (по умолчанию `id`), направление `asc` или `desc` |
| `page`, `size` | Номер и размер страницы (по умолчанию 20 записей) |

### Номера — `GET /api/rooms`

Обязателен `hotelId`. Допускаются `category` (`STANDARD`, `DELUXE`, `SUITE`), `minCapacity`, `minPrice`, `maxPrice`, `available`, `amenity` (`WIFI`, `TV`, `AIR_CONDITIONING`, `MINIBAR`, `BALCONY`), `sortBy`, `direction`, `page`, `size`. Для списка только административно доступных номеров предусмотрен `GET /api/rooms/available?hotelId=...`; он **не проверяет свободные даты**. Подбор по датам доступен через `GET /api/rooms/recommend`.

### Бронирования и пользователи

- `GET /api/bookings/my`: `status?`, `checkOutFrom?`, `page?`, `size?`, `sort?`. Для фильтра `checkOutFrom` используется дата ISO; в запросе с этой датой необходимо указать `status=CONFIRMED`.
- `GET /api/bookings` (ADMIN): `status?`, `page?`, `size?`, `sort?`.
- `GET /api/users` (ADMIN): `q?` (поиск по имени пользователя, имени, фамилии или email), `page?`, `size?`, `sort?`.

## Бронирование и стратегии выбора

При **ручном** выборе клиент передаёт `autoSelect=false` и `roomId`. При **автоматическом** подборе — `autoSelect=true` и `hotelId`; указанный `roomId` игнорируется. Поле `guestCount` по умолчанию равно 1 (допустимый диапазон 1–20).

Стратегия по умолчанию — `LEAST_BOOKED` (по возрастанию `times_booked`, при равенстве по `id`). Дополнительный вариант `MOST_EXPENSIVE` ранжирует номера сначала по категории (`SUITE` → `DELUXE` → `STANDARD`), внутри каждой категории — по убыванию `pricePerNight`, а при равной цене — по возрастанию `id`. Поэтому номер более высокой категории имеет приоритет независимо от цены номера более низкой категории. Текущая логика приоритета: **параметр `strategy` запроса → стратегия отеля → `LEAST_BOOKED` для отеля без индивидуальной настройки**. Стратегию отеля изменяет ADMIN через `PUT /api/hotels/{id}/room-selection-strategy`. При ручном выборе `strategy` не влияет на номер.

Пример создания с ручным выбором:

```http
POST http://localhost:8080/api/bookings
Authorization: Bearer <JWT>
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{
  "roomId": 1,
  "autoSelect": false,
  "checkIn": "2027-04-10",
  "checkOut": "2027-04-12",
  "guestCount": 2
}
```

Пример автоматического подбора (без переопределения стратегии отеля):

```json
{
  "hotelId": 1,
  "autoSelect": true,
  "checkIn": "2027-04-10",
  "checkOut": "2027-04-12",
  "guestCount": 2
}
```

Даты в примерах демонстрационные; для выполнения запроса они должны быть допустимыми на момент запуска.

## Запросы изменения данных

- `HotelRequest`: обязательные `name` (до 150 символов), `address` (до 255); оформление меняется отдельно через `PATCH /api/hotels/{hotelId}/presentation`.
- `HotelPresentationRequest`: `description?` (до 3000), `rating?` (0–5), `amenities?`, `checkInTime?`, `checkOutTime?`.
- `RoomRequest`: обязательные `hotelId`, `number` (до 20); дополнительные `category?`, `capacity?`, `pricePerNight?`, `amenities?`.
- `RoomUpdateRequest`: только `number`; изменения категории, вместимости, цены и удобств выполняются через `PATCH /api/rooms/{roomId}/details`.
- Регистрация: `email`, `password` (8–64 символа), `username` (3–30 символов с заданным шаблоном), `firstName`, `lastName`; `phone` необязателен.
- Изменение профиля и пользователя администратором — частичные операции `PATCH`. Изменение собственной роли ADMIN и удаление последнего администратора ограничены.
- Фотографии загружаются через `multipart/form-data`, поле `file`. Поддерживаются JPEG/PNG/WebP, до 5 МБ на файл и до 10 фотографий на объект. Выдача бинарного файла осуществляется `GET .../photos/{photoId}/file`, тип ответа соответствует изображению.

## Примеры JSON-ответов

Ниже приведены примеры **формата фактических DTO** из Booking Service. Значения идентификаторов, дат и персональных данных условные; JWT заменён заполнителем. При реальном вызове они будут другими.

**Регистрация** — `POST /api/users/register`, `201 Created`, заголовок `Location: /api/users/1`. Возвращается `UserResponse`: регистрация создаёт пользователя, **но не выдаёт JWT**; для получения токена нужен отдельный запрос входа.

```json
{
  "id": 1,
  "email": "anna@example.com",
  "username": "anna",
  "phone": null,
  "firstName": "Anna",
  "lastName": "Petrova",
  "role": "USER"
}
```

**Вход** — `POST /api/users/login`, `200 OK`. Возвращается `LoginResponse`; `expiresIn` указан в секундах (при текущем `PT1H` — 3600).

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "passwordChangeRequired": false,
  "username": "anna",
  "firstName": "Anna",
  "lastName": "Petrova",
  "email": "anna@example.com"
}
```

**Успешное бронирование** — `POST /api/bookings`, `201 Created`, заголовок `Location: /api/bookings/42`. Возвращается `BookingResponse`; в примере Saga завершилась подтверждением (`CONFIRMED`).

```json
{
  "id": 42,
  "userId": 1,
  "roomId": 7,
  "checkIn": "2027-04-10",
  "checkOut": "2027-04-12",
  "status": "CONFIRMED",
  "createdAt": "2026-11-04T09:00:00Z",
  "guestCount": 2
}
```

## Ответы и ошибки

Успешное создание бронирования/отеля/номера возвращает `201 Created`; удаление отеля, номера или фотографии — `204 No Content`, удаление пользователя — `200 OK` с телом ответа, административный повтор Recovery — `202 Accepted`. Чтение и изменение обычно возвращают `200 OK`. Ответы поиска используют Spring Data `Page` (поля `content`, `totalElements`, `totalPages`, `number`, `size` и др.).

Сервисные ошибки используют `ErrorResponse`:

```json
{
  "timestamp": "2026-11-03T09:00:00Z",
  "status": 409,
  "code": "ROOM_ALREADY_BOOKED",
  "message": "Room is already booked for selected dates: 7",
  "path": "/api/bookings",
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

Поля `traceId` и `fieldErrors` зависят от контекста и наличия данных; некоторые поля могут отсутствовать. Коды: `400` — валидация, `401` — JWT, `403` — права, `404` — ресурс не найден, `409` — конфликт, `413` — размер фотографии, `415` — формат файла, `429` — Rate Limiting, `503` — временная недоступность зависимости. При `429` ориентируйтесь на `Retry-After`, когда этот заголовок присутствует. Для подробной OpenAPI-спецификации см. [Swagger и OpenAPI](swagger.md).

## Внутренние операции

`POST /api/rooms/{id}/confirm-availability` и `POST /api/rooms/{id}/release` вызываются Booking Service со служебным JWT; `GET /api/users/token-status` используется Gateway для проверки статуса пользовательского токена. Клиентские запросы к этим маршрутам через Gateway запрещены. Наличие пути в контроллере не означает разрешённый публичный доступ.
