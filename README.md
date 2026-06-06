# Roles Service (`roles`)

The role-management microservice for the **Digital Notes / secure-vault** platform. It owns the canonical role catalogue (`ROLE_CUSTOMER`, `ROLE_ADMIN`, `ROLE_DELEGATE`) and the user-to-role mappings that the rest of the platform relies on for authorization. It exposes endpoints to create and delete roles, look up a user's roles (by username or userId), assign roles to users, and count holders of a given role. The **Authentication service** is its primary caller: it talks to this service over a Feign client to resolve roles, create role mappings (default `ROLE_CUSTOMER` on signup, delegate assignment), and bootstrap RBAC. This service in turn calls back into Authentication over its own Feign client to introspect Bearer tokens and look up users.

---

## Tech stack

| | |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 4.0.6 (Spring Cloud 2025.1.1) |
| Web | Spring Web MVC |
| Persistence | Spring Data JPA + Hibernate, PostgreSQL |
| Inter-service calls | Spring Cloud OpenFeign (→ Authentication service) |
| Auth enforcement | `HandlerInterceptor` (token introspection via Feign) + shared internal-key header |
| Mapping | ModelMapper |
| Validation | Spring Boot Validation (Jakarta) |
| JSON | `org.json` |
| API docs | springdoc-openapi (Swagger UI) |
| Build | Maven (wrapper included) |

> Note: `spring-security-crypto` is present transitively and a `BCryptPasswordEncoder` bean is declared in [CorsConfiguration.java](src/main/java/com/application/roles/configuration/CorsConfiguration.java), but this service does **not** run the Spring Security filter chain — request authentication is handled by a custom interceptor (see [Security model](#security-model)).

---

## How it fits in the platform

```
                          ┌────────────────────┐
   Browser / UI ─▶ Authentication ──Feign──▶  │   roles-service    │
                  (issues JWT)    X-INTERNAL-  │   (this service)   │
                                  KEY / Bearer │  role catalogue +  │
                                               │  user→role mapping │
                                               └─────────┬──────────┘
                                                         │ Feign (introspect token,
                                                         ▼ getUserByUsername)
                                                  Authentication
```

- **roles-service is internal.** The only legitimate caller is the Authentication service over the cluster network; the UI never calls `/roles` directly. The shipped Ingress + host nginx snippet expose it publicly anyway for parity / Swagger access and can be omitted in prod (see [Deployment](#deployment)).
- Protected endpoints are guarded by [AuthenticationInterceptor](src/main/java/com/application/roles/configuration/AuthenticationInterceptor.java), which validates the incoming `Authorization: Bearer <jwt>` by calling Authentication's `/api/user/public/introspect` and checking for `ROLE_ADMIN`.
- Internal machine-to-machine endpoints are guarded by a shared `X-INTERNAL-KEY` header that must match `INTERNAL_ROLE_SERVICE_KEY` (the same secret configured on the Authentication deployment).

---

## Running locally

### Prerequisites
- JDK 21
- PostgreSQL reachable via the JDBC URL below (default local DB/schema: `digital-notes` / `secure-vault`)
- A running Authentication service (for token introspection and user lookups)
- Maven (or use the bundled `.\mvnw.cmd` / `./mvnw`)

### Environment variables

The service reads everything from environment variables (see [application.yml](src/main/resources/application.yml)). Defaults for local dev are in [application-local.yml](target/classes/application-local.yml).

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/digital-notes?currentSchema=secure-vault` |
| `SPRING_DATASOURCE_USERNAME` | Postgres username (e.g. `postgres`) |
| `SPRING_DATASOURCE_PASSWORD` | Postgres password |
| `INTERNAL_ROLE_SERVICE_KEY` | Shared internal secret for the `X-INTERNAL-KEY` header on internal endpoints — **must match the Authentication service** |
| `AUTHENTICATION_SERVICE_URL` | Base URL of the Authentication service (Feign client target), e.g. `http://localhost:3211/authentication` |

Deploy-time variables consumed by `ci/deploy.sh` (mapped onto the above and the k8s manifests): `VPS_USER`, `VPS_HOST`, `REMOTE_DIR`, `LXD_CONTAINER`, `KUBE_NAMESPACE`, `APP_NAME`, `IMAGE_REPO`, `IMAGE_TAG`, `INGRESS_HOST`, `LXD_BRIDGE_IP`, `DB_URL`, `DB_USERNAME` (default `postgres`), `DB_PASSWORD`, `INTERNAL_ROLE_SERVICE_KEY`, `AUTHENTICATION_SERVICE_URL`, `REPLICAS` (default `1`), `SWAGGER_ENABLED` (default `false`), `INSTALL_PUBLIC_INGRESS` (default `true`).

### Start it

```powershell
# Windows (PowerShell)
.\mvnw.cmd spring-boot:run
```

```bash
# Linux / macOS
./mvnw spring-boot:run
```

The service starts on **port `3212`** with context path **`/roles`**, so it responds at:

```
http://localhost:3212/roles/...
```

Swagger UI: `http://localhost:3212/roles/swagger.html`

---

## API overview

All paths below are relative to the context path `/roles`. Auth enforcement is applied by [AuthenticationInterceptor](src/main/java/com/application/roles/configuration/AuthenticationInterceptor.java), which registers on `/**` via [WebInterceptorConfiguration](src/main/java/com/application/roles/configuration/WebInterceptorConfiguration.java). It requires a `Bearer` JWT with `ROLE_ADMIN` **only** for paths matching `/api/role/createRoles`, `/api/role/deleteRole`, and `/userRoleMappings`; everything else (Swagger, `/api/public/**`, and all other `/api/role/**` and `/api/role-mapping/**` reads/writes) passes through the interceptor unauthenticated. The internal controllers self-enforce a shared `X-INTERNAL-KEY` header instead.

### Roles — `/api/role` ([RoleController.java](src/main/java/com/application/roles/controllers/RoleController.java))

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/role/createRoles` | Bearer · `ROLE_ADMIN` | Create a role from `{ roleType, description }`; also maps the role to the calling user (resolved via Feign `getUserByUsername`). Returns `201`. |
| `GET` | `/api/role/getAllRoles` | none enforced | List all roles. |
| `DELETE` | `/api/role/deleteRole?roleId=` | Bearer · `ROLE_ADMIN` | Delete a role and its user-role mappings. |
| `GET` | `/api/role/getRolesByUsername?username=` | none enforced | Return the list of role-type strings for a username (resolves the user via Feign). |
| `GET` | `/api/role/rolesByUserId?userId=` | none enforced | Return roles (`RoleRespDto[]`) mapped to a userId. |
| `GET` | `/api/role/getRolesByRoleName?roleType=` | none enforced | Look up a single role by type (auto-prefixes `ROLE_`). |

### User-role mapping — `/api/role-mapping` ([UserRoleMappingController.java](src/main/java/com/application/roles/controllers/UserRoleMappingController.java))

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/role-mapping/assign?roleType=&userId=` | `X-INTERNAL-KEY` header | Assign a role to a user (idempotent). Header is checked against a hard-coded `MY_SUPER_SECRET_KEY` in the controller. |

> Caveat found in code: this controller compares `X-INTERNAL-KEY` against a hard-coded literal `"MY_SUPER_SECRET_KEY"` rather than the `internal.role-service-key` config property. The newer [InternalRolesController](src/main/java/com/application/roles/controllers/InternalRolesController.java) below uses the configured key.

### Internal roles — `/api/internal/roles` ([InternalRolesController.java](src/main/java/com/application/roles/controllers/InternalRolesController.java))

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/internal/roles/delegate-role?roleType=&userId=` | `X-INTERNAL-KEY` header (`internal.role-service-key`) | Assign `ROLE_DELEGATE` to a user. Rejects any non-delegate role type. |
| `GET` | `/api/internal/roles/delegates/count` | `X-INTERNAL-KEY` header (`internal.role-service-key`) | Count users holding `ROLE_DELEGATE`. |

### Public user-role mapping — `/api/user/public/userRoleMappings` ([PublicUserRoleMappingController.java](src/main/java/com/application/roles/controllers/PublicUserRoleMappingController.java))

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/user/public/userRoleMappings/default?userId=` | public | Assign the default `ROLE_CUSTOMER` to a new user (idempotent). Called by Authentication on signup. |

---

## Security model

There is **no `SecurityConfiguration` / Spring Security filter chain** in this service. Authentication is enforced by a custom MVC interceptor, [AuthenticationInterceptor](src/main/java/com/application/roles/configuration/AuthenticationInterceptor.java), registered on all paths by [WebInterceptorConfiguration](src/main/java/com/application/roles/configuration/WebInterceptorConfiguration.java):

- **Allowlist (pass-through):** `/swagger*`, `/v3/api-docs*`, `/actuator*`, and any `/api/public/**` path.
- **Admin-only paths:** `/userRoleMappings*`, `/api/role/createRoles`, `/api/role/deleteRole`. For these the interceptor:
  1. requires `Authorization: Bearer <jwt>`,
  2. calls Authentication's `introspect` over Feign ([AuthenticationClient](src/main/java/com/application/roles/feignService/AuthenticationClient.java)),
  3. rejects inactive/expired tokens (`401`) and tokens whose roles do not include `ROLE_ADMIN` (`403`),
  4. on success stores `auth_username` as a request attribute for audit.
- **All other paths** pass through the interceptor without authentication — including the bulk of `/api/role/**` reads and `/api/role-mapping/**`.
- **Internal endpoints** (`/api/internal/roles/**`, `/api/role-mapping/assign`) are not covered by the admin allowlist; they self-enforce a shared `X-INTERNAL-KEY` header inside the controller.
- **CORS** is restricted to `http://localhost:3000` (methods GET/POST/PUT/DELETE/OPTIONS) in [CorsConfiguration](src/main/java/com/application/roles/configuration/CorsConfiguration.java).
- **Role seeding:** [RoleSeeder](src/main/java/com/application/roles/configuration/RoleSeeder.java) is a `CommandLineRunner` that creates `ROLE_CUSTOMER`, `ROLE_ADMIN`, and `ROLE_DELEGATE` on startup if missing.

---

## Data model

Two JPA entities persisted in PostgreSQL (schema `secure-vault`):

- **`roles`** ([Roles.java](src/main/java/com/application/roles/model/Roles.java)) — `roleId` (PK, app-generated string), `roleType` (e.g. `ROLE_ADMIN`), `description`, `createdAt`, `updatedAt`, `status`. A `@Transient` `Users` field carries the owning user at creation time but is not persisted.
- **`user_role_mapping`** ([UserRoleMapping.java](src/main/java/com/application/roles/model/UserRoleMapping.java)) — `mapId` (PK, app-generated), `userId`, `roleId`. Join table linking users to roles.

[Users.java](src/main/java/com/application/roles/model/Users.java) is **not** an entity here — it is a plain DTO used to deserialize the user object returned by the Authentication service over Feign. Likewise [TokenIntrospectionResponse](src/main/java/com/application/roles/dtos/TokenIntrospectionResponse.java) (`active`, `username`, `roles`) carries the introspection result. Request/response DTOs: [RoleDto](src/main/java/com/application/roles/dtos/RoleDto.java) (`roleType`, `description`) and [RoleRespDto](src/main/java/com/application/roles/dtos/RoleRespDto.java) (`roleId`, `roleType`, `description`). [ApiResponse](src/main/java/com/application/roles/utils/ApiResponse.java) is the standard envelope (`status`, optional `roles`, `message`).

IDs are generated from a `LocalDateTime`-derived string pattern (e.g. `ROLE2026...`, `MAP2026...`).

---

## Build, test & package

```bash
./mvnw clean test       # run tests
./mvnw clean package    # build the executable jar → target/*.jar
java -jar target/roles-0.0.1-SNAPSHOT.jar
```

On Windows use `.\mvnw.cmd` in place of `./mvnw`.

---

## Docker

A multi-stage [Dockerfile](Dockerfile) is provided (Maven build stage → JRE-only `eclipse-temurin:21-jre` runtime). It exposes port `3212`.

```bash
docker build -t secure-vault-roles .
docker run -p 3212:3212 --env-file .env secure-vault-roles
```

The image accepts `GIT_COMMIT`, `BUILD_NUMBER`, and `BUILD_DATE` build args for OCI labels.

---

## Deployment

CI/CD lives under `ci/` with the root Kubernetes manifests:

- [ci/deploy.sh](ci/deploy.sh) — renders the manifests and nginx snippet with `sed` and ships them to a k3s cluster (inside an LXD container on a VPS) over SSH. See its header comment for the full list of required deployment variables.
- [ci/deploy-remote.sh](ci/deploy-remote.sh) — runs on the LXD host: waits for k3s, applies the manifests, waits for the deployment to be Available, runs a routing test, and (optionally) installs the host nginx snippet.
- [deployment.yml](deployment.yml), [service.yml](service.yml), [ingress.yml](ingress.yml) — Kubernetes manifests templated with `${VAR}` placeholders. The Service is `ClusterIP` (port `80` → `3212`); Authentication reaches it via cluster DNS `${APP_NAME}-service`.
- [ci/nginx/roles.location.conf](ci/nginx/roles.location.conf) — host nginx reverse-proxy snippet for `/roles/*`.

Because roles is an internal service, the Ingress and nginx snippet are optional: set `INSTALL_PUBLIC_INGRESS=false` to keep it reachable only on the cluster's internal network (the safe default for prod). When `true` (default), `/roles/*` is exposed publicly for parity and Swagger access.

---

## Project layout

```
roles/
├── Dockerfile                          # Multi-stage build, exposes 3212
├── deployment.yml · service.yml · ingress.yml   # K8s manifests (${VAR} templated)
├── ci/
│   ├── deploy.sh · deploy-remote.sh    # Render + ship + apply to k3s/LXD over SSH
│   └── nginx/roles.location.conf       # Host nginx reverse-proxy snippet
└── src/main/java/com/application/roles/
    ├── RolesApplication.java           # Spring Boot entry point (@EnableFeignClients)
    ├── configuration/                  # AuthenticationInterceptor, WebInterceptorConfiguration,
    │                                   #   CorsConfiguration, SwaggerConfiguration, RoleSeeder
    ├── controllers/                    # RoleController, UserRoleMappingController,
    │                                   #   InternalRolesController, PublicUserRoleMappingController
    ├── service/                        # RoleService(Impl), UserRoleMappingService(Impl)
    ├── feignService/AuthenticationClient.java   # Feign client → Authentication service
    ├── repository/                     # RoleRepository, UserRoleMappingRepository
    ├── model/                          # Roles, UserRoleMapping (JPA); Users (Feign DTO)
    ├── dtos/                           # RoleDto, RoleRespDto, TokenIntrospectionResponse
    ├── exceptions/                     # GlobalExceptionHandler + custom exceptions
    └── utils/                          # ApiResponse, Constants
```
