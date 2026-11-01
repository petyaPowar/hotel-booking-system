# common

Библиотека для `hotel-service` и `booking-service` (Spring MVC). Не запускается, порта нет. Подключается зависимостью
`com.hotelbooking:common`; автоконфигурация регистрируется через
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Решение — [ADR-0007](../docs/adr/0007-shared-common-module.md).

## Пакеты (`com.hotelbooking.common`)

| Пакет | Содержимое |
|---|---|
| `security` | `ResourceServerAutoConfiguration` (HS256 `JwtDecoder` с проверкой срока, обязательного claim `exp` и issuer, `JwtAuthenticationConverter` `roles` → `ROLE_*`, `@EnableMethodSecurity`), `JwtProperties` (`app.jwt.secret` ≥ 32 символов, `ttl`, `issuer`), `RestAuthenticationEntryPoint` и `RestAccessDeniedHandler` (JSON 401/403 через `JsonErrorWriter`), `Roles` (`USER`, `ADMIN`, `INTERNAL`, claims `roles`, `uid`), `Authorities` (выражения `@PreAuthorize`: `PUBLIC`, `ADMIN`, `INTERNAL`, `USER_OR_ADMIN`, `USER_ADMIN_OR_INTERNAL`) |
| `error` | `ErrorResponse`, `ErrorResponseFactory` (берёт `traceId` из MDC), `GlobalExceptionHandler`, `ApiException` и наследники `NotFoundException` (404), `ConflictException` (409) |
| `logging` | `BookingMdc` — ключ MDC `bookingId` и `put(bookingId)` для корреляции логов в обоих сервисах |
| `web` | `PageResponse<T>` — `{content, page, size, totalElements, totalPages}`; `RequestPaths.publicPath(request)` — `X-Forwarded-Prefix` (ставит gateway при `StripPrefix`) + URI запроса, используется как `path` в ошибках |
| `validation` | `DateRange` (`rangeStart()`, `rangeEnd()`), `@ValidDateRange` (конец периода строго позже начала; поле ошибки задаётся `field`, по умолчанию `endDate`; сообщение `end date must be after start date`), `DateRangeValidator`, `@MaxUtf8Bytes(n)` (строка не длиннее `n` байт в UTF-8, null допустим; для паролей — предел BCrypt 72 байта), `MaxUtf8BytesValidator`, `ConstraintViolations` |

`GlobalExceptionHandler`:

| Исключение | Ответ |
|---|---|
| `ApiException` | свой статус и сообщение |
| Bean Validation (`MethodArgumentNotValidException`, `ConstraintViolationException`) | 400 `Validation failed` с `fieldErrors`; ошибка типа поля (code `typeMismatch`) — `Invalid value` |
| `HttpMessageNotReadableException` | 400 `Malformed request body` |
| `MethodArgumentTypeMismatchException` | 400 `Invalid value for parameter '<имя>'` |
| `PropertyReferenceException` (неизвестное поле сортировки) | 400 `Invalid sort property '<поле>'` |
| `NoResourceFoundException` | 404 `Resource not found` |
| `AuthenticationException` / `AccessDeniedException` | 401 / 403 |
| `DataIntegrityViolationException`, `OptimisticLockingFailureException` | 409 `Concurrent modification or duplicate data` |
| исключения Spring, реализующие `org.springframework.web.ErrorResponse` (415, 406, 405, отсутствующий заголовок или параметр) | свой статус и заголовки |
| прочее | 500 `Internal server error`, стек в лог на ERROR |

Детали отклонённых запросов пишутся в лог на WARN. `SecurityFilterChain` в библиотеке не объявляется: каждый сервис
задаёт его сам.

## Сборка и тесты

```bash
mvn -pl common install
```

Собственных тестов нет; поведение проверяется интеграционными тестами `hotel-service` и `booking-service`.
