# App taxi — Infra

Este directorio contiene la **configuración modular de infraestructura** para el backend de `app-taxi`, basada en **Docker Compose**. Permite levantar **MySQL**, **Redis** y la **API HTTP** del backend (`../backend-app-taxi`) de forma **independiente**, compartiendo una **red externa** y variables de entorno comunes.

En esta clase solo existe el modo **HTTP**. No hay modos CRON ni WebSocket, y por eso no hay un puerto 3002.

---

## Paso 0) Instalación de Docker y Docker Compose

Antes de continuar, asegúrate de tener instalados **Docker Engine** y **Docker Compose v2** (plugin `docker compose`) en tu máquina:

### Windows

1. Descarga e instala **Docker Desktop** desde: [https://www.docker.com/products/docker-desktop/](https://www.docker.com/products/docker-desktop/)
2. Verifica la instalación en PowerShell o CMD:
    ```bash
    docker --version
    docker compose version
    ```

### Linux (Ubuntu/Debian como ejemplo)

1. Instala Docker:
    ```bash
    sudo apt-get update
    sudo apt-get install -y ca-certificates curl gnupg lsb-release
    sudo mkdir -p /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg]    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"    | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    sudo apt-get update
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    ```
2. Verifica la instalación:
    ```bash
    docker --version
    docker compose version
    ```

### macOS

1. Descarga e instala **Docker Desktop para Mac** desde: [https://www.docker.com/products/docker-desktop/](https://www.docker.com/products/docker-desktop/)  
   (Soporta tanto Intel como Apple Silicon).
2. Verifica la instalación en la terminal:
    ```bash
    docker --version
    docker compose version
    ```

---

## Estructura del directorio

```
infra-app-taxi
├── docker-compose.mysql.yml   # Servicio MySQL (development)
├── docker-compose.redis.yml   # Servicio Redis (development)
├── docker-compose.http.yml    # API HTTP (construye ../backend-app-taxi)
├── .env                       # Variables (local, no se versiona)
└── README.md
```

---

## Requisitos

| Requisito          | Versión                                                                                                     |
| ------------------ | ----------------------------------------------------------------------------------------------------------- |
| Docker Engine      | 24 o superior (recomendado la versión vigente). Validado con **28.4.0**; el mínimo de 24 no se probó.       |
| Docker Compose     | **v2** (plugin `docker compose`), validado con **v2.39.4**. Compose v1 (`docker-compose`) está descontinuado y aquí no se usa. |
| Red externa        | Compartida (p. ej. `network-app-taxi`) para vincular servicios entre archivos Compose                       |

Los archivos Compose ya no llevan `version:` (es obsoleto y Compose v2 lo avisa).

### Imágenes

| Servicio | Imagen              | Antes           | Motivo                                                                                                       |
| -------- | ------------------- | --------------- | ------------------------------------------------------------------------------------------------------------ |
| MySQL    | `mysql:9.7`         | `mysql:8.0.35`  | 8.0 llegó a fin de vida (2026-04-30). 9.7 es LTS con soporte hasta 2034-04 (8.4 LTS llega a 2032-04).       |
| Redis    | `redis:8.10-alpine` | `redis:8.2-alpine` | Última serie 8.x soportada (8.10.2 al validar).                                                          |
| Backend  | `node:24-alpine` + pnpm 12.5.1 | `node:22-alpine` + pnpm 9 | Node 24 es LTS activo (fin de vida 2028-04). Ver `../backend-app-taxi/Dockerfile`.              |

MySQL 9.7 se validó con TypeORM 1.1 / `mysql2` 3.24: autentica `root` con `caching_sha2_password`, la migración corre y el esquema queda sin diferencias respecto a las entidades.

---

## Variables de entorno

Ejemplo **.env** (recortado a lo esencial para infra). Adecúa nombres/credenciales a tu entorno. Ejecuta siempre los comandos desde este directorio: Compose lee el `.env` de aquí.

```env
PROJECT_NAME="app-taxi"
NETWORK="network-app-taxi"
TZ="America/Lima"

# Mysql
MYSQL_CONTAINER_NAME="mysql-app-taxi"
MYSQL_ROOT_PASSWORD="root"
MYSQL_DATABASE="db_app_taxi"
MYSQL_VOLUME="mysql_volume"

# Redis
REDIS_CONTAINER_NAME="redis-app-taxi"
REDIS_PORT="6379"
REDIS_VOLUME="redis_volume"

# Http
BACKEND_IMAGE="app-taxi-backend"
BACKEND_TAG="local"
HTTP_CONTAINER_NAME="http-app-taxi"
HTTP_DOCKER_PLATFORM="linux/amd64"
HTTP_NODE_ENV="development"
HTTP_NODE_DEBUG="false"
HTTP_DB_POOL="10"
HTTP_APPLICATION_PORT="3001"
HTTP_JWT_ACCESS_TTL_SEC="900"
HTTP_JWT_REFRESH_TTL_SEC="2592000"
HTTP_JWT_ACCESS_SECRET="Key@Access@Secret."
HTTP_JWT_REFRESH_SECRET="Key@Refresh@Secret."
```

| Variable                                 | La usa                | Notas                                                                                                  |
| ---------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------ |
| `NETWORK`                                | los 3 archivos        | Nombre de la red externa (debe existir)                                                                |
| `MYSQL_*`                                | mysql, http           | El healthcheck usa `MYSQL_ROOT_PASSWORD` del entorno del contenedor                                    |
| `REDIS_*`                                | redis, http           | El backend de esta clase no usa Redis todavía (`CacheModule` no está importado)                        |
| `BACKEND_IMAGE`, `BACKEND_TAG`           | http                  | **Obligatorias**: nombre y tag con el que se construye y ejecuta la imagen (`image: ${BACKEND_IMAGE}:${BACKEND_TAG}`) |
| `HTTP_DOCKER_PLATFORM`                   | http                  | `linux/amd64` funciona en Apple Silicon por emulación (validado); `linux/arm64` es nativo y más rápido (validado) |
| `HTTP_NODE_DEBUG`                        | http                  | Déjala en `"false"`: con `"true"` la imagen no arranca (`pino-pretty` no está en la imagen de producción) |

---

## Red compartida (una sola vez)

Crea la red externa (si no existe). Todas las composiciones la referencian como `external: true`:

```bash
docker network create network-app-taxi
```

> Puedes verificar con `docker network ls`. Si la red ya existe, este comando fallará inofensivamente.

---

## Orden recomendado de arranque

> **Importante**: El servicio **HTTP** espera a MySQL, ejecuta **migraciones** y **seeders** automáticamente antes de iniciar (entrypoint). Asegúrate de que MySQL esté **arriba** antes de levantar HTTP.

Como los tres archivos comparten `-p app-taxi`, Compose mostrará `Found orphan containers` al levantar el segundo y el tercero. Es esperado: **no** uses `--remove-orphans`, porque borraría los otros servicios.

### 1) MySQL

```bash
docker compose -f docker-compose.mysql.yml -p app-taxi up -d
docker compose -f docker-compose.mysql.yml -p app-taxi ps   # espera "healthy"
```

### 2) Redis

```bash
docker compose -f docker-compose.redis.yml -p app-taxi up -d
```

### 3) HTTP (API)

```bash
docker compose -f docker-compose.http.yml -p app-taxi up -d --build
docker compose -f docker-compose.http.yml -p app-taxi logs -f http
```

Deberías ver `Migrations OK`, `Seeders OK` y `Nest application successfully started`.

### Verificación

```bash
# Swagger
open http://localhost:3001/api/docs

# Login con un teléfono sembrado (ver ../backend-app-taxi/src/core/database/seeders/data/passengers.json)
curl -s -X POST http://localhost:3001/passenger/login \
  -H 'content-type: application/json' \
  -d '{"phone":"<telefono-sembrado>"}'
```

La respuesta esperada es `200` con `accessToken`, `refreshToken` y `user` (ver el README del backend).

### Apagar

```bash
docker compose -f docker-compose.http.yml -p app-taxi down
docker compose -f docker-compose.redis.yml -p app-taxi down
docker compose -f docker-compose.mysql.yml -p app-taxi down      # conserva el volumen
```

---

## Aviso de migración: volumen creado con MySQL 8.0.35

Si ya tenías el volumen `MYSQL_VOLUME` (por defecto `mysql_volume`) creado con `mysql:8.0.35`, **MySQL 9.7 no puede arrancarlo**. El contenedor sale con código 1 y este error (reproducido con un volumen desechable):

```
[ERROR] [MY-014060] [Server] Invalid MySQL server upgrade: Cannot upgrade from 80035 to 90702. Upgrade to next major version is only allowed from the last LTS release, which version 80035 is not.
[ERROR] [MY-010020] [Server] Data Dictionary initialization failed.
```

El volumen no se modifica al fallar. Tienes dos caminos:

### Opción A) Reiniciar el volumen (recomendada para el curso)

Los datos son solo los seeds, y se vuelven a crear al levantar HTTP. **Borra el volumen y todos sus datos**:

```bash
docker compose -f docker-compose.http.yml -p app-taxi down
docker compose -f docker-compose.mysql.yml -p app-taxi down
docker volume rm mysql_volume                     # usa el valor de MYSQL_VOLUME
docker compose -f docker-compose.mysql.yml -p app-taxi up -d
docker compose -f docker-compose.mysql.yml -p app-taxi ps   # espera "healthy"
docker compose -f docker-compose.http.yml -p app-taxi up -d # migra y siembra de nuevo
```

### Opción B) Conservar los datos (upgrade en dos saltos, irreversible)

8.0 → 9.x directo no está soportado: hay que pasar por **8.4 LTS** y luego a 9.7. Cada salto actualiza el volumen **en el lugar y no se puede revertir**, así que haz un respaldo antes:

```bash
docker compose -f docker-compose.http.yml -p app-taxi down
docker compose -f docker-compose.mysql.yml -p app-taxi down
docker run --rm -v mysql_volume:/data:ro -v "$PWD":/backup alpine tar czf /backup/mysql_volume.tgz -C /data .
```

1. En `docker-compose.mysql.yml` cambia temporalmente `image: mysql:9.7` por `image: mysql:8.4`.
2. `docker compose -f docker-compose.mysql.yml -p app-taxi up -d` y espera `healthy` (el log muestra `Server upgrade from '80035' to '80411' completed`).
3. `docker compose -f docker-compose.mysql.yml -p app-taxi down`.
4. Restaura `image: mysql:9.7` y vuelve a levantar (el log muestra `Server upgrade from '80411' to '90702' completed`).

Este camino se validó con un volumen desechable: los datos sobrevivieron a ambos saltos.

> Un volumen ya creado con `mysql:9.7` puede volver a arrancarse con `mysql:9.7` sin problema; no se puede volver a 8.0.

---

## Puertos por servicio (host → container)

| Servicio     | Host                          | Container |
| ------------ | ----------------------------- | --------: |
| Backend HTTP | 3001 (`HTTP_APPLICATION_PORT`) |      3001 |
| DB MySQL     | 3306                          |      3306 |
| Cache Redis  | 6379 (`REDIS_PORT`)           |      6379 |

Los puertos se publican en todas las interfaces del host. En una máquina compartida, antepón `127.0.0.1:` en los `ports:` de cada archivo (p. ej. `"127.0.0.1:3306:3306"`).
