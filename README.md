# Carrinho Esperto — Backend

Brazilian supermarket purchase-intelligence backend. Ingests NFC-e fiscal
receipts, resolves their line items to canonical products, and serves spending
insight. Spring Boot 4 (Java 25), Spring Security OAuth2 Resource Server
(validates Cognito JWTs), Spring JDBC + ELSQL (no ORM), Flyway migrations,
PostgreSQL 17.

The consumer SPA lives in a separate repository: `carrinho-esperto-frontend`.

## Docs

- `docs/architecture.md` — bounded contexts, ownership rules, roadmap (start here)
- `docs/fiscal-core.md`, `nfce-ingestion.md`, `catalog-model.md`,
  `resolution-core.md`, `resolution-model.md`, `store-vocabulary.md`,
  `basket-insight.md`, `anonymous-trial.md`

## Local development

```bash
docker compose up -d --wait          # Postgres 17 + MiniStack Cognito + one-shot bootstrap
source infra/ministack/.cognito.env
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The bootstrap writes `infra/ministack/.cognito.env` (Cognito issuer/JWKS, client
id, dev user). For the separate frontend repo, copy `COGNITO_CLIENT_ID` into its
`.env` as `VITE_COGNITO_CLIENT_ID`.

Dev login: `dev@carrinho-esperto.local` / `Devpass123!`.

## Tests

```bash
mvn test                            # unit + integration (Testcontainers)
mvn test -Dtest=CatalogIntegrationTest
```

## Build

```bash
mvn -DskipTests package             # uber jar in target/
```

## Production (AWS)

EC2 (t3.micro) + RDS PostgreSQL + Cognito + S3/CloudFront, Free Tier, Flyway on
startup. CDK infrastructure will live in `infra/cdk/`. AWS CLI profile:
`carrinho-esperto` (region us-east-2) — always pass `--profile carrinho-esperto`.
