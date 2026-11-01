# hotel-service

Отели и номера, удержание номеров на даты, рекомендации и статистика загрузки. Порт 8081, база H2
`jdbc:h2:mem:hoteldb` (`LOCK_TIMEOUT=10000`). Зависит от `common`.

## Пакеты (`com.hotelbooking.hotel`)

| Пакет | Содержимое |
|---|---|
| `controller` | `HotelController` (`/api/hotels`), `RoomController` (`/api/rooms`), `RoomAvailabilityController` (`/api/rooms/{id}/confirm-availability`, `/release`, роль INTERNAL) |
| `service`, `service.impl` | `HotelService`, `RoomService`, `RoomAvailabilityService` (confirm/release под пессимистической блокировкой), `OccupancyStatsService` и `OccupancyMath` (расчёт ночей и `occupancyRate`) |
| `repository` | Spring Data JPA; `RoomRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`), `findFree`; `HotelSpecifications` — фильтр по `name`/`address` |
| `entity` | `Hotel`, `Room`, `RoomReservation`, `ReservationStatus` (`HELD`, `RELEASED`) |
| `dto`, `mapper` | record-DTO с Bean Validation (периоды — `@ValidDateRange` из `common`), MapStruct |
| `config` | `SecurityConfig` (публичны Swagger, `/v3/api-docs/**`, `/actuator/health`, `/actuator/info`), `OpenApiConfig`, `DataSeeder` |

## Эндпоинты

| Метод и путь | Роль |
|---|---|
| `POST /api/hotels`, `PUT/DELETE /api/hotels/{id}`, `GET /api/hotels/stats?from&to` | ADMIN |
| `GET /api/hotels?name&address&page&size&sort`, `GET /api/hotels/{id}` | USER, ADMIN |
| `POST /api/rooms`, `PUT/DELETE /api/rooms/{id}`, `GET /api/rooms/stats?from&to&hotelId&sortBy&direction` | ADMIN |
| `GET /api/rooms?startDate&endDate&hotelId`, `GET /api/rooms/recommend?...` | USER, ADMIN, INTERNAL |
| `GET /api/rooms/{id}` | USER, ADMIN |
| `POST /api/rooms/{id}/confirm-availability`, `POST /api/rooms/{id}/release` | INTERNAL (через gateway — 404) |

Роли в `@PreAuthorize` — константы `Authorities` из `common`; ошибки — `GlobalExceptionHandler` из `common`.
Тела и коды ответов — в [README проекта](../README.md#эндпоинты), схема БД — в [docs/database.md](../docs/database.md).

## Особенности

- Confirm в одной транзакции: блокировка строки номера → поиск по `requestId` (`<bookingId>:<roomId>`): найденное
  удержание возвращается, если совпадают номер, `bookingId`, даты и оно ещё `HELD`, иначе `409` (в том числе для
  записи `RELEASED`) → проверки `available` и пересечения с `HELD` → новое удержание, `times_booked + 1`.
- Release под той же блокировкой снимает все `HELD` удержания `bookingId` на номере, `times_booked` уменьшается (не ниже 0).
  Если удержаний нет и записи с таким `requestId` нет, сохраняется «надгробие» — запись `RELEASED` с этим `requestId`
  (даты — день release; занятость и статистика учитывают только `HELD`). Опоздавший confirm с тем же `requestId` получает
  `409` ([ADR-0001](../docs/adr/0001-saga-with-compensation.md), [ADR-0003](../docs/adr/0003-pessimistic-room-lock.md)).
- В MDC на время confirm/release кладётся `bookingId` (`BookingMdc` из `common`).
- Сид: удержания `1:1`, `2:5`, `3:9` согласованы с бронированиями 1–3 в booking-service.
- Swagger UI: http://localhost:8081/swagger-ui.html. Логи: консоль и ECS JSON в `logs/hotel-service.log`.
- Конфигурация (`JWT_SECRET`, `EUREKA_URL`, `APP_SEED_ENABLED`, `lease-renewal-interval-in-seconds: 5`,
  `max-page-size: 100`, `spring.web.locale`) — в [README проекта](../README.md#конфигурация).

## Запуск

```bash
mvn -pl hotel-service spring-boot:run
# или
java -jar hotel-service/target/hotel-service-1.0.0.jar
```

## Тесты

```bash
mvn -pl hotel-service -am verify
```

Профиль `test` (`src/test/resources/application-test.yml`, активируется в `IntegrationTest` и `DataSeederTest`):
отдельная H2 `hoteltestdb`, Eureka и сидирование выключены, файловый лог отключён; токены выпускает `JwtTestTokens`.
73 теста:

| Класс | Что проверяется |
|---|---|
| `RoomAvailabilityControllerTest` | удержание и рост `timesBooked`; идемпотентность по `requestId`; 409 при пересечении дат, недоступном номере, повторе `requestId` для другого номера или бронирования, повторе после release; release без удержания оставляет «надгробие», опоздавший confirm с тем же `requestId` → 409; заезд в день выезда разрешён; снятое удержание не мешает; release идемпотентен и не трогает другие брони; 401/403 для не-INTERNAL; 400 |
| `ConfirmAvailabilityConcurrencyTest` | параллельные подтверждения одного номера на пересекающиеся даты → ровно одно удержание |
| `HotelControllerTest`, `RoomControllerTest` | CRUD, роли, фильтры, пагинация, 400/404/409; токен без `exp` → 401; неизвестное поле сортировки → 400, `text/plain` → 415, битый JSON → 400, нечисловой `hotelId` → 400 `Invalid value`; поиск свободных номеров; сортировка рекомендаций `timesBooked`, `id` |
| `OccupancyStatsControllerTest`, `OccupancyMathTest` | расчёт и сортировка статистики, валидация периода и `sortBy`; обрезка ночей по периоду и округление |
| `DataSeederTest` | сидирование отелей, номеров и удержаний |
