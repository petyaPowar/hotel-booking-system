# api-gateway

Единая точка входа на Spring Cloud Gateway (WebFlux). Порт 8080. Запускается последним.

## Маршруты

| Предикат | Куда | Фильтр |
|---|---|---|
| `/api/rooms/*/confirm-availability`, `/api/rooms/*/release` | — | `NotPublished`: ответ `404` с JSON-телом `ErrorResponse`, запрос в сервисы не уходит; пути открыты в `SecurityConfig`, поэтому 404 возвращается и без токена |
| `/api/bookings/**` | `lb://booking-service` | `StripPrefix=2`; gateway добавляет `X-Forwarded-Prefix: /api/bookings`, booking-service подставляет его в `path` ошибок |
| `/api/hotels/**`, `/api/rooms/**` | `lb://hotel-service` | — |

Адреса сервисов берутся из Eureka (`lb://`), реестр обновляется каждые 5 с. Заголовок `Authorization` передаётся в
сервисы без изменений, роли проверяют сами сервисы. Список эндпоинтов — в [README проекта](../README.md#эндпоинты).

## Пакеты (`com.hotelbooking.gateway`)

| Пакет | Содержимое |
|---|---|
| `config` | `SecurityConfig` (reactive resource server; публичны `/api/bookings/user/register`, `/api/bookings/user/auth`, `/actuator/health/**`, остальное требует JWT), `JwtConfig` (HS256-декодер с проверкой срока, обязательного `exp` и issuer, `roles` → `ROLE_*`), `JwtProperties` (`app.jwt.secret`, `app.jwt.issuer`) |
| `security` | `JsonAuthenticationEntryPoint`, `JsonAccessDeniedHandler` — 401/403 в формате `ErrorResponse` |
| `filter` | `RequestLoggingFilter` — лог `метод путь -> статус in N ms traceId=...` и заголовок ответа `X-Trace-Id`; `NotPublishedGatewayFilterFactory` — фильтр маршрута, отвечающий 404 |
| `tracing` | `TraceIdResolver` — `traceId` текущего span Micrometer |
| `error` | `ErrorResponse` `{timestamp, status, error, message, path, traceId}` (форма `common`, без `fieldErrors`); `JsonErrorWriter`; `GatewayErrorWebExceptionHandler` (`@Order(-2)`, раньше стандартного обработчика): `ResponseStatusException` — свой статус, нет экземпляров сервиса — 503 `Upstream service unavailable` (имя сервиса не раскрывается), отказ в соединении — 503, превышение `response-timeout` — 504, прочее — 500 |

Модуль не зависит от `common`: он реактивный, а `common` рассчитан на Spring MVC.

## Конфигурация

`JWT_SECRET` (тот же, что в сервисах), `EUREKA_URL`; `spring.cloud.gateway.server.webflux.httpclient.connect-timeout: 2000` (ms),
`response-timeout: 35s` — больше худшего времени `POST /booking` с `autoSelect` (≈ 30s, [ADR-0005](../docs/adr/0005-resilience-hotel-client.md)).
Eureka: `registry-fetch-interval-seconds: 5`, `lease-renewal-interval-in-seconds: 5`, `spring.cloud.loadbalancer.cache.ttl: 5s`.
Логи: консоль и ECS JSON в `logs/api-gateway.log`.

## Запуск

```bash
mvn -pl api-gateway spring-boot:run
# или
java -jar api-gateway/target/api-gateway-1.0.0.jar
```

## Тесты

```bash
mvn -pl api-gateway verify
```

Профиль `test` (`src/test/resources/application-test.yml`) выключает Eureka и файловый лог; бэкенды подменены WireMock
через `spring.cloud.discovery.client.simple.instances.*`, токены выпускает `TestTokens`. 17 тестов:

| Класс | Что проверяется |
|---|---|
| `ApiGatewayRoutingTest` | 401 с JSON-телом без токена, с истёкшим токеном, токеном без `exp`, чужим секретом и issuer; внутренние пути → 404 с JSON-телом (и без токена); бэкенд не ответил за `response-timeout` → 504 с JSON-телом; register/auth проходят без токена со снятым префиксом; `/api/bookings/**` снимает префикс и передаёт `Authorization`; `/api/hotels/**`, `/api/rooms/**` маршрутизируются без изменения пути; ответ содержит `X-Trace-Id`; health публичен |
| `ApiGatewayErrorHandlingTest` | у `hotel-service` нет экземпляров (`Upstream service unavailable`), `booking-service` указывает на закрытый порт — оба случая → 503 с JSON-телом и `traceId` |
