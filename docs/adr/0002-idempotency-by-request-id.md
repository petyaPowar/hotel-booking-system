# ADR-0002. Идемпотентность по requestId

## Контекст

Клиент может повторить `POST /booking` после таймаута; `booking-service` повторяет `confirm-availability` и `release`
при сбоях (ADR-0005). Повтор не должен создавать второе бронирование или удержание и повторно увеличивать `times_booked`.
Клиентский `requestId` уникален только в пределах пользователя и как ключ таблицы удержаний hotel-service не годится.

## Решение

- booking-service: клиент передаёт `requestId` (до 64 символов); в `bookings` действует `UNIQUE (user_id, request_id)`
  (`uk_bookings_user_request`). Перед сагой ищется бронирование с той же парой; найденное возвращается с `200` (новое —
  `201`) без вызовов hotel-service. Повтор должен совпадать по `startDate`, `endDate` и (при `autoSelect=false`) `roomId`,
  иначе `409` `requestId already used with different parameters`; при `autoSelect=true` `roomId` не сравнивается.
  При гонке параллельных дубликатов второй INSERT получает `DataIntegrityViolationException`, запрос перечитывает
  созданную запись и возвращает её с той же проверкой. Это штатный путь, поэтому
  `logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper: OFF`: нарушение `uk_bookings_user_request` не пишется
  как ERROR, исключение при этом доходит до саги.
- hotel-service: `booking-service` передаёт в confirm и release `requestId = "<bookingId>:<roomId>"`
  (`client/ReservationRequests`); `bookingId` глобально уникален, ключ одинаков во всех ретраях. Колонка
  `room_reservations.request_id` уникальна. Повтор confirm возвращает то же удержание, только если совпадают номер,
  `bookingId`, даты и оно ещё `HELD`; иначе `409`.
- release идемпотентен по состоянию: снимаются только удержания `HELD` данного `bookingId` на номере, повтор возвращает
  `releasedCount: 0`. Release раньше confirm оставляет «надгробие» `RELEASED` с этим `requestId` (ADR-0001), и
  опоздавший confirm получает `409`.
- Сид: удержания `1:1`, `2:5`, `3:9` для бронирований 1, 2, 3.

## Последствия

- Повтор безопасен на любом шаге: и клиентом, и retry.
- Одинаковые клиентские `requestId` у разных пользователей не конфликтуют: разные `bookingId` → разные ключи в
  hotel-service (`BookingSagaTest.createBooking_sendsDistinctHotelRequestIds_whenUsersShareClientRequestId`).
- Повтор не может присвоить чужое удержание или воскресить снятое — `409`.
- Повтор с другими параметрами — `409`, а не чужое по смыслу бронирование
  (`BookingSagaTest.createBooking_returns409_whenRequestIdReusedWithDifferentDates`, `..._whenRequestIdReusedForAnotherRoom`).
- Повтор возвращает бронирование в текущем статусе, в том числе `CANCELLED`; новая попытка — с новым `requestId`.
