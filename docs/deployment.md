# Развёртывание на публичном сервере

Production-контур предназначен для одной Linux VM с публичным IP и доменным именем. Caddy принимает HTTP/HTTPS-трафик, автоматически получает TLS-сертификат и направляет запросы к frontend или backend. PostgreSQL, backend и frontend не публикуют собственные порты на хосте.

## 1. Требования

- Ubuntu 24.04 LTS или 26.04 LTS, архитектура `amd64` или `arm64`;
- минимум 2 ГБ RAM и 20 ГБ диска, рекомендуется 4 ГБ RAM для сборки контейнеров;
- публичный IPv4-адрес;
- домен или поддомен с возможностью изменить DNS;
- SSH-ключ;
- открытые входящие TCP-порты `80` и `443`;
- TCP-порт `22`, ограниченный вашим IP-адресом.

Порты `5432`, `8080` и `3000` открывать во внешнем firewall не требуется. В production Compose они не публикуются на хосте.

## 2. Создание виртуальной машины

### Microsoft Azure

1. Активируйте Azure for Students и создайте Linux VM по [официальному руководству Microsoft](https://learn.microsoft.com/en-us/azure/azure-linux/quick-create-vm-portal).
2. Выберите Ubuntu LTS, вход по SSH-ключу и размер VM не меньше 2 ГБ RAM.
3. Разрешите входящий TCP-трафик на порты `80` и `443` из Интернета.
4. Ограничьте порт `22` вашим внешним IP-адресом.
5. Скопируйте публичный IP со страницы созданной VM.

### DigitalOcean

1. Создайте Ubuntu Droplet и добавьте SSH-ключ. DigitalOcean рекомендует использовать SSH-ключи вместо пароля: [настройка ключей](https://docs.digitalocean.com/products/droplets/how-to/add-ssh-keys/).
2. Выберите конфигурацию не меньше 2 ГБ RAM.
3. Создайте Cloud Firewall и разрешите `HTTP`, `HTTPS`, а также `SSH` только с вашего IP: [настройка firewall](https://docs.digitalocean.com/products/networking/firewalls/how-to/configure-rules/).
4. Скопируйте публичный IP Droplet.

## 3. Настройка DNS

Создайте у DNS-провайдера запись `A` для выбранного домена:

```text
portal.example.com -> PUBLIC_SERVER_IP
```

Создавайте запись `AAAA` только при настроенном публичном IPv6. Перед запуском Caddy убедитесь, что домен разрешается в адрес сервера:

```bash
nslookup portal.example.com
```

Caddy сможет получить публичный сертификат только после обновления DNS и открытия портов `80/443`.

## 4. Установка Docker

Подключитесь к серверу по SSH и установите Docker Engine из официального `apt`-репозитория. Актуальная процедура опубликована в [документации Docker](https://docs.docker.com/engine/install/ubuntu/).

```bash
sudo apt update
sudo apt install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
```

Завершите SSH-сессию, подключитесь повторно и проверьте установку:

```bash
docker version
docker compose version
```

## 5. Получение проекта и настройка секретов

```bash
git clone https://github.com/OWNER/REPOSITORY.git webcare-portal
cd webcare-portal
cp .env.prod.example .env.prod
chmod 600 .env.prod
nano .env.prod
```

Замените `OWNER/REPOSITORY` адресом репозитория. В `.env.prod` обязательно задайте:

- `APP_DOMAIN` без `https://`, например `portal.example.com`;
- `ACME_EMAIL` для уведомлений центра сертификации;
- уникальный `POSTGRES_PASSWORD`;
- отдельные длинные пароли для администратора, специалиста и наблюдателя.

Безопасные значения можно получить командой `openssl rand -hex 32`. Не добавляйте `.env.prod` в Git и не публикуйте административные данные в README.

Стартовые учётные данные применяются только при создании пользователя. Поэтому правильные значения необходимо задать до первого production-запуска; последующее изменение `.env.prod` не заменит пароль уже созданной записи.

## 6. Первый запуск

Сначала проверьте заполнение обязательных переменных, затем запустите окружение:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml config --quiet
docker compose --env-file .env.prod -f compose.prod.yaml up --build -d --wait
```

Проверьте контейнеры и публичные endpoints:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml ps
curl -fsS https://portal.example.com/api/health
curl -fsS https://portal.example.com/actuator/health
```

Все четыре контейнера должны иметь состояние `healthy`. Если сертификат не выпускается, проверьте DNS, firewall и журнал Caddy:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml logs --tail 200 caddy
```

## 7. Обновление

Перед обновлением сохраните резервную копию, затем получите изменения и пересоберите сервисы:

```bash
mkdir -p backups
docker compose --env-file .env.prod -f compose.prod.yaml exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > "backups/webcare-$(date +%F-%H%M).sql"
git pull --ff-only
docker compose --env-file .env.prod -f compose.prod.yaml up --build -d --wait
```

Именованные тома PostgreSQL и Caddy сохраняются при обновлении контейнеров. Не используйте `down --volumes` на сервере: эта команда удаляет базу и сохранённые TLS-данные.

## 8. Остановка и диагностика

```bash
docker compose --env-file .env.prod -f compose.prod.yaml down
docker compose --env-file .env.prod -f compose.prod.yaml logs --tail 200 backend
docker compose --env-file .env.prod -f compose.prod.yaml logs --tail 200 frontend
```

Обычная команда `down` не удаляет именованные тома. Для постоянного публичного демо оставьте `AUDIT_ALLOW_PRIVATE_ADDRESSES=false`, не публикуйте порты внутренних сервисов и передавайте посетителям только HTTPS-адрес портала.
