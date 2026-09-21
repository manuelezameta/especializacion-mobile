# Backend AppTaxi (NestJS + TypeORM)

Servicio backend de ejemplo para el curso — arquitectura modular con **NestJS 12**, **TypeORM 1.x (MySQL)**, **JWT**, **i18n**, **Pino logger** y **Swagger**. Incluye un flujo de **login por teléfono** para pasajeros.

---

## Requisitos

| Herramienta | Versión                                                       | Notas                                                                                                                                                            |
| ----------- | ------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Node.js     | **24 LTS** (recomendado). Mínimo `^22.22.3`, `^24.15` o `>=26` | Es el piso de `@nestjs/schematics` 12 (dependencia de `@nestjs/cli` 12). Con Node 22.14 `pnpm build` falla con `ERR_REQUIRE_CYCLE_MODULE`. Está fijado en `engines`. |
| pnpm        | **12.5.1** (fijado en `packageManager`)                       | Con Node 24: `corepack enable` y corepack descarga la versión fijada. Ver [Instalación](#instalación).                                                            |
| MySQL       | **9.7 LTS** (validado)                                        | Se levanta con `../infra-app-taxi` (Docker Compose). La app autentica con `caching_sha2_password`.                                                               |
| Docker      | Ver `../infra-app-taxi/README.md`                             | Solo si vas a usar la imagen del backend.                                                                                                                        |

> Este proyecto usa **pnpm** siempre (nunca npm ni yarn).

---

## Tecnologías principales

| Tecnología       | Versión | Uso                                                            |
| ---------------- | ------- | -------------------------------------------------------------- |
| NestJS           | 12.0    | `@nestjs/common`, `core`, `platform-express`, `config`, `jwt`, `swagger`, `typeorm` |
| TypeORM          | 1.1     | Migraciones, repos, `DataSource` (driver `mysql2` 3.24)        |
| TypeScript       | 6.0     | Ver nota de versiones abajo                                    |
| JWT              | —       | `@nestjs/jwt` — access y refresh tokens                        |
| nestjs-i18n      | 10.8    | Mensajes ES/EN vía `Accept-Language`                           |
| nestjs-pino      | 5.2     | Logs estructurados con `x-request-id` (`pino` 10)              |
| Swagger          | —       | Documentación en `/api/docs`                                   |
| ESLint / Prettier| 10 / 3.9| Lint y formato                                                 |
| Jest / ts-jest   | 30 / 29 | Pruebas unitarias                                              |

**Notas de versiones**

- **TypeScript 7 no se adopta**: el paquete `typescript@7` es el compilador nativo y no expone la API clásica de JS; además `typescript-eslint` declara `typescript >=4.8.4 <6.1.0`, `ts-jest` `<7` y `@nestjs/cli` 12 depende de `typescript ~6.0`.
- `ioredis` se mantiene en 5.x porque `typeorm` 1.1 declara `ioredis ^5.0.4` como peer opcional.
- `@types/node` sigue la versión de Node de la imagen Docker (24).
- pnpm 12 aplica `minimumReleaseAge` de 1 día por defecto: las versiones publicadas hace menos de 24 h no entran al lockfile hasta que pasen ese tiempo (`pnpm update` las recogerá).

---

## Instalación

```bash
# 1) Activar pnpm (una sola vez, Node 24). Lee la versión de "packageManager"
corepack enable

# 2) Instalar dependencias
pnpm install

# 3) Crear el archivo de variables de entorno (ver sección Configuración)
touch .env
```

- El repositorio **no** incluye un `.env.example`; crea `.env` con las variables de la sección siguiente.
- Un pnpm 10.11.0 instalado globalmente (probado) falla con `Failed to switch pnpm to v12.5.1` y, si se desactiva el cambio automático, con `ERR_PNPM_BROKEN_LOCKFILE`: el lockfile de pnpm 12 tiene varios documentos YAML. Actualiza pnpm (<https://pnpm.io/installation>) o usa `corepack enable` con Node 24. El corepack de Node 22.14 (0.31) tampoco soporta pnpm 11+.
- `pnpm-workspace.yaml` declara `allowBuilds`: desde pnpm 11 la instalación falla con `ERR_PNPM_IGNORED_BUILDS` si una dependencia trae script de build sin declarar. `@parcel/watcher`, `@scarf/scarf`, `@swc/core`, `argon2` y `unrs-resolver` están en `false` (ninguna es necesaria para compilar ni ejecutar). Cambia a `true` solo si empiezas a usar una que requiera compilar.

---

## Configuración (.env)

Variables usadas por la app (según `src/core/database/typeorm.config.ts`, `src/main.ts`, `src/app.module.ts` y servicios):

```bash
# App
APPLICATION_PORT=3001
NODE_DEBUG=false                  # true = logs bonitos con pino-pretty (solo local, ver Logging)

# DB (usa los valores de ../infra-app-taxi/.env)
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USERNAME=root
DB_PASSWORD=change-me
DB_NAME=db_app_taxi
DB_POOL=10

# JWT
JWT_ACCESS_SECRET=change-this-access
JWT_REFRESH_SECRET=change-this-refresh
JWT_ACCESS_TTL_SEC=900          # 15m
JWT_REFRESH_TTL_SEC=2592000     # 30d

# Redis (solo lo lee CacheService, que hoy no está importado en AppModule)
REDIS_HOST=127.0.0.1
REDIS_PORT=6379

# I18N
# El módulo i18n usa Accept-Language y fallback "es"
```

`APPLICATION_MODE` solo lo lee `entrypoint.sh` dentro de Docker (por defecto `http`).

> La app **no** usa `synchronize`. Asegúrate de correr **migraciones**.

---

## Base de datos: migraciones y seeders

MySQL local: levántalo con `../infra-app-taxi` (ver su README) y apunta el `.env` a `127.0.0.1:3306`.

### Ejecutar migraciones

**A) CLI de TypeORM** (usa el `DataSource` de `src/core/database/typeorm.migration.ts`):

```bash
# generar nueva migración (sin "--"; termina con código 1 si no hay cambios de esquema)
pnpm migration:generate src/core/database/migrations/<nombre>

# aplicar migraciones pendientes
pnpm migration:run

# revertir última migración
pnpm migration:revert
```

**B) Runners TS incluidos**

```bash
pnpm exec ts-node -r tsconfig-paths/register src/core/cli/run-migrations.ts
```

### Seeders

Incluye un seeder de pasajeros (`src/core/database/seeders/passenger-seed.ts`) que carga `passengers.json`. Es idempotente: si el teléfono ya existe lo omite.

```bash
pnpm exec ts-node -r tsconfig-paths/register src/core/cli/run-seeders.ts
```

---

## Ejecución

### Desarrollo

```bash
pnpm start:dev
# Swagger: http://localhost:${APPLICATION_PORT}/api/docs
```

### Producción

```bash
pnpm build
pnpm start:prod
```

> La app levanta en el puerto `APPLICATION_PORT` (defínelo en `.env`).

### Pruebas y lint

```bash
pnpm test      # Jest (hoy: pruebas del mapper de pasajeros)
pnpm lint      # ESLint con --fix
```

> Estado actual de `pnpm lint`: reporta 45 errores y 3 warnings preexistentes (reglas `no-unsafe-*` de typescript-eslint y formato Prettier en la migración y `trip.model.ts`). Como el script usa `--fix`, reescribe esos archivos; para solo revisar usa `pnpm exec eslint "{src,apps,libs,test}/**/*.ts"`.

---

## Endpoints principales

### POST `/passenger/login`

Login por número de teléfono (E.164 **sin** `+` ni símbolos, exactamente 11 caracteres). El teléfono debe existir en `entity_passenger` (el seeder crea los de `passengers.json`).

**Request body**

```json
{
    "phone": "51987654321"
}
```

**Responses**

- **200 OK**

```json
{
    "accessToken": "jwt_access",
    "refreshToken": "jwt_refresh",
    "user": {
        "id": "uuid",
        "phoneNumber": "51987654321",
        "givenName": "Nombre",
        "familyName": "Apellido",
        "email": "mail@dominio.com",
        "photoUrl": null,
        "status": "ACTIVE"
    }
}
```

`user` no incluye `lastLoginAt`, `createdAt`, `updatedAt` ni `deletedAt`. Payload de los JWT: `sub` (id), `typ: "passenger"`, `phone`, `iat`, `exp` (y `rt: true` en el refresh). Duración: `JWT_ACCESS_TTL_SEC` y `JWT_REFRESH_TTL_SEC`.

- **422 Unprocessable Entity** – pasajero no existe (no es 404)  
  El mensaje proviene de i18n (`passenger.notExists`) según `Accept-Language` (`es` / `en`; cualquier otro idioma o sin header usa `es`):

```json
{
    "status_code": 422,
    "message": "Tu número de teléfono no existe."
}
```

- **400 Bad Request** – validaciones (`ValidationPipe` global) o JSON mal formado  
  Formato armado por `HttpExceptionFilter`; los mensajes de `class-validator` se traducen con `Accept-Language`:

```json
{
    "status_code": 400,
    "message": "Bad Request",
    "errors": [
        {
            "field": "general",
            "message": "Debe tener al menos 11 caracteres."
        }
    ]
}
```

Un campo no permitido (`forbidNonWhitelisted`) devuelve `"property extra should not exist"` sin traducir; un JSON mal formado devuelve `errors: []`.

> Swagger documenta un 404 para este endpoint (`@ApiNotFoundResponse`), pero el código real responde 422.

---

## Arquitectura y estructura

Organización modular por **feature** y utilidades en `core/`:

```
src/
├─ app.module.ts
├─ app.controller.ts / app.service.ts   # GET / ("Hello World!")
├─ main.ts
├─ trip.model.ts                        # tipos de viaje (sin uso por ahora)
├─ commons/
│  └─ utils/UtilDate.ts
├─ core/
│  ├─ cache/                            # CacheService (Redis); no está importado en AppModule
│  ├─ cli/                              # Runners de migraciones/seeders
│  ├─ database/
│  │  ├─ typeorm.config.ts              # DataSource app
│  │  ├─ typeorm.migration.ts           # DataSource CLI
│  │  ├─ migrations/                    # Migraciones TypeORM
│  │  └─ seeders/                       # Seeders y datos (passengers.json)
│  ├─ http/
│  │  ├─ exception/http.exception.ts    # HttpCustomException (422 por defecto)
│  │  └─ filters/
│  │     ├─ http-exception.filter.ts    # Formato uniforme de errores (registrado)
│  │     └─ validation-exception.filter.ts  # Definido pero no registrado
│  └─ i18n/
│     ├─ es/passenger.json              # i18n ES
│     └─ en/passenger.json              # i18n EN
│
└─ features/
   └─ passengers/
      ├─ passenger.module.ts
      ├─ controllers/passenger.controller.ts   # POST /passenger/login
      ├─ services/passenger.service.ts         # Emite JWTs y toca lastLoginAt
      ├─ dao/passenger.dao.ts                  # Acceso puro a BD
      ├─ entities/passenger.entity.ts          # TypeORM entity
      ├─ dto/                                  # Request/Response DTOs
      ├─ enum/passenger-status.enum.ts
      └─ mapper/passenger.mapper.ts            # + passenger.mapper.spec.ts
```

**Capa de presentación**: Controllers (Swagger, validación)  
**Capa de aplicación**: Services (orquestan DAO + emisión JWT)  
**Capa de acceso a datos**: DAO (TypeORM)  
**Dominio**: Entidades/DTOs/enums/mappers

---

## Docker

El `Dockerfile` es multi-stage sobre `node:24-alpine`:

| Stage    | Qué hace                                                                                       |
| -------- | ---------------------------------------------------------------------------------------------- |
| `base`   | Activa pnpm 12.5.1 con corepack (misma versión que `packageManager` y que generó el lockfile)  |
| `deps`   | `pnpm install --frozen-lockfile --prod=false` (copia `package.json`, `pnpm-lock.yaml` y `pnpm-workspace.yaml`) |
| `build`  | `pnpm build` (`nest build`)                                                                    |
| `pruned` | `pnpm prune --prod`                                                                            |
| `runner` | Copia `node_modules` podado y `dist`; corre como usuario `node`                                |

`entrypoint.sh` espera a MySQL (`DB_HOST:DB_PORT`), ejecuta migraciones y seeders y luego arranca `node dist/main.js`. Para levantar todo con Compose sigue `../infra-app-taxi/README.md`.

> Con `NODE_DEBUG=true` la imagen no arranca: `pino-pretty` es una dependencia de desarrollo y `pnpm prune --prod` la elimina. Usa `false` en Docker.

---

## Seguridad

- **JWT**: usa `JWT_ACCESS_SECRET` y `JWT_REFRESH_SECRET` **fuertes**.
- **No** incluir datos sensibles en el payload JWT.
- Recomendada **rotación** de refresh tokens y almacenamiento/blacklist (Redis/DB).
- Considera **rate limit** en `/passenger/login`.
- Habilita CORS si expones la API a frontends externos.
- No loguees datos personales sensibles.

---

## Logging

- `nestjs-pino` agrega `x-request-id` y logs JSON.
- En `NODE_DEBUG=true`, usa `pino-pretty` (color, tiempos legibles) y nivel `debug`; solo en local (ver [Docker](#docker)).
- Todas las excepciones HTTP pasan por `HttpExceptionFilter` para formato uniforme.
- `typeorm.config.ts` tiene `logging: true`, por lo que se imprimen las consultas SQL.

---

## Swagger

Disponible en:

```
http://localhost:${APPLICATION_PORT}/api/docs
```

El JSON de OpenAPI está en `/api/docs-json`. Incluye esquema de DTOs (`PassengerLoginRequestDto`, `PassengerLoginResponseDto`, `PassengerDto`).

---

## Troubleshooting

| Síntoma                                               | Causa / solución                                                                                                                                       |
| ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `ERR_REQUIRE_CYCLE_MODULE` al correr `pnpm build`     | Node demasiado antiguo (p. ej. 22.14). Usa Node 24 LTS (o `^22.22.3`).                                                                                  |
| `Failed to switch pnpm to v12.5.1`                    | pnpm global antiguo (probado con 10.11.0). Actualiza pnpm o usa `corepack enable` con Node 24.                                                                          |
| `ERR_PNPM_IGNORED_BUILDS`                             | Una dependencia nueva trae script de build: agrégala a `allowBuilds` en `pnpm-workspace.yaml`.                                                          |
| `EntityMetadataNotFoundError`                         | Ajusta el glob de entidades en `typeorm.config.ts` (p. ej. `__dirname + "/../../**/*.entity{.ts,.js}"`) o usa `autoLoadEntities: true` y revisa `TypeOrmModule.forFeature(...)`. |
| **422** en `/passenger/login`                         | El teléfono no existe en `entity_passenger` o tiene `deleted_at` distinto de `NULL`. Formato E.164 **sin** `+` y de 11 dígitos.                          |
| **400** con `errors[].field = "general"`              | Falló la validación del body (ver formato arriba).                                                                                                      |
| i18n no traduce                                       | Confirma las claves (`passenger.notExists`, `passenger.validation.*`) y el `Accept-Language` del request.                                                |
| `pnpm migration:generate -- <ruta>` falla             | No uses `--`: `pnpm migration:generate <ruta>`.                                                                                                         |
| El contenedor se reinicia en bucle con `pino-pretty`  | `NODE_DEBUG=true` en la imagen de producción (ver [Docker](#docker)).                                                                                   |

---

## Licencia

Uso académico / educativo.
