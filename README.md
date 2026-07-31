# WebCare Portal

Портал для безопасного технического и SEO-мониторинга сайтов. MVP завершён: авторизация, ролевой доступ, реестр сайтов, защищённый фоновый обход, технические проверки, сравнение результатов, задачи обслуживания и воспроизводимый тестовый контур.

## Быстрый запуск

Требуется Docker Desktop с поддержкой Docker Compose.

```bash
docker compose up --build
```

После запуска доступны:

- интерфейс: <http://localhost:3000>;
- API состояния: <http://localhost:8080/api/health>;
- технический health-check: <http://localhost:8080/actuator/health>;
- PostgreSQL: `localhost:5432`.

Для собственных параметров скопируйте `.env.example` в `.env` и замените необходимые значения. Файл `.env` не попадает в Git.

Локальные демонстрационные учётные записи при запуске без `.env`:

| Роль | Email | Пароль |
|---|---|---|
| Администратор | `admin@webcare.local` | `WebcareAdmin!2026` |
| Специалист | `specialist@webcare.local` | `WebcareSpecialist!2026` |
| Наблюдатель | `viewer@webcare.local` | `WebcareViewer!2026` |

Пароли предназначены только для локальной демонстрации. Перед размещением системы задайте собственные значения `BOOTSTRAP_*_PASSWORD`, включите HTTPS и установите `SESSION_COOKIE_SECURE=true`.

Остановка окружения:

```bash
docker compose down
```

Данные PostgreSQL сохраняются в именованном Docker-томе. Для удаления данных явно выполните `docker compose down --volumes`.

## Документация

- [Требования и пользовательские сценарии](docs/requirements.md)
- [Архитектура приложения](docs/architecture.md)
- [Модель базы данных](docs/database.md)
- [Контракт API](docs/api.md)
- [Реализация второго этапа](docs/stage-2.md)
- [Реализация третьего этапа](docs/stage-3.md)
- [Реализация четвёртого этапа](docs/stage-4.md)
- [Реализация пятого этапа](docs/stage-5.md)
- [Установка и обновление](docs/installation.md)
- [Руководство пользователя](docs/user-guide.md)
- [Автоматические тесты](docs/testing.md)
- [Эксплуатация и диагностика](docs/operations.md)

## Структура

```text
backend/       Spring Boot REST API, URL-политика, аудиты, сравнение и задачи
frontend/      React + TypeScript, сайты, результаты, сравнение и рабочая очередь
infra/         конфигурация reverse proxy
docs/          требования и проектные решения
test-sites/    место для контролируемых сценариев проверки аудита
compose.yaml   единый запуск frontend, backend и PostgreSQL
```

## Статус MVP

Реализованы критерии из общего плана. Unit-тесты проверяют правила аудита, сравнение и URL-политику; integration-профиль запускает отдельную PostgreSQL и контролируемый сайт, затем выполняет полный REST-сценарий. Автоматические аудиты по расписанию, уведомления и экспорт отчётов остаются за границей MVP.

Полная проверка тестового контура:

```bash
docker compose -f compose.test.yaml up --abort-on-container-exit --exit-code-from backend-tests
docker compose -f compose.test.yaml down --volumes --remove-orphans
```
