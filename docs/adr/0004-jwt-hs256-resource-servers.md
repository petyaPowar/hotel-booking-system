# ADR-0004. JWT HS256, каждый сервис — resource server

## Контекст

Нужна stateless-аутентификация с ролями USER и ADMIN и защита внутренних эндпоинтов hotel-service, которые вызывает
только booking-service. Отдельного сервера авторизации нет.

## Решение

- Токены выпускает `booking-service` (`JwtTokenIssuer`, `POST /user/register`, `POST /user/auth`) алгоритмом HS256 с
  общим секретом `app.jwt.secret` (`JWT_SECRET`, не короче 32 символов). Claims: `iss=hotel-booking`, `sub=username`,
  `uid=<id пользователя>`, `roles=["USER"|"ADMIN"]`, `iat`, `exp = iat + app.jwt.ttl` (PT1H). Пароли — BCrypt-хэш.
- `api-gateway` (reactive resource server) проверяет подпись, срок (claim `exp` обязателен) и issuer; публичны только
  `/api/bookings/user/register`, `/api/bookings/user/auth`, `/actuator/health/**`. `Authorization` передаётся в сервисы
  без изменений.
- `hotel-service` и `booking-service` — тоже resource server (автоконфигурация из `common`): повторно проверяют токен и
  роли через `@PreAuthorize`; claim `roles` → `ROLE_*`. Эндпоинты бронирований берут id пользователя из `uid`; нет или не
  числовой → `401` (`CurrentUser`).
- Межсервисные вызовы: `booking-service` выпускает служебный токен `sub=booking-service`, `roles=["INTERNAL"]`
  (`ServiceTokenInterceptor`). `confirm-availability` и `release` требуют `hasRole('INTERNAL')`, gateway отвечает на эти
  пути `404`.

## Последствия

- Нет обращений к серверу авторизации на каждый запрос; сервисы защищены и при обращении в обход gateway.
- Пользователь не может вызвать внутренние эндпоинты: роль INTERNAL есть только у служебного токена.

### Ограничения

- Секрет симметричный и общий для трёх сервисов: компрометация любого позволяет выпускать токены. Значение по
  умолчанию в `application.yml` учебное, вне локального запуска его заменяют через `JWT_SECRET`.
- Отзыва токенов нет; смена роли или удаление пользователя (`PATCH`/`DELETE /user/{id}`) не инвалидируют выданные
  токены до истечения `exp` (1 час).
- Eureka server без аутентификации: любой процесс в сети может зарегистрироваться или прочитать реестр. Данные сервисов
  при этом защищены JWT; вне локального окружения реестр закрывают Spring Security на eureka-server или сетевым периметром.
