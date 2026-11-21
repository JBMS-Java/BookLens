# ADR 001: Initial MVP technology stack

- **Status:** Accepted
- **Date:** 2026-11-21

## Decision

Use Java 21 with Spring Boot for the backend and React with TypeScript for the frontend.

Use PostgreSQL as the primary database, Redis for caching and job coordination, and S3-compatible object storage for uploaded and generated documents.

## Rationale

- Java and Spring Boot provide strong typing, mature ecosystems and clear modular boundaries.
- React and TypeScript provide a mature component model for document previews, dashboards and comparisons.
- PostgreSQL supports relational ownership data and JSONB analysis payloads.
- Redis supports progress coordination and future queue integration.
- S3-compatible storage separates large files from application databases.

## Consequences

- The repository requires separate Java and Node.js toolchains.
- Document and analysis work must be isolated behind asynchronous job boundaries.
- API contracts must be explicit and stable.
- Provider and storage integrations need interface boundaries.
