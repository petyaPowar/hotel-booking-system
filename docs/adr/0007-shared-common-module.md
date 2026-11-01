# ADR-0007. Общий модуль common

## Контекст

`hotel-service` и `booking-service` (оба на Spring MVC) должны одинаково проверять JWT, отображать роли, формировать
ошибки и страницы. Копирование кода ведёт к расхождению форматов.

## Решение

Библиотека `common` (jar без `main`) подключается зависимостью и регистрирует `ResourceServerAutoConfiguration` через
`META-INF/spring/...AutoConfiguration.imports`. Содержимое:

- `JwtDecoder` HS256 с проверкой срока, обязательного `exp` и issuer, `JwtAuthenticationConverter` (`roles` → `ROLE_*`),
  `@EnableMethodSecurity`, свойства `app.jwt.*` с валидацией;
- JSON-ответы `401`/`403` (`RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`);
- `GlobalExceptionHandler` и `ErrorResponse` с `traceId` из MDC (включая неизвестное поле сортировки → 400 и исключения
  Spring, реализующие `org.springframework.web.ErrorResponse`, со своим статусом);
- иерархия `ApiException` (`NotFoundException`, `ConflictException`);
- `PageResponse` для пагинации;
- валидация: `DateRange`, `@ValidDateRange` (конец строго позже начала; ошибка привязывается к полю `field`, по умолчанию
  `endDate`), `@MaxUtf8Bytes`;
- константы ролей и claims (`Roles`), выражения `@PreAuthorize` (`Authorities`);
- ключ MDC `bookingId` (`logging.BookingMdc`).

`SecurityFilterChain` каждый сервис объявляет сам (свои публичные пути). `api-gateway` построен на WebFlux и `common`
не использует: у него своя реактивная настройка JWT, `ErrorResponse` той же формы и `GatewayErrorWebExceptionHandler`.

## Последствия

- Единый формат ошибок, правила безопасности и проверка периодов без дублирования кода.
- Изменение `common` затрагивает оба сервиса; перед `mvn -pl <service> spring-boot:run` модуль нужно установить
  (`mvn install`).
- Сервисы связаны только инфраструктурной библиотекой: DTO межсервисного API объявлены в каждом сервисе отдельно.
