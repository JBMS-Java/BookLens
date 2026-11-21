# BookLens

BookLens is a multilingual platform for importing, translating, comparing and analyzing books.

## MVP stack

- **Backend:** Java 21 + Spring Boot
- **Frontend:** React + TypeScript
- **Database:** PostgreSQL
- **Cache and job coordination:** Redis
- **Object storage:** local filesystem in development; S3-compatible storage in production
- **Containerization:** Docker Compose

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the system context, modules, data model, API conventions and infrastructure.

## Repository layout

```

## Local setup

Prerequisites:

- Java 21
- Node.js 20 or newer
- Docker Desktop, if running PostgreSQL and Redis locally

Start the infrastructure:

```powershell
docker compose up -d
```

For the full development profile with PostgreSQL and Flyway:

```powershell
cd backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Start the backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Start the frontend:

```powershell
cd frontend
npm install
npm run dev
```text

## Supported MVP capabilities

- Library of works and multilingual editions.
- TXT, PDF, DOCX and EPUB import.
- Chapter and section detection.
- Extracted-content preview.
- Deterministic statistical analysis.
- Heuristic literary indicators.
- Edition comparison.
- Glossary-based translation and TXT/DOCX export.
- Work and edition deletion.

PDFs containing selectable text are supported. Scanned PDFs are identified as requiring OCR, but image OCR requires Tesseract or a hosted OCR provider. The legacy DOC format is intentionally outside the MVP adapter and is best handled through conversion to DOCX.

Real AI literary analysis and neural translation can be added behind provider interfaces when credentials and a provider endpoint are configured.text
booklens/
├── backend/       # Spring Boot application
├── frontend/      # React application
├── docs/          # Architecture and decisions
└── docker/        # Container infrastructure
```
