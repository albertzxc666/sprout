# Backend Skeleton (подпроект 1) — План реализации

> **Для исполнителя:** REQUIRED SUB-SKILL: используй superpowers:subagent-driven-development (рекомендовано) или superpowers:executing-plans. Шаги используют `- [ ]` для трекинга.

**Goal:** Поднять отдельный репозиторий `transcard-server` со скелетом Express + TypeScript + Prisma + PostgreSQL + Caddy в Docker Compose, рабочим `/healthz` и инструкцией по деплою. Без бизнес-логики (auth/sync — следующие подпроекты).

**Architecture:** Один процесс Node.js (Express) общается с PostgreSQL через Prisma. Caddy впереди — TLS + reverse proxy. Всё запускается одной командой `docker compose up`. Локально TLS не нужен — Caddy слушает HTTP на `:80`. На VPS — тот же compose плюс реальный домен в Caddyfile через переменную `{$DOMAIN}`.

**Tech Stack:** Node 20-alpine, TypeScript 5.x, Express 4.x, Prisma 6.x + PostgreSQL 16-alpine, helmet, dotenv, tsx (dev) / tsc (prod), Caddy 2-alpine.

**Scope-notes:**
- Тесты не пишем (см. `2026-05-12-account-sync-design.md` → раздел «Тестирование»). Верификация — ручная: curl + docker logs.
- Бэкенд живёт в отдельном репо `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server` — НЕ внутри TransCard.
- Деплой не выполняем — README содержит подробную инструкцию, пользователь выкатит сам, когда выберет VPS/домен.
- Prisma модели в этом подпроекте не вводятся — только базовая конфигурация Prisma Client (модели придут с подпроектом 2 backend-auth).

---

## File Structure

После выполнения плана:

```
transcard-server/
├── .git/                       (Task 1)
├── .gitignore                  (Task 1)
├── .env.example                (Task 1 + расширение в Task 6)
├── .dockerignore               (Task 5)
├── package.json                (Task 2)
├── package-lock.json           (auto)
├── tsconfig.json               (Task 2)
├── prisma/
│   └── schema.prisma           (Task 4)
├── src/
│   ├── index.ts                (Task 3)
│   └── lib/
│       └── prisma.ts           (Task 4)
├── Dockerfile                  (Task 5)
├── docker-compose.yml          (Task 6)
├── Caddyfile                   (Task 6)
└── README.md                   (Task 8)
```

Каждый файл — одна ответственность: `src/index.ts` бутстрап HTTP-сервера, `src/lib/prisma.ts` — singleton Prisma Client, `Dockerfile` — multi-stage сборка для прод-образа, `docker-compose.yml` — оркестрация трёх контейнеров.

---

## Task 1: Инициализация репозитория

**Files:**
- Create: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\.gitignore`
- Create: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\.env.example`

- [ ] **Шаг 1: Создать директорию и инициализировать git**

```powershell
New-Item -ItemType Directory -Path "c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server"
git -C "c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server" init -b main
```

Ожидаемый вывод: `Initialized empty Git repository in c:/Users/alber/Desktop/WEB-PROJECTS/transcard-server/.git/`.

- [ ] **Шаг 2: Создать .gitignore**

Содержимое `.gitignore`:

```gitignore
# Node
node_modules/
dist/
*.log
npm-debug.log*

# Env
.env
.env.local

# Editor
.vscode/
.idea/

# OS
.DS_Store
Thumbs.db

# Prisma
prisma/dev.db
prisma/dev.db-journal
```

- [ ] **Шаг 3: Создать .env.example (минимум для подпроекта 1)**

Содержимое `.env.example`:

```env
# Server
NODE_ENV=development
PORT=3000
```

(POSTGRES_*, JWT_SECRET, DOMAIN добавим в Task 6, чтобы держать .env.example синхронизированным с задействованными переменными на каждом шаге.)

- [ ] **Шаг 4: Первый коммит**

```powershell
git -C "c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server" add .gitignore .env.example
git -C "c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server" commit -m "chore: initialize repository"
```

Ожидаемый вывод: `[main (root-commit) ...] chore: initialize repository`, 2 files changed.

---

## Task 2: package.json + TypeScript

**Files:**
- Create: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\package.json`
- Create: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\tsconfig.json`

Дальше все команды выполняются из директории `transcard-server`. На Windows можно держать одно окно PowerShell внутри неё:

```powershell
Set-Location "c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server"
```

- [ ] **Шаг 1: Инициализировать npm**

```powershell
npm init -y
```

Ожидаемый вывод: `Wrote to ...\transcard-server\package.json`, файл создан со значениями по умолчанию.

- [ ] **Шаг 2: Установить TypeScript и tsx**

```powershell
npm install -D typescript @types/node tsx
```

Ожидаемый вывод: `added N packages, audited N+1 packages`. Появляются `node_modules/` и `package-lock.json`.

- [ ] **Шаг 3: Создать tsconfig.json**

Содержимое `tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "outDir": "dist",
    "rootDir": "src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "resolveJsonModule": true,
    "declaration": false,
    "sourceMap": false
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist"]
}
```

- [ ] **Шаг 4: Прописать скрипты и type=module в package.json**

Открыть `package.json` и заменить его на:

```json
{
  "name": "transcard-server",
  "version": "0.1.0",
  "description": "TransCard backup/sync backend",
  "main": "dist/index.js",
  "type": "module",
  "scripts": {
    "dev": "tsx watch src/index.ts",
    "build": "tsc",
    "start": "node dist/index.js"
  },
  "license": "UNLICENSED",
  "private": true
}
```

(Версии в `dependencies`/`devDependencies` уже подставлены `npm install` — не трогаем.)

- [ ] **Шаг 5: Коммит**

```powershell
git add package.json package-lock.json tsconfig.json
git commit -m "chore: add TypeScript toolchain (tsx + tsc)"
```

Ожидаемый вывод: `3 files changed`.

---

## Task 3: Express-приложение и /healthz

**Files:**
- Create: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\src\index.ts`

- [ ] **Шаг 1: Установить express, helmet, dotenv**

```powershell
npm install express helmet dotenv
npm install -D @types/express
```

Ожидаемый вывод: всего 4 пакета добавлено в `package.json`.

- [ ] **Шаг 2: Создать src\index.ts**

Содержимое `src/index.ts`:

```typescript
import express, { Request, Response } from 'express';
import helmet from 'helmet';
import 'dotenv/config';

const app = express();

app.use(helmet());
app.use(express.json({ limit: '6mb' }));

app.get('/healthz', (_req: Request, res: Response) => {
  res.json({ status: 'ok', ts: Date.now() });
});

const port = Number(process.env.PORT ?? 3000);
app.listen(port, () => {
  console.log(`transcard-server listening on :${port}`);
});
```

- [ ] **Шаг 3: Запустить локально и проверить**

В одном окне PowerShell:

```powershell
npm run dev
```

Ожидаемый вывод: `transcard-server listening on :3000`.

В другом окне:

```powershell
curl http://localhost:3000/healthz
```

Ожидаемый вывод (JSON): `{"status":"ok","ts":1715520000000}` (timestamp будет свой). Код 200.

Останови dev-сервер (`Ctrl+C`).

- [ ] **Шаг 4: Проверить, что build проходит**

```powershell
npm run build
```

Ожидаемый вывод: создаётся `dist/index.js`, ошибок нет.

- [ ] **Шаг 5: Коммит**

```powershell
git add package.json package-lock.json src/index.ts
git commit -m "feat: add Express app with /healthz endpoint"
```

Ожидаемый вывод: `3 files changed`.

---

## Task 4: Prisma Client (без моделей)

**Files:**
- Create: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\prisma\schema.prisma`
- Create: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\src\lib\prisma.ts`

- [ ] **Шаг 1: Установить prisma и client**

```powershell
npm install -D prisma
npm install @prisma/client
```

- [ ] **Шаг 2: Инициализировать prisma**

```powershell
npx prisma init --datasource-provider postgresql
```

Ожидаемый вывод: создаются `prisma/schema.prisma` и `.env` с `DATABASE_URL=...`. Если `.env` уже существует — prisma не перезапишет, добавит вручную позже.

- [ ] **Шаг 3: Зафиксировать содержимое prisma/schema.prisma**

Перезаписать `prisma/schema.prisma` ровно так (модели добавит подпроект 2):

```prisma
// Backend-skeleton: только конфигурация. Модели придут с подпроектом backend-auth.
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}
```

- [ ] **Шаг 4: Создать singleton-обёртку src\lib\prisma.ts**

```typescript
import { PrismaClient } from '@prisma/client';

declare global {
  var __prisma: PrismaClient | undefined;
}

export const prisma =
  global.__prisma ?? new PrismaClient();

if (process.env.NODE_ENV !== 'production') {
  global.__prisma = prisma;
}
```

(Singleton нужен, чтобы при hot-reload через `tsx watch` не плодились коннекшены.)

- [ ] **Шаг 5: Сгенерировать prisma client**

```powershell
npx prisma generate
```

Ожидаемый вывод: `✔ Generated Prisma Client (vX.Y.Z) to .\node_modules\@prisma\client`.

- [ ] **Шаг 6: Убедиться, что build всё ещё проходит**

```powershell
npm run build
```

Ожидаемый вывод: ошибок нет, `dist/index.js` и `dist/lib/prisma.js` собраны.

- [ ] **Шаг 7: Коммит**

```powershell
git add package.json package-lock.json prisma/schema.prisma src/lib/prisma.ts
git commit -m "feat: configure Prisma client (no models yet)"
```

Ожидаемый вывод: `4 files changed`.

---

## Task 5: Dockerfile (multi-stage)

**Files:**
- Create: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\Dockerfile`
- Create: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\.dockerignore`

- [ ] **Шаг 1: Создать .dockerignore**

Содержимое `.dockerignore`:

```dockerignore
node_modules
dist
.git
.env
.env.local
*.log
README.md
.vscode
.idea
```

- [ ] **Шаг 2: Создать Dockerfile**

Содержимое `Dockerfile`:

```dockerfile
# ---------- builder ----------
FROM node:20-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json ./
COPY prisma ./prisma
COPY src ./src

RUN npx prisma generate
RUN npm run build

# ---------- runner ----------
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# Prisma generated client + schema (нужен для миграций потом)
COPY --from=builder /app/node_modules/.prisma ./node_modules/.prisma
COPY --from=builder /app/node_modules/@prisma ./node_modules/@prisma
COPY --from=builder /app/prisma ./prisma
COPY --from=builder /app/dist ./dist

EXPOSE 3000
CMD ["node", "dist/index.js"]
```

- [ ] **Шаг 3: Локально собрать образ для проверки**

```powershell
docker build -t transcard-server:dev .
```

Ожидаемый вывод: `Successfully tagged transcard-server:dev`. Время сборки ~1-3 мин на первый раз.

Если ошибка `npm ci` про lockfile — выполнить `npm install` локально для свежего lockfile, затем `docker build` снова.

- [ ] **Шаг 4: Локально запустить контейнер для smoke-проверки**

```powershell
docker run --rm -d -p 3000:3000 --name transcard-server-test transcard-server:dev
```

Подожди 2 секунды, проверь:

```powershell
curl http://localhost:3000/healthz
```

Ожидаемый вывод: `{"status":"ok","ts":...}`.

Останови контейнер:

```powershell
docker stop transcard-server-test
```

(Контейнер запускается без БД — это ок, /healthz не трогает Prisma.)

- [ ] **Шаг 5: Коммит**

```powershell
git add Dockerfile .dockerignore
git commit -m "build: add multi-stage Dockerfile"
```

Ожидаемый вывод: `2 files changed`.

---

## Task 6: docker-compose.yml + Caddy + .env.example

**Files:**
- Create: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\docker-compose.yml`
- Create: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\Caddyfile`
- Modify: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\.env.example` (расширить)

- [ ] **Шаг 1: Создать docker-compose.yml**

Содержимое `docker-compose.yml`:

```yaml
services:
  app:
    build: .
    environment:
      NODE_ENV: production
      PORT: 3000
      DATABASE_URL: postgres://transcard:${DB_PASSWORD}@db:5432/transcard
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: transcard
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: transcard
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U transcard"]
      interval: 5s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  caddy:
    image: caddy:2-alpine
    ports:
      - "80:80"
      - "443:443"
    environment:
      DOMAIN: ${DOMAIN:-localhost}
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    depends_on:
      - app
    restart: unless-stopped

volumes:
  pgdata:
  caddy_data:
  caddy_config:
```

- [ ] **Шаг 2: Создать Caddyfile**

Содержимое `Caddyfile`:

```caddyfile
{$DOMAIN:localhost} {
    reverse_proxy app:3000
}
```

Если `DOMAIN=localhost` (локально) — Caddy отдаст self-signed TLS, что для отладки достаточно. На VPS пользователь подставит реальный домен и Caddy получит сертификат Let's Encrypt автоматически.

- [ ] **Шаг 3: Расширить .env.example**

Перезаписать `.env.example`:

```env
# Server
NODE_ENV=production
PORT=3000

# PostgreSQL (используется docker-compose и Prisma)
DB_PASSWORD=changeme_strong_password

# JWT (зарезервировано для подпроекта 2 backend-auth)
JWT_SECRET=changeme_at_least_32_random_bytes_base64

# Caddy
# Для локальной отладки оставь localhost. Для VPS — твой домен, например sync.example.ru.
DOMAIN=localhost

# Prisma CLI (только для локальной разработки вне docker)
DATABASE_URL=postgres://transcard:changeme_strong_password@localhost:5432/transcard
```

- [ ] **Шаг 4: Коммит**

```powershell
git add docker-compose.yml Caddyfile .env.example
git commit -m "build: add docker-compose with Postgres and Caddy"
```

Ожидаемый вывод: `3 files changed`.

---

## Task 7: Локальный smoke-test через docker compose

**Files:** ничего не создаём, только запускаем стек локально и проверяем.

- [ ] **Шаг 1: Создать локальный .env из .env.example**

```powershell
Copy-Item .env.example .env
```

Открыть `.env` и заменить `changeme_*` значения на любые рабочие (для локалки достаточно `local_dev_password_123` и любых 32+ символов в `JWT_SECRET`). `DOMAIN=localhost` оставить.

ВАЖНО: `.env` уже в `.gitignore` — он не попадёт в репо.

- [ ] **Шаг 2: Поднять стек**

```powershell
docker compose up -d --build
```

Ожидаемый вывод: три контейнера в статусе `Started` / `Healthy`. Время первой сборки ~2-5 минут.

Если ошибка про порты 80/443 заняты — останови IIS / Skype / другую службу, либо временно поменяй `"80:80"` на `"8080:80"` и `"443:443"` на `"8443:443"` в `docker-compose.yml` (только для локального теста, не коммитить).

- [ ] **Шаг 3: Проверить /healthz через Caddy**

```powershell
curl -k https://localhost/healthz
```

(`-k` нужен, потому что Caddy для localhost выдаёт self-signed сертификат.)

Ожидаемый вывод: `{"status":"ok","ts":...}`.

Если HTTPS не отдаёт — попробуй HTTP:

```powershell
curl http://localhost/healthz
```

Ожидаемый: 308 редирект на HTTPS (Caddy по умолчанию редиректит). Это нормально.

- [ ] **Шаг 4: Проверить логи всех трёх контейнеров**

```powershell
docker compose logs --tail=30 app db caddy
```

Ожидаемые маркеры:
- `app`: `transcard-server listening on :3000`
- `db`: `database system is ready to accept connections`
- `caddy`: `serving initial configuration`

Ошибок (особенно стектрейсов) быть не должно. `db` может писать предупреждения первой инициализации — это ок.

- [ ] **Шаг 5: Остановить стек**

```powershell
docker compose down
```

Ожидаемый вывод: три контейнера остановлены и удалены. Volume `pgdata` остаётся — это намеренно.

- [ ] **Шаг 6 (опционально): Полная очистка**

Если хочешь начисто:

```powershell
docker compose down -v
```

— дополнительно удалит volume `pgdata`.

(Коммита в этой задаче нет — мы ничего не меняли, только проверяли.)

---

## Task 8: README с инструкцией локального запуска и деплоя

**Files:**
- Create: `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server\README.md`

- [ ] **Шаг 1: Создать README.md**

Содержимое `README.md`:

````markdown
# transcard-server

Бэкенд для облачной синхронизации [TransCard](https://github.com/<your>/TransCard).
Бэкап-режим: клиент пушит полные snapshot'ы, сервер хранит последние 10 версий.

**Текущая стадия:** подпроект 1 — skeleton с `/healthz`. Auth и Sync — отдельные подпроекты.

## Стек

- Node 20 + TypeScript 5
- Express 4 (+ helmet)
- Prisma 6 + PostgreSQL 16
- Caddy 2 (TLS, reverse proxy)
- Docker Compose

## Локальный запуск

Требуется: Docker Desktop (Windows/macOS) или Docker Engine + Compose plugin (Linux).

```powershell
# 1. Клонировать
git clone <repo-url> transcard-server
cd transcard-server

# 2. Подготовить env
Copy-Item .env.example .env
# Открой .env и подставь свои значения для DB_PASSWORD и JWT_SECRET.

# 3. Запустить
docker compose up -d --build

# 4. Проверить
curl -k https://localhost/healthz
# → {"status":"ok","ts":...}
```

Остановить:

```powershell
docker compose down
```

Полная очистка (включая БД):

```powershell
docker compose down -v
```

## Разработка вне Docker

Для итеративной разработки удобнее запускать только Postgres в Docker, а app — локально через `tsx`:

```powershell
docker compose up -d db
npm install
npm run dev
# → curl http://localhost:3000/healthz
```

## Структура

```
src/
  index.ts          # bootstrap Express
  lib/prisma.ts     # singleton Prisma Client
prisma/
  schema.prisma     # модели появятся в подпроекте 2 (backend-auth)
Dockerfile          # multi-stage build (builder + runner)
docker-compose.yml  # app + db + caddy
Caddyfile          # reverse proxy + автоматический TLS
```

## Деплой на VPS

### Что нужно от тебя заранее

1. **VPS** (рекомендуется Timeweb Cloud, тариф от ~300 ₽/мес: 1 vCPU / 2 ГБ RAM / 30 ГБ NVMe). Альтернативы: Selectel, REG.RU Cloud, Beget Cloud.
2. **Домен или субдомен**, направленный на IP сервера (A-запись). Например, `sync.example.ru`. Регистрация — REG.RU.
3. **SSH-ключ**, добавленный в личный кабинет провайдера и в `~/.ssh/authorized_keys` на сервере.

### Подготовка сервера (один раз)

Подключись по SSH (root или sudo-юзер):

```bash
ssh root@<server-ip>

# Обновить систему
apt update && apt upgrade -y

# Поставить Docker + Compose plugin
curl -fsSL https://get.docker.com | sh
apt install -y docker-compose-plugin

# Открыть firewall (если используется ufw)
ufw allow 22
ufw allow 80
ufw allow 443
ufw enable
```

### Первый деплой

```bash
# На сервере, в любой удобной директории (например, /opt)
cd /opt
git clone <repo-url> transcard-server
cd transcard-server

# Сконфигурировать env
cp .env.example .env
nano .env
# Заполни:
#   DB_PASSWORD=<сгенерируй: openssl rand -base64 24>
#   JWT_SECRET=<сгенерируй: openssl rand -base64 32>
#   DOMAIN=sync.example.ru

# Поднять
docker compose up -d --build

# Проверить (с локальной машины)
curl https://sync.example.ru/healthz
# → {"status":"ok","ts":...}
```

Первый запрос может занять 10-30 секунд, пока Caddy получает сертификат Let's Encrypt. Если ошибка — проверь `docker compose logs caddy`, чаще всего DNS ещё не пропагировался.

### Обновление (последующие деплои)

```bash
ssh root@<server-ip>
cd /opt/transcard-server
git pull
docker compose up -d --build
```

CI/CD добавим позже — в первой итерации деплой ручной (см. design-doc `2026-05-12-account-sync-design.md` → «Деплой»).

### Бэкапы PostgreSQL

Простейший вариант — systemd-таймер на хосте раз в сутки:

```bash
# /etc/systemd/system/transcard-backup.service
[Unit]
Description=transcard postgres dump

[Service]
Type=oneshot
ExecStart=/bin/bash -c 'docker compose -f /opt/transcard-server/docker-compose.yml exec -T db pg_dump -U transcard transcard | gzip > /var/backups/transcard/transcard-$(date +%%Y%%m%%d).sql.gz'

# /etc/systemd/system/transcard-backup.timer
[Unit]
Description=transcard backup daily

[Timer]
OnCalendar=daily
Persistent=true

[Install]
WantedBy=timers.target
```

```bash
mkdir -p /var/backups/transcard
systemctl enable --now transcard-backup.timer
```

Дальше — копирование архивов в Timeweb Cloud Storage / Selectel Object Storage по желанию (см. design-doc).

## Связанные документы

- [Design-doc общий](../TransCard/docs/superpowers/specs/2026-05-12-account-sync-design.md)
- Следующий подпроект: **backend-auth** (регистрация, логин, JWT + refresh).
````

- [ ] **Шаг 2: Коммит**

```powershell
git add README.md
git commit -m "docs: add README with local setup and deployment guide"
```

Ожидаемый вывод: `1 file changed`.

---

## Task 9: Финальная верификация

- [ ] **Шаг 1: Чистый прогон с нуля**

```powershell
docker compose down -v
Remove-Item -Recurse -Force node_modules, dist -ErrorAction SilentlyContinue
docker compose up -d --build
```

Ожидаемый вывод: всё поднимается с чистого листа без ошибок.

- [ ] **Шаг 2: Smoke-проверка**

```powershell
curl -k https://localhost/healthz
```

Ожидаемый вывод: `{"status":"ok","ts":...}`.

- [ ] **Шаг 3: Проверка истории коммитов**

```powershell
git log --oneline
```

Ожидаемый вывод (порядок снизу вверх):
```
<hash> docs: add README with local setup and deployment guide
<hash> build: add docker-compose with Postgres and Caddy
<hash> build: add multi-stage Dockerfile
<hash> feat: configure Prisma client (no models yet)
<hash> feat: add Express app with /healthz endpoint
<hash> chore: add TypeScript toolchain (tsx + tsc)
<hash> chore: initialize repository
```

7 коммитов, каждый — самодостаточный шаг.

- [ ] **Шаг 4: Остановить стек**

```powershell
docker compose down
```

(Без `-v`, чтобы pgdata осталась к следующему подпроекту.)

---

## Что НЕ делаем в этом подпроекте (вне scope)

- Auth-эндпоинты, JWT-utilities, `requireAuth` middleware — подпроект 2.
- `/sync*` эндпоинты, модели `User`/`RefreshToken`/`Snapshot`, rate limit, zod-валидация — подпроекты 2-3.
- GitHub remote, CI/CD — после стабилизации MVP.
- Деплой на реальный VPS — ты выполнишь его сам по README, когда выберешь хостинг.
- Юнит-тесты — по решению из design-doc.

## Definition of Done

- [ ] Репозиторий `c:\Users\alber\Desktop\WEB-PROJECTS\transcard-server` существует, инициализирован git'ом, 7 коммитов в `main`.
- [ ] `docker compose up -d --build` поднимает стек из трёх контейнеров без ошибок.
- [ ] `curl -k https://localhost/healthz` возвращает 200 с JSON `{"status":"ok","ts":...}`.
- [ ] `npm run build` локально проходит без ошибок TypeScript.
- [ ] `npx prisma generate` локально проходит без ошибок.
- [ ] README содержит локальную инструкцию и подробный раздел про деплой на VPS.
