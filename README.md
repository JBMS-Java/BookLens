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

```text
booklens/
├── backend/       # Spring Boot application
├── frontend/      # React application
├── docs/          # Architecture and decisions
└── docker/        # Container infrastructure
```

## Local setup

Prerequisites:

- Java 21
- Node.js 20 or newer
- Docker Desktop, if running PostgreSQL and Redis locally
- Python and LibreTranslate for local translation

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
```

## Supported MVP capabilities

- Library of works with optional JPEG/PNG covers and multilingual editions.
- TXT, PDF, DOCX and EPUB import.
- Chapter and section detection.
- Extracted-content preview.
- Deterministic statistical analysis.
- Heuristic literary indicators.
- Edition comparison.
- LibreTranslate whole-book translation and TXT/DOCX/PDF export.
- Work and edition deletion.

PDFs containing selectable text are supported. Scanned PDFs are identified as requiring OCR, but image OCR requires Tesseract or a hosted OCR provider. The legacy DOC format is intentionally outside the MVP adapter and is best handled through conversion to DOCX.

### Whole-book local translation

Install LibreTranslate in an isolated environment once, then start it in a separate
PowerShell window:

```powershell
cd backend
py -m venv .venv-libretranslate
.\.venv-libretranslate\Scripts\python.exe -m pip install libretranslate==1.9.6
$env:PYTHONUTF8 = "1"
$env:ARGOS_CHUNK_TYPE = "MINISBD"
.\.venv-libretranslate\Scripts\libretranslate.exe --host 127.0.0.1 --load-only pt,en,es
```

On later starts, set the two environment variables and run the last command. The first start downloads language
models and may take some time. Keep LibreTranslate running alongside the backend
and frontend. Its local endpoint defaults to `http://127.0.0.1:5000`; set
`LIBRETRANSLATE_URL` before starting the backend to use another endpoint.

The complete extracted text is sent in ordered chunks to the configured service.
When using the local endpoint, the text stays on your computer. Long books can take
a long time; a translation is saved only after all chunks succeed. Local translation
is free, but its quality may vary and it requires disk space for language models.
By default, the backend stores books and completed translations in the persistent
H2 database under `backend/data/`. Keep this directory to retain the library across
restarts. Book covers are also stored in the database; JPEG and PNG images up to
5 MB and 4096 × 4096 pixels are supported.
