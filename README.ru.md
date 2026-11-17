# WebCare Portal

Портал для безопасного технического и SEO-мониторинга сайтов. MVP завершён: авторизация, ролевой доступ, реестр сайтов, защищённый фоновый обход, технические проверки, сравнение результатов, задачи обслуживания и воспроизводимый тестовый контур.

## Быстрый запуск

Требуется Docker Desktop с поддержкой Docker Compose.

```bash
docker compose up --build
```

После запуска доступны:

- интерфейс: <http://localhost:3000>;
- API состояния: <http://localhost:3000/api/health>;
- технический health-check: <http://localhost:3000/actuator/health>;
- PostgreSQL: `localhost:5432`.

Для собственных параметров скопируйте `.env.example` в `.env` и замените необходимые значения. Файл `.env` не попадает в Git.

Публичные демонстрационные учётные записи:

| Роль | Email | Пароль |
|---|---|---|
| Администратор | `admin@webcare.local` | `WebcareAdmin!2026` |
| Специалист | `specialist@webcare.local` | `WebcareSpecialist!2026` |
| Наблюдатель | `viewer@webcare.local` | `WebcareViewer!2026` |

Кнопки «Демо-роли» используют эти же данные. В Docker Compose включён demo-режим: при каждом запуске пароли трёх стартовых пользователей синхронизируются с указанными значениями, в том числе для уже существующей базы. Эти данные намеренно публичны и не подходят для реальной системы.

Остановка окружения:

```bash
docker compose down
```

Данные PostgreSQL сохраняются в именованном Docker-томе. Для удаления данных явно выполните `docker compose down --volumes`.

## Публичное развёртывание

Для Linux VM подготовлен отдельный production-контур с Caddy и автоматическим HTTPS. Он публикует только порты `80/443`; PostgreSQL, backend и frontend остаются во внутренних Docker-сетях. Используйте `compose.prod.yaml`, заполните домен, email ACME и секретный пароль PostgreSQL в `.env.prod` по шаблону `.env.prod.example`, затем следуйте [руководству по развёртыванию на Azure или DigitalOcean](docs/deployment.md).

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
- [Публичное развёртывание](docs/deployment.md)
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
compose.prod.yaml  production-запуск с Caddy и HTTPS
```

## Статус MVP

Реализованы критерии из общего плана. Unit-тесты проверяют правила аудита, сравнение и URL-политику; integration-профиль запускает отдельную PostgreSQL и контролируемый сайт, затем выполняет полный REST-сценарий. Автоматические аудиты по расписанию, уведомления и экспорт отчётов остаются за границей MVP.

Полная проверка тестового контура:

```bash
docker compose -f compose.test.yaml up --abort-on-container-exit --exit-code-from backend-tests
docker compose -f compose.test.yaml down --volumes --remove-orphans
```
