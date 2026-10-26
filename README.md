# LifeStyle Store

A full-stack e-commerce demo: **Java 21 + Spring Boot 4**, **MongoDB**, **JWT auth**, a dependency-free responsive
frontend, and an **agentic AI shopping assistant powered by Claude**. It started life as a static Bootstrap 3
prototype and has been rebuilt as a secure, containerised, tested service with a 100+ product catalogue.

Original static prototype: https://shubham541.github.io/LifestyleStore/ (screenshots in `docs/legacy/`).

## Features

**Shopping**
- 102 products across 14 departments (mobiles, laptops, audio, smartwatches, cameras, men's and women's clothing,
  footwear, watches, bags, home and kitchen, beauty, sports, books) with brands, ratings, MRP and discounts, in INR
- Search by name, brand or tag; filter by department, price range, rating, brand and stock; sort by popularity, price,
  rating or discount; shareable URLs; skeleton loading and empty states
- Product pages with related products, server-side cart with quantity steppers, savings and a free-delivery progress
  bar (free above INR 499, otherwise INR 49), order placement and order history
- Sign up, login, lockout after repeated failures, change password
- Light and dark themes, keyboard and screen-reader friendly, works from phone to desktop

**AI assistant ("Lifey")**
- Claude is given three read-only tools that run against the live catalogue (`search_products`,
  `get_product_details`, `compare_products`) and decides what to look up before answering, so answers stay grounded
  in real prices, ratings and stock instead of a prompt-stuffed catalogue
- Multi-turn conversations, an "Ask AI about this product" button that anchors the chat to the product being viewed,
  follow-up suggestion chips, and product cards in the chat with an Add to cart button
- Works without an API key: a deterministic natural-language parser (departments, brands, budgets such as
  "under 30k", "cheapest", "best rated", "deals") answers instead, and also takes over if the upstream call fails

## Architecture

```
browser (static SPA, hash routes, ES modules)
   |  JSON + Bearer JWT
Spring Boot 4 (web, security, validation, actuator)
   |-- web/      REST controllers, RFC 9457 problem responses
   |-- service/  auth, catalogue search, cart, orders, pricing, assistant (Claude tool loop + offline parser)
   |-- config/   security chain, rate limiting, request-id logging, catalogue seeder
   '-- repo/     Spring Data MongoDB
MongoDB 8        users, products, carts, orders
Claude API       Messages API with tool use (optional)
```

The catalogue lives in `src/main/resources/seed/products.json`. On every start the seeder syncs it into MongoDB:
product data comes from the file, live stock levels are preserved, and legacy documents from older schema versions
are migrated automatically.

## Run it

Requirements: Java 21, Maven 3.9+, MongoDB (local) **or** Docker.

**With Docker Compose** (app + MongoDB with auth):

```bash
cp .env.example .env     # fill in JWT_SECRET (>= 32 chars) and MONGO_PASSWORD
docker compose up --build
# open http://localhost:8080
```

**Locally** against a MongoDB on `localhost:27017`:

```bash
cp .env.example .env     # then set JWT_SECRET (32+ chars); ANTHROPIC_API_KEY is optional
mvn spring-boot:run      # or run LifestyleStoreApplication from your IDE
# open http://localhost:8080
```

Spring Boot reads the gitignored `.env` in the project root automatically (`spring.config.import`), so no IDE
run-configuration arguments are needed. Real environment variables still take precedence, which is how containers
and CI supply secrets. Make sure the IDE's working directory is the project root.

The app refuses to start without a valid `JWT_SECRET`.

### Configuration

| Variable | Purpose | Default |
|---|---|---|
| `JWT_SECRET` | HS256 signing secret, 32+ chars (required) | none |
| `MONGODB_URI` | Mongo connection string | `mongodb://localhost:27017/lifestylestore` |
| `ANTHROPIC_API_KEY` | Enables Claude in the assistant | unset (offline parser) |
| `ANTHROPIC_MODEL` | Claude model id | `claude-haiku-5-5` |
| `OPENAPI_ENABLED` | Swagger UI and `/v3/api-docs` | `true` (set `false` in prod) |
| `PORT` | HTTP port | `8080` |

## API overview

| Method & path | Auth | Description |
|---|---|---|
| `POST /api/auth/signup`, `/api/auth/login` | public | Register, obtain JWT |
| `GET /api/products` | public | Search: `q`, `category`, `brand`, `minPrice`, `maxPrice`, `minRating`, `inStock`, `sort`, `page`, `size` |
| `GET /api/products/meta` | public | Departments with counts and brands |
| `GET /api/products/{id}`, `/{id}/related` | public | Product detail, related products |
| `GET /api/cart`, `POST /api/cart/items`, `PUT/DELETE /api/cart/items/{id}` | user | Own cart only |
| `POST /api/orders`, `GET /api/orders` | user | Order from cart, order history |
| `GET /api/account`, `POST /api/account/password` | user | Profile, change password |
| `POST /api/assistant/chat` | user | Multi-turn AI assistant, optional `productId` context |

Interactive docs at `/swagger-ui.html` when `OPENAPI_ENABLED=true`.

## Security notes

- Stateless JWT (HS256 pinned, issuer and expiry validated, `alg=none` rejected), BCrypt cost 12, constant-time-ish
  login for unknown emails, lockout after 5 failures, per-IP rate limits on credential endpoints and per-user limit
  on the assistant
- Bean Validation on every request body and query parameter; catalogue search input is quoted so it is never
  evaluated as a regex; prices and totals are always computed on the server
- Strict CSP (`script-src 'self'`), `X-Frame-Options: DENY`, `nosniff`, no referrer; the UI builds the DOM with
  `createElement`/`textContent` and never uses `innerHTML`
- Errors return generic problem responses with a request id; stack traces stay in the logs
- Container runs as non-root with a read-only root filesystem and dropped capabilities; secrets come from the
  environment or a Kubernetes Secret, never the repo
- The assistant's tools are read-only and see only the public catalogue (no carts, orders or user data). Tool
  inputs are validated and clamped, the tool loop is bounded, history is length-capped, model output is rendered as
  plain text, cited product ids are re-checked against the catalogue, and "add to cart" is always the customer's click

Known trade-offs for a demo: the access token lives in `sessionStorage` (mitigated by the CSP), tokens are not
revoked on password change (60-minute TTL), the rate limiter is in-memory and keys on the socket address (configure
forwarded headers only behind a trusted proxy, or move to Redis for multiple replicas), and anyone can register and
use the assistant, so set a spend limit on the Anthropic key.

## Tests and CI

```bash
mvn verify
```

Unit tests (Mockito) cover auth and lockout, order placement, stock rollback and shipping rules, the rate limiter, the
assistant's natural-language parser, tool input handling and conversation validation. `StoreFlowTest` runs the full
signup, search, cart, order and assistant flow against a real MongoDB through Testcontainers; it is skipped
automatically when Docker is unavailable. GitHub Actions runs build, tests, a Docker image build and CodeQL, and
Dependabot keeps Maven, Actions and Docker dependencies current.

## Deploy

`k8s/app.yaml` provides a hardened Deployment (probes, resource limits, non-root, read-only FS), Service and
PodDisruptionBudget. Create the secret from `k8s/secret.example.yaml` out of band.

## Project layout

```
src/main/java/...                 Spring Boot application
src/main/resources/seed/          product catalogue (products.json)
src/main/resources/static/        frontend (index.html, css/, js/, img/)
src/test/java/...                 unit and integration tests
Dockerfile, docker-compose.yml, k8s/, .github/
notebooks/fyp.ipynb               unrelated Colab notebook (OpenCV/NumPy experiments)
docs/legacy/                      screenshots of the original static prototype
```

### Product images

Most products have a real photo (`static/img/products/`), sourced from Wikimedia Commons and Openverse under
Creative Commons or public-domain licences and credited on `/image-credits.html` (also `docs/IMAGE_CREDITS.md`). The 12
original camera, watch and shirt photos are the author's own. Every candidate photo was reviewed by eye. Where the
exact model could not be found, a photo of the same type of product is shown and captioned "Representative image" on
the product page. Photos of people were deliberately excluded, as were photos that show another brand's logo.
Products with no suitable photo (books, some beauty and clothing items) render as generated department-coloured
tiles. To add or replace a photo, drop the file in `static/img/products/` and set `image` (and `imageNote` for
representative photos) in `seed/products.json`.

## Roadmap

- Publish order-placed events to Kafka and add an async consumer (notifications, inventory sync)
- Streaming assistant responses, wishlists, reviews and an admin role for catalogue management
- Refresh tokens / token revocation, or delegate login to an OIDC provider
- Payment integration (Stripe test mode)
