# booking-service

Пользователи, выпуск JWT, бронирования и оркестратор саги. Порт 8082, база H2 `jdbc:h2:mem:bookingdb`.
Зависит от `common`. Через gateway доступен по префиксу `/api/bookings` (gateway снимает его), поэтому внутри
сервиса пути начинаются с `/user`, `/booking`, `/bookings`.

## Пакеты (`com.hotelbooking.booking`)

| Пакет | Содержимое |
|---|---|
| `controller` | `AuthController` (`/user/register`, `/user/auth`), `UserController` (`/user`, ADMIN), `BookingController` (`/booking`, `/bookings`, `/booking/recommendations`) |
| `saga` | `BookingSagaOrchestrator` (реализует `BookingCreationService`) — сага создания бронирования, `bookingId` в MDC через `BookingMdc` |
| `service`, `service.impl` | `BookingStateService` (каждый переход статуса в своей транзакции), `BookingService` (чтение, отмена, рекомендации), `RoomRanking` (сортировка `timesBooked asc, id asc` для автоподбора и рекомендаций), `UserService`, `AuthService` |
| `client` | `HotelClient` / `RestHotelClient` (RestClient + Resilience4j `hotelService`), `ServiceTokenInterceptor` (служебный JWT роли INTERNAL, `sub=booking-service`), `ReservationRequests` (ключ для hotel-service `<bookingId>:<roomId>`), `client.dto` |
| `security` | `JwtTokenIssuer` (HS256), `CurrentUser` (id из claim `uid` и признак ADMIN; без числового `uid` → 401) |
| `entity`, `repository` | `User`, `Booking` (проверяет переходы `PENDING → CONFIRMED/CANCELLED`, `CONFIRMED → CANCELLED`, иначе 409), `BookingStatus`, `UserRole` |
| `dto`, `mapper`, `validation` | record-DTO, MapStruct, `@ValidRoomSelection` (`roomId` обязателен без `autoSelect`); периоды — `@ValidDateRange`, пароли — `@Size(6..72)` и `@MaxUtf8Bytes(72)` из `common` |
| `config` | `SecurityConfig` (BCrypt, `JwtEncoder`; публичны `/user/register`, `/user/auth`, Swagger, `/v3/api-docs/**`, `/actuator/health`, `/actuator/info`), `HotelClientConfig` и `HotelClientProperties` (`@LoadBalanced` RestClient, таймауты), `OpenApiConfig`, `DataSeeder` |
| `exception` | `RoomUnavailableException` (409), `HotelServiceUnavailableException` (503), `BookingAccessDeniedException` (403), `InvalidCredentialsException` (401) |

## Эндпоинты

| Метод и путь (внутренний; на gateway с префиксом `/api/bookings`) | Роль |
|---|---|
| `POST /user/register`, `POST /user/auth` | публичные |
| `POST /user`, `GET /user`, `GET/PATCH/DELETE /user/{id}` | ADMIN |
| `POST /booking`, `GET /bookings`, `GET/DELETE /booking/{id}`, `GET /booking/recommendations` | USER, ADMIN |

Тела и коды ответов — в [README проекта](../README.md#эндпоинты); сага — [ADR-0001](../docs/adr/0001-saga-with-compensation.md),
идемпотентность по `requestId` — [ADR-0002](../docs/adr/0002-idempotency-by-request-id.md),
retry и circuit breaker — [ADR-0005](../docs/adr/0005-resilience-hotel-client.md).

## Конфигурация

Общие параметры (`JWT_SECRET`, `EUREKA_URL`, `APP_SEED_ENABLED`, таймауты `app.hotel-client.*`, Resilience4j, Eureka,
пагинация, локаль, `SqlExceptionHelper: OFF`) — в [README проекта](../README.md#конфигурация).
Специфично для модуля: `app.hotel-client.base-url: http://hotel-service` (адрес через Eureka), `app.jwt.ttl: PT1H` — срок
выпускаемых токенов. Swagger UI: http://localhost:8082/swagger-ui.html. Логи: консоль и ECS JSON в
`logs/booking-service.log` с полями `traceId`, `spanId`, `bookingId`.

## Запуск

```bash
mvn -pl booking-service spring-boot:run
# или
java -jar booking-service/target/booking-service-1.0.0.jar
```

## Тесты

```bash
mvn -pl booking-service -am verify
```

Профиль `test` (`src/test/resources/application-test.yml`): Eureka, сидирование и файловый лог выключены, таймауты
hotel-client 500 ms, пауза retry 10 ms. `hotel-service` заменён WireMock (`IntegrationTestSupport`, `HotelServiceStubs`).
79 тестов:

| Класс | Что проверяется |
|---|---|
| `BookingSagaTest` | успешная сага (CONFIRMED, 201, запрос к hotel с `requestId` вида `<bookingId>:<roomId>` и Bearer-токеном); 409 от hotel → CANCELLED без компенсации; 500 и таймаут → 3 попытки, release, CANCELLED, 503; сбой компенсации → CANCELLED, 503 и запись `PENDING -> CANCELLED (compensation failed for room …)` в логе; circuit breaker OPEN → 503 без вызова confirm, release отправляется; одинаковый клиентский `requestId` у разных пользователей → разные `requestId` для hotel; повтор `requestId` → 200 и один вызов hotel (в том числе при `autoSelect`); повтор `requestId` с другими датами или номером → 409; параллельные дубликаты → одно бронирование (один 201, остальные 200); autoSelect берёт наименее загруженный номер (сортировка на стороне booking-service, даже если hotel вернул номера не по порядку), переходит к следующему при 409 и пропускает номер при 404; ручной выбор несуществующего номера → 404 и CANCELLED; нет свободных номеров → 409; валидация дат и `roomId` → 400; без токена → 401 |
| `BookingSagaCompensationTest` | hotel удержал номер, но локальное подтверждение упало → release и CANCELLED |
| `HotelCircuitBreakerTest` | повторяющиеся 4xx от hotel (403) не размыкают breaker; повторяющиеся 5xx размыкают его, следующий запрос не доходит до hotel; в HALF_OPEN бизнес-ответы 409 считаются успехом и закрывают breaker |
| `BookingCancelRaceTest` | отмена видит CONFIRMED, но к моменту записи бронирование уже отменено параллельно → 200 без изменений |
| `ErrorResponseTest` | неизвестное поле сортировки → 400; `text/plain` → 415; битый JSON → 400 `Malformed request body`; нечисловой `id` → 400 `Invalid value for parameter 'id'`; нечисловой `hotelId` в query → 400 с `fieldErrors.hotelId: Invalid value`; неизвестный путь → 404 `Resource not found`; `path` в ошибке (400 и 401) включает `X-Forwarded-Prefix`; неподдерживаемый метод → 405 |
| `BookingTest` | переходы статусов сущности: разрешённые и 409 для запрещённых |
| `BookingControllerTest` | доступ к бронированию владельцем, админом, чужим (403), 404; пагинация только своих бронирований; 401 без токена и при токене без `uid`; отмена с release, повторная отмена, 409 для PENDING у владельца (статус не меняется), отмена PENDING админом (release + CANCELLED), 503 при недоступном hotel (статус остаётся CONFIRMED); рекомендации (сортировка `timesBooked`, `id` на стороне booking-service) и их валидация |
| `UserControllerTest`, `AuthControllerTest` | CRUD пользователей админом, 403 для USER, 401 без токена и с неверным токеном, 409 при занятом имени и активных бронированиях; регистрация (BCrypt-хэш в БД), 400 при пароле длиннее 72 символов или 72 байт в UTF-8, вход, 401 при неверном пароле или пользователе |
