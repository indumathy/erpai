# erpai

A small, open-source **B2B ERP** for the Procure-to-Pay process — useful on its own, without any AI.
Later phases add an **AI intelligence layer** on top (intelligent invoice processing, reconciliation support,
RAG, tool calling, MCP, agents).

> Design principle: deterministic business logic (money, tax, matching, reconciliation decisions) lives in the ERP.
> AI may extract, explain and recommend — it never makes authoritative monetary or accounting decisions.

## Status

| Module | Backend | UI |
|---|---|---|
| Suppliers | ✅ | ✅ |
| Products | ✅ | ✅ |
| Purchase orders | ✅ | ✅ |
| Goods receipts | ✅ | ✅ |
| Inventory / stock (warehouses, stock ledger, adjustments) | ✅ | ✅ |
| Supplier invoices | ✅ | ✅ (read-only) |
| Three-way invoice matching | ✅ | ✅ |

## Tech stack

- **Backend** (`backend/`): Kotlin, Spring Boot 4, Spring Data JPA, PostgreSQL, Flyway, Maven, JUnit 5, Testcontainers
- **Frontend** (`frontend/`): React, TypeScript, Vite, Mantine, TanStack Query, React Router
- **AI layer (planned)**: Python/FastAPI, Spring AI, document AI, pgvector, RAG, MCP, agents, evaluation —
  see [ROADMAP.md](ROADMAP.md)

## Getting started

Prerequisites: JDK 22+, Node.js 20+, Docker.

```bash
# 1. Database (PostgreSQL 17 on localhost:5433)
docker compose up -d

# 2. Backend (http://localhost:8080, Swagger UI at /swagger-ui.html)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Frontend (http://localhost:5173, proxies /api to the backend)
cd frontend
npm install
npm run dev

# 4. Optional demo data (backend must be running)
node scripts/seed-demo-data.mjs
```

## Tests

```bash
cd backend && ./mvnw verify     # unit tests + integration tests (needs Docker for Testcontainers)
cd frontend && npm run build    # type-check + production build
```

## Project layout

```
backend/    Spring Boot modular monolith, package-by-feature (supplier/, product/, ...)
frontend/   React SPA, folder-by-feature (features/suppliers, features/products, ...)
docker-compose.yml
```
