# eureka-server

Service registry на Spring Cloud Netflix Eureka. Порт 8761. Запускается первым.

- `EurekaServerApplication` с `@EnableEurekaServer`.
- Сам в себе не регистрируется (`register-with-eureka: false`, `fetch-registry: false`).
- Клиенты (`api-gateway`, `hotel-service`, `booking-service`) подключаются по `EUREKA_URL`
  (по умолчанию `http://localhost:8761/eureka`) и регистрируются по IP (`prefer-ip-address: true`).
- Дашборд: http://localhost:8761, health: http://localhost:8761/actuator/health. Аутентификации нет
  ([ADR-0004](../docs/adr/0004-jwt-hs256-resource-servers.md)).
- Логи: консоль и ECS JSON в `logs/eureka-server.log`.

## Запуск

```bash
mvn -pl eureka-server spring-boot:run
# или
java -jar eureka-server/target/eureka-server-1.0.0.jar
```

## Тесты

```bash
mvn -pl eureka-server verify
```

Профиль `test` (`src/test/resources/application-test.yml`) выключает файловый лог. 2 теста:
`EurekaServerApplicationTest` — контекст поднимается, `/actuator/health` возвращает UP, реестр `/eureka/apps` отдаётся.
