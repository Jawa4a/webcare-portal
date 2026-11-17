# WebCare Portal

A web application for technical and SEO monitoring of websites. The implemented MVP includes authentication, role-based access, a website registry, background crawling with URL safety checks, technical audits, result comparison, maintenance tasks, and a reproducible test environment.

## Tech stack

- **Backend:** Java, Spring Boot, REST API
- **Frontend:** React, TypeScript
- **Database:** PostgreSQL
- **Environment:** Docker Compose
- **Public deployment:** Caddy with automatic HTTPS

## Quick start

Requires Docker Desktop with Docker Compose support.

```bash
docker compose up --build
```

Once the environment is running:

- Web interface: <http://localhost:3000>
- Application health API: <http://localhost:3000/api/health>
- Technical health check: <http://localhost:3000/actuator/health>
- PostgreSQL: `localhost:5432`

To customise the configuration, copy `.env.example` to `.env` and update the required values. The `.env` file is excluded from Git.

### Demo accounts

| Role | Email | Password |
| --- | --- | --- |
| Administrator | `admin@webcare.local` | `WebcareAdmin!2026` |
| Specialist | `specialist@webcare.local` | `WebcareSpecialist!2026` |
| Viewer | `viewer@webcare.local` | `WebcareViewer!2026` |

The demo role buttons use these same credentials. Demo mode is enabled in Docker Compose: on each startup, the passwords of the three initial users are synchronised with the values above, including when the database already exists. These credentials are intentionally public and must not be used in a production system.

### Stop the environment

```bash
docker compose down
```

PostgreSQL data is retained in a named Docker volume. To delete the data as well, explicitly run `docker compose down --volumes`.

## Public deployment

A separate production configuration is provided for a Linux VM, using Caddy with automatic HTTPS. Only ports `80/443` are exposed; PostgreSQL, the backend, and the frontend remain on internal Docker networks.

Use `compose.prod.yaml`. Create `.env.prod` from `.env.prod.example` and set the domain, ACME email address, and a secret PostgreSQL password. Then follow the [Azure or DigitalOcean deployment guide](docs/deployment.md).

## Documentation

The supporting documentation is currently in Russian.

- [Requirements and user scenarios](docs/requirements.md)
- [Application architecture](docs/architecture.md)
- [Database model](docs/database.md)
- [API contract](docs/api.md)
- [Stage 2 implementation](docs/stage-2.md)
- [Stage 3 implementation](docs/stage-3.md)
- [Stage 4 implementation](docs/stage-4.md)
- [Stage 5 implementation](docs/stage-5.md)
- [Installation and updates](docs/installation.md)
- [Public deployment](docs/deployment.md)
- [User guide](docs/user-guide.md)
- [Automated tests](docs/testing.md)
- [Operations and troubleshooting](docs/operations.md)

## Repository structure

```text
backend/           Spring Boot REST API, URL policy, audits, comparisons, and tasks
frontend/          React + TypeScript, websites, results, comparisons, and work queue
infra/             Reverse proxy configuration
docs/              Requirements and design decisions
test-sites/        Controlled website audit scenarios
compose.yaml       Combined frontend, backend, and PostgreSQL environment
compose.prod.yaml  Production environment with Caddy and HTTPS
```

## MVP status and testing

The acceptance criteria in the overall project plan have been implemented. Unit tests cover audit rules, result comparison, and URL policy. The integration profile starts a separate PostgreSQL instance and a controlled test website, then runs an end-to-end REST scenario.

Scheduled audits, notifications, and report exports are outside the scope of the MVP.

To run the full test environment:

```bash
docker compose -f compose.test.yaml up --abort-on-container-exit --exit-code-from backend-tests
docker compose -f compose.test.yaml down --volumes --remove-orphans
```