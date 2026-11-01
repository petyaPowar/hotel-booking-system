# Architecture Decision Records

Формат: Контекст / Решение / Последствия. Статус всех записей — «Принято».

| № | Решение |
|---|---|
| [0001](0001-saga-with-compensation.md) | Сага с оркестрацией и компенсацией вместо распределённых транзакций |
| [0002](0002-idempotency-by-request-id.md) | Идемпотентность по `requestId` в обоих сервисах |
| [0003](0003-pessimistic-room-lock.md) | Пессимистическая блокировка номера при подтверждении |
| [0004](0004-jwt-hs256-resource-servers.md) | JWT HS256 с общим секретом, каждый сервис — resource server, роль INTERNAL |
| [0005](0005-resilience-hotel-client.md) | Таймауты, retry с экспоненциальной задержкой и circuit breaker для вызовов hotel-service |
| [0006](0006-room-recommendation-order.md) | Рекомендация номеров по `times_booked asc, id asc` |
| [0007](0007-shared-common-module.md) | Общий модуль `common` |
