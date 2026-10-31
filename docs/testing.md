# Проверка и демонстрация

## Unit и интеграционные тесты

Из корня проекта в Windows PowerShell:

```powershell
.\gradlew.bat clean test
```

macOS/Linux:

```bash
chmod +x ./gradlew
./gradlew clean test
```

В исходниках есть тесты каждого модуля (`src/test/java`), включая security, booking и hotel domain. 

## E2E

E2E классы содержатся в модуле Booking Service, среди них `HotelBookingSmokeE2ETest`, `HotelBookingSagaFaultE2ETest`, `HotelBookingLoadE2ETest`, `AuthenticationSecurityExtendedE2ETest`, `ResilienceBoundaryE2ETest` и другие. Они предполагают **запущенные отдельно** Eureka, Gateway, Hotel и Booking, корректные JWT/service secrets и, для некоторых fault-сценариев, специальную локальную конфигурацию. Используйте подготовленную в IntelliJ конфигурацию `E2E All`, из конфигураций запуска проекта. Не смешивайте её с `clean test`.

Проверочный цикл:

1. Остановить четыре сервиса, запустить Eureka → Gateway → Hotel → Booking.
2. Убедиться, что сервисы зарегистрированы в Eureka.
3. Убедиться, что тестовые секреты доступны только локально, а не в Git.
4. Запустить E2E All.
5. Проверить отсутствие неразрешённых `503`, неожиданных `409`, зависших Saga, расхождения `timesBooked` и зарезервированных слотов.



## Конфигурация `E2E All` в IntelliJ IDEA

В **Run → Edit Configurations → + → JUnit** задайте:

| Поле | Значение |
|---|---|
| **Name** | `E2E All` |
| **JRE** | Java 17 |
| **Use classpath of module** | `hotel-booking-system.booking-service.test` |
| **Test kind** | `All in package` |
| **Package** | `com.hotelbooking.booking.e2e` |
| **VM options** | `-ea -Dhotel.e2e=true` |
| **Working directory** | `$MODULE_WORKING_DIR$` |
| **Environment variables** | Не требуются для основной конфигурации `E2E All` |

Если поле **VM options** отсутствует, откройте **Modify options → Add VM options**. Конфигурацию можно сохранить в проект через **Store as project file**; на предоставленном скриншоте этот флажок для `E2E All` не установлен, поэтому настройку может потребоваться создать заново на другом компьютере.

Перед запуском E2E должны работать четыре Spring Boot конфигурации из [README](../README.md#конфигурации-запуска-в-intellij-idea). В них нужны одинаковый `GATEWAY_INTERNAL_SECRET`, корректные JWT keystore и `APP_DEMO_ENABLED=true` в Booking и Hotel. Для административных E2E учётные данные демо-администратора в блоке `Login as ADMIN` файла `requests.http` должны совпадать с настройками `ADMIN_EMAIL` / `ADMIN_PASSWORD` Booking Service.

**Сценарий Saga Fault с исчерпанием повторов (№21):** используется локальный файл `booking-service/secrets/saga-e2e-admin.properties` со свойствами `email` и `password` администратора. Из-за `Working directory = $MODULE_WORKING_DIR$` тест находит его по относительному пути `secrets/saga-e2e-admin.properties`. Отсутствие файла приводит к пропуску сценария №21. Тест входа ADMIN также использует блок `Login as ADMIN` из `requests.http`. Локальные пароли не следует помещать в Git.

```properties
email=<адрес администратора>
password=<пароль администратора>
```

Тест `HotelBookingSagaFaultE2ETest` изменяет статус **единственного локального** экземпляра Hotel Service в Eureka на `OUT_OF_SERVICE`, а затем возвращает `UP`; для этих сценариев не запускайте вторую копию Hotel Service.

## Ручная демонстрация для ментора

| Сценарий | Ожидаемый результат |
|---|---|
| Регистрация + вход | Получен действительный JWT, срок 1 ч |
| Нет JWT / нет роли | HTTP 401 / 403 соответственно |
| Создание отеля и номера ADMIN | Успех и последующее чтение данных |
| Подбор по `timesBooked` | Сортировка от меньшего счётчика, при равенстве по ID |
| Создание брони | Состояние `CONFIRMED` при доступном номере |
| Двойная бронь на пересекающиеся даты | Только одна успешно резервирует тот же слот, остальные получают конфликт |
| Повтор того же `Idempotency-Key` | Не создаёт вторую бронь |
| Сбой/тайм-аут Hotel | Retry/компенсация либо контролируемый recovery-сценарий |
| Отмена подтверждённой брони | Освобождение занятости и однократная корректировка счётчика |
| Фильтры и пагинация | Согласованные страницы результатов |
| Photo upload | Метаданные и файл доступны только по предусмотренному контракту |

Для ручных запросов имеются `requests.http`, `idempotency-tests.http` и другие `.http` примеры. Время и параметры `checkIn/checkOut` нужно обновлять на будущие даты. 

## Проверка Swagger

При запущенных Booking Service и Hotel Service откройте `/swagger-ui.html` на портах `8081` и `8082` соответственно. Проверьте `/v3/api-docs`, авторизацию через кнопку **Authorize** и выполнение защищённого запроса с JWT. В Hotel Swagger внутренние методы `confirm-availability` и `release` не отображаются.
