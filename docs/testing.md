# Автоматические тесты

## Уровни проверки

### Unit

Проверяются URL-политика, правила аудита и классификация изменений:

```bash
cd backend
mvn test
```

Обычная Docker-сборка backend также выполняет unit-тесты.

### Frontend

```bash
cd frontend
npm ci
npm run build
```

Команда включает проверку TypeScript и production-сборку Vite.

### PostgreSQL integration

```bash
docker compose -f compose.test.yaml up --abort-on-container-exit --exit-code-from backend-tests
docker compose -f compose.test.yaml down --volumes --remove-orphans
```

Контур создаёт отдельную PostgreSQL 17 и Nginx с контролируемыми страницами. Тест проверяет:

- применение всех Flyway-миграций к пустой базе;
- неверный и успешный вход;
- добавление сайта;
- два реальных фоновых аудита;
- правила HTTP, redirect, broken link, SEO и accessibility;
- фильтрацию и сравнение;
- создание, назначение и изменение задачи;
- защиту от дубликата;
- запрет изменения для `VIEWER`;
- агрегаты обзорной панели.

Тестовые данные и том удаляются второй командой. Основной `postgres_data` не используется.

## Контролируемые страницы

Содержимое находится в `test-sites/site`, конфигурация редиректа — в `test-sites/default.conf`. Режим разрешения private-адресов включён только в `compose.test.yaml`; переносить его в обычный запуск запрещено.

## CI

`.github/workflows/ci.yml` содержит независимые проверки unit/frontend, PostgreSQL integration и production Docker images. Любая ошибка завершает соответствующую job ненулевым кодом.
