# ADR-0001. Сага с оркестрацией и компенсацией

## Контекст

Бронирование меняет две базы: `bookings` в `booking-service` и `room_reservations` в `hotel-service`. Базы
независимы (database per service), общего транзакционного менеджера нет; XA потребовал бы координатора и держал бы
блокировки на время сетевых вызовов. Вызов может завершиться таймаутом после того, как hotel-service уже удержал номер.

## Решение

Оркестрируемая сага в `booking-service` (`saga/BookingSagaOrchestrator`):

1. Повтор `(userId, requestId)` → существующее бронирование, `200` (другие даты или `roomId` → `409`, ADR-0002).
2. Кандидаты: `[roomId]` или при `autoSelect=true` список из `GET /api/rooms/recommend`; пустой список → `409`.
3. Локальная транзакция: бронирование `PENDING`.
4. Для каждого кандидата `POST /api/rooms/{id}/confirm-availability` с `requestId = <bookingId>:<roomId>`:
   - `200` → транзакция `PENDING → CONFIRMED` (с фактическим `roomId`), ответ `201`; если эта транзакция упала —
     компенсация (release + `CANCELLED`), ошибка клиенту;
   - `409` → следующий кандидат;
   - `404` → при `autoSelect` следующий кандидат, при ручном выборе `CANCELLED` и `404`;
   - таймаут, 5xx после ретраев, circuit breaker OPEN → компенсация `POST /api/rooms/{id}/release` (best effort:
     ошибка компенсации только логируется), `PENDING → CANCELLED`, `503`;
   - другая ошибка API → `CANCELLED`, ошибка клиенту.
5. Кандидаты исчерпаны → `CANCELLED`, `409`.

Переходы статуса выполняет `BookingStateService` (`@Transactional` на каждом методе), HTTP-вызовы идут вне транзакций.
Допустимые переходы проверяет сущность `Booking`: `PENDING → CONFIRMED | CANCELLED`, `CONFIRMED → CANCELLED`, иначе `409`.
Release идёт через отдельный retry-инстанс `hotelRelease` без circuit breaker, поэтому выполняется и при OPEN (ADR-0005).

Отмена (`DELETE /booking/{id}`): release, затем `CANCELLED`; hotel-service недоступен → бронирование остаётся
`CONFIRMED`, клиент получает `503`. `PENDING` владелец отменить не может (`409`: сага ещё выполняется), ADMIN — может тем
же путём. Уже отменённое бронирование (в том числе параллельной отменой) возвращается без изменений, `200`.

Release без удержания (confirm ещё не дошёл до hotel-service): если записи с этим `requestId` нет, hotel-service
сохраняет «надгробие» — `room_reservations` со статусом `RELEASED` и этим `requestId` (даты — день release; занятость и
статистика учитывают только `HELD`). Опоздавший confirm с тем же `requestId` получает `409`. Гонку release/confirm
исключают уникальность `request_id` и блокировка строки номера (ADR-0003).

## Последствия

- Нет распределённых блокировок; каждая база консистентна локально, система — в конечном счёте.
- Промежуточный статус `PENDING` виден клиенту.
- Компенсация снимает удержание и после таймаута, и после сбоя локального подтверждения: номер не остаётся занятым
  при отменённом бронировании.
- Исход компенсации в логе: `Booking status PENDING -> CANCELLED (compensated)`; при неудаче —
  `Compensation failed for booking ... on room ...` и `Booking status PENDING -> CANCELLED (compensation failed for room X)`
  на уровне ERROR с `bookingId` в MDC.

### Ограничения

- Нет фонового reconciler'а: если release исчерпал ретраи, удержание может остаться при `CANCELLED`; если booking-service
  упал посреди саги, бронирование остаётся `PENDING`, номер — удержанным. Сбой фиксируется в логе ERROR с `bookingId`;
  зависшее `PENDING` ADMIN закрывает `DELETE /booking/{id}` (release, затем `CANCELLED`).
- Обе базы in-memory: перезапуск одного сервиса расходит данные, `hotel-service` и `booking-service` перезапускаются вместе.
