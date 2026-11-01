# ADR-0005. Устойчивость вызовов hotel-service

## Контекст

Сага синхронно зависит от hotel-service: зависший вызов держит поток запроса, при недоступности сервиса каждый запрос
ждал бы таймаута. Компенсация (release) должна доходить до hotel-service и тогда, когда новые бронирования уже
блокируются из-за накопленных ошибок.

## Решение

`RestHotelClient` (RestClient через `@LoadBalanced`, адрес `http://hotel-service` из Eureka):

| Механизм | Настройка |
|---|---|
| Таймауты | `app.hotel-client.connect-timeout: 2s`, `read-timeout: 3s` |
| `resilience4j.retry.configs.default` | `max-attempts: 3`, `wait-duration: 300ms`, экспоненциальный множитель 2 (паузы 300 и 600 мс); повторяются только `ResourceAccessException` (таймаут, отказ соединения) и `HttpServerErrorException` (5xx) |
| Retry `hotelService` | `base-config: default`; confirm-availability и recommend |
| Retry `hotelRelease` | `base-config: default`; release |
| Circuit breaker `hotelService` | только confirm-availability и recommend; окно 10 вызовов, минимум 5, порог 50%, OPEN 10 с → HALF_OPEN автоматически, 5 пробных вызовов; ошибкой считаются только `ResourceAccessException` и `HttpServerErrorException` (`record-exceptions`), остальное — успех: бизнес-ответы `409`/`404` (`RoomUnavailableException`, `NotFoundException`) и прочие 4xx |
| Fallback | техническая ошибка, включая `CallNotPermittedException` и неожиданные 4xx, → `HotelServiceUnavailableException` → `503`; `ApiException` пробрасывается как есть |

Release не закрыт circuit breaker: компенсация и отмена выполняются и при OPEN, ограничиваясь таймаутами и ретраями.
Повторы безопасны благодаря идемпотентности (ADR-0002).

Окно `503` после запуска сокращено частым обновлением Eureka: `lease-renewal-interval-in-seconds: 5` (hotel-service,
booking-service, api-gateway), `registry-fetch-interval-seconds: 5` и `spring.cloud.loadbalancer.cache.ttl: 5s`
(booking-service, api-gateway).

## Последствия

- Худший случай одного вызова ≈ 10 с (3 × 3 с + паузы 0.3 и 0.6 с); при `autoSelect` до трёх вызовов подряд
  (recommend, confirm, компенсирующий release) ≈ 30 с. `response-timeout` gateway (35s) больше этого, и gateway не
  обрывает выполняющуюся сагу.
- При систематических сбоях breaker размыкается: бронирования и рекомендации получают `503` без обращения к
  hotel-service, release продолжает отправляться.
- 4xx не размыкают breaker, повторяющиеся 5xx — размыкают (`HotelCircuitBreakerTest`).
- Бизнес-ответы `409`/`404` в HALF_OPEN заполняют окно пробных вызовов и закрывают breaker. С `ignore-exceptions`
  они не учитывались бы, и breaker мог бы остаться в HALF_OPEN, отвечая `503` вместо `409`
  (`HotelCircuitBreakerTest.circuitBreaker_closesFromHalfOpen_whenHotelReturnsBusinessConflicts`).
- После старта всех сервисов `booking-service` видит `hotel-service` примерно через 5 секунд.
- Отказ hotel-service не влияет на чтение своих бронирований и работу с пользователями.
