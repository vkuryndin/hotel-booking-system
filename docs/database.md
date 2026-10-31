# Модель данных и ограничения

Оба бизнес-сервиса используют независимые H2 in-memory с `hibernate.ddl-auto=create-drop`. Таблицы формируются JPA/Hibernate при запуске. Ниже приведены классы-сущности исходного кода (фактические SQL-имена зависят от `@Table`, `@Column` и naming strategy).

| Сервис | Сущность | Назначение |
|---|---|---|
| `booking-service` | `AppUser` (`app_users`) | JPA entity |
| `booking-service` | `Booking` (`bookings`) | JPA entity |
| `hotel-service` | `Hotel` | JPA entity |
| `hotel-service` | `HotelPhoto` (`hotel_photos`) | JPA entity |
| `hotel-service` | `Room` (`rooms`) | JPA entity |
| `hotel-service` | `RoomPhoto` (`room_photos`) | JPA entity |
| `hotel-service` | `RoomReservation` (`room_reservations`) | JPA entity |
| `hotel-service` | `RoomReservationNight` (`room_reservation_nights`) | JPA entity |

## Основные связи и правила

- `AppUser` связан с `Booking`; `Booking` хранит идентификатор номера и даты проживания, статус `PENDING/CONFIRMED/CANCELLED`, а также поля корреляции/идемпотентности.
- `Hotel` содержит номера `Room`; `Room` имеет операционный флаг `available` и статистический счётчик `timesBooked`.
- `RoomReservation` и `RoomReservationNight` в Hotel Service обеспечивают фиксацию и проверку занятости конкретных дат; это отдельно от флага `available`.
- `HotelPhoto` и `RoomPhoto` содержат метаданные фотографий; бинарные данные располагаются в файловом каталоге.
- В сущностях применяются уникальные ограничения, индексы и связи JPA; актуальные имена полей, состав индексов и ограничений необходимо брать из `entity/*.java` в проверяемой версии исходного кода.

**Важная семантика:** отменённые брони не считаются действующими для `timesBooked`; при подтверждении +1, при отмене −1 однократно, успешная компенсация не должна оставлять прирост. Пересечение дат проверяется на уровне резервирований Hotel Service, а не булевым `Room.available`.

## Данные при рестарте

При рестарте процессов H2 очищается. Демо-инициализаторы могут повторно создать стартовые записи; секреты демопользователей/администратора берутся из окружения. Для E2E лучше использовать чистый запуск и контролировать тестовые фикстуры.
