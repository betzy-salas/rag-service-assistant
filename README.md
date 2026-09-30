# assistant

Servicio de consulta RAG. Recibe una pregunta, busca en **PostgreSQL + pgvector** los fragmentos
(chunks) de páginas de Confluence más parecidos, se los pasa como contexto a un modelo de chat de
**OpenAI** con **Spring AI** y devuelve la respuesta junto con las páginas de origen.

Los chunks los crea el servicio de ingesta (`confluence-ingestion-service`) en la tabla
`confluence_chunk`. Este servicio solo la lee.

Es una prueba de concepto: cada pregunta se responde de forma independiente, sin memoria de
conversación ni autenticación.

## Qué hace

| Operación | Endpoint | Descripción |
|---|---|---|
| Preguntar | `POST /questions` | Responde una pregunta con la documentación indexada y cita las páginas usadas. |

Responder una pregunta significa:
1. Reescribir la pregunta con el modelo de chat: corrige erratas y la hace más específica sin
   añadir información ni cambiar palabras por sinónimos. Las preguntas cortas o con erratas no
   llegaban al umbral de similitud.
2. Buscar con las dos preguntas, la original y la reescrita: se genera el embedding de cada una con
   OpenAI y se recuperan de `confluence_chunk` los `app.rag.top-k` chunks más similares con
   similitud mayor o igual a `app.rag.similarity-threshold`, opcionalmente solo los de un espacio.
   La reescritura puede cambiar el sentido de un verbo ambiguo ("roto las credenciales" leído como
   "restauro"), así que solo puede añadir chunks, nunca perder los que encuentra la original.
3. Unir los resultados sin repetir chunks, ordenados por similitud y recortados a `app.rag.top-k`.
4. Construir el prompt con esos chunks como contexto (`src/main/resources/prompts/`) y pedir la
   respuesta al modelo de chat. Si no hay chunks, el modelo recibe un prompt que le indica decir
   que la documentación no cubre la pregunta; ese prompt incluye la pregunta para que responda en su
   idioma.
5. Devolver la respuesta y, como fuentes, el `title` y la `url` de cada página recuperada, sin
   repetir páginas y con la más similar primero.

Todo el pipeline lo hace el `RetrievalAugmentationAdvisor` de Spring AI (`spring-ai-rag`), configurado
en `ChatClientConfig`.

Los diagramas C4, de secuencia y de clases están en [docs/architecture.md](docs/architecture.md).

## Tecnologías

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.1.1 (Spring MVC, Validation, Actuator) |
| IA | Spring AI 2.0.1: OpenAI (chat y embeddings), PgVectorStore, módulo RAG |
| Base de datos | PostgreSQL 17 + pgvector (esquema gestionado por el servicio de ingesta) |
| Configuración | Spring Cloud Consul 2025.1.3 |
| Utilidades | Lombok, MapStruct 1.6.3 |
| Tests | JUnit 5, Mockito, MockMvc, Testcontainers |

## Requisitos

- Java 21. Maven no hace falta: el proyecto incluye Maven Wrapper (`./mvnw`).
- PostgreSQL con pgvector y la base de datos `rag-service-db` ya poblada por el servicio de ingesta.
- Consul con la configuración del servicio (ver abajo).
- Docker, solo para ejecutar los tests.

## Configuración

El servicio lee su configuración y sus secretos de Consul, en las claves de `config/assistant/`,
que comparte con el servicio de ingesta. `application.properties` solo contiene lo que no depende
del entorno.

Claves que debe tener Consul:

| Clave | Ejemplo | Para qué |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5433/rag-service-db` | Base de datos |
| `spring.datasource.username` / `password` | | Credenciales de la base de datos |
| `spring.ai.openai.api-key` | | API key de OpenAI |
| `spring.ai.openai.chat.options.model` | `gpt-4o-mini` | Modelo de chat que redacta la respuesta |
| `spring.ai.openai.embedding.options.model` | `text-embedding-3-large` | Modelo de embeddings. Debe ser el mismo que usó la ingesta |
| `spring.ai.vectorstore.pgvector.table-name` | `confluence_chunk` | Tabla de los chunks |
| `spring.ai.vectorstore.pgvector.initialize-schema` | `false` | La tabla la crea la ingesta con Liquibase |
| `spring.ai.vectorstore.pgvector.dimensions` | `3072` | Debe coincidir con la columna `embedding` |
| `spring.ai.vectorstore.pgvector.distance-type` | `COSINE_DISTANCE` | |
| `spring.ai.vectorstore.pgvector.index-type` | `NONE` | pgvector no indexa más de 2000 dimensiones |
| `app.rag.top-k` | `5` | Chunks que se pasan como contexto |
| `app.rag.similarity-threshold` | `0.5` | Similitud mínima (0 a 1) para usar un chunk |

> Si Consul no tiene `app.rag.*`, el servicio usa `5` y `0.5`.

Opcional, para el cliente web (`../rag-service-front`):

| Clave | Valor por defecto | Para qué |
|---|---|---|
| `app.cors.allowed-origins` | `http://localhost:5173` | Orígenes del navegador que pueden llamar a `POST /questions` y a `/actuator/health`. Lista separada por comas. |

Opcional: `spring.cloud.consul.config.watch.enabled=false` evita los `ReadTimeoutException` que el
watcher de Consul escribe en el log. Los cambios en Consul se aplican entonces al reiniciar.

## Arrancar el servicio

```bash
./mvnw spring-boot:run
```

Escucha en el puerto **8081**. Para comprobar que está conectado a PostgreSQL y a Consul:

```bash
curl -s localhost:8081/actuator/health
```

## Métricas

El servicio expone `/actuator/prometheus` (formato Prometheus, vía Micrometer) y `/actuator/metrics`.
El Prometheus y el Grafana del proyecto (`docker-compose.yml` en `spec-development-driven/rag-service`)
recogen esas métricas cada 15 s; Grafana está en `http://localhost:3000` (usuario `admin`, contraseña
`admin`) con el panel "JVM (Micrometer)" provisionado.

Para consultar un valor a mano:

```bash
curl -s localhost:8081/actuator/metrics/http.server.requests
```

## Uso

```bash
curl -s -X POST localhost:8081/questions \
  -H 'Content-Type: application/json' \
  -d '{"question":"¿Cómo se despliega el servicio?","spaceKey":"DS"}'
```

```json
{
  "answer": "El despliegue se hace con Jenkins...",
  "sources": [
    {"title": "Guía de despliegue", "url": "https://sitio.atlassian.net/wiki/spaces/DS/pages/458864"}
  ]
}
```

- `question`: obligatoria, hasta 2000 caracteres.
- `spaceKey`: opcional. Si se envía, solo se usan chunks de ese espacio de Confluence.

Cuando ningún chunk supera el umbral, la respuesta indica que la documentación no cubre la pregunta
y `sources` es `[]`.

Para probar desde el navegador sin otro proyecto, el servicio sirve una consola de preguntas en
`http://localhost:8081/index.html` (`src/main/resources/static/index.html`): envía la pregunta con
el espacio opcional y muestra la respuesta, las fuentes enlazadas y el tiempo de respuesta. Al estar
en el mismo origen que la API no necesita CORS.

### CORS

Un cliente web servido desde otro origen, como `rag-service-front` en `http://localhost:5173`,
solo puede llamar a la API si su origen está en `app.cors.allowed-origins` (Consul, lista separada
por comas). Se permite `POST /questions` y el `GET` de Actuator; cualquier otro origen recibe un 403
en el preflight y el navegador no envía la petición. CORS solo afecta a los navegadores: curl,
Postman u otros servicios pueden llamar a la API sin restricciones.

### Errores

Los errores siguen el formato `ProblemDetail` (RFC 9457, `application/problem+json`):

```json
{
  "type": "about:blank",
  "title": "Bad Gateway",
  "status": 502,
  "detail": "Could not generate the answer with OpenAI",
  "instance": "/questions"
}
```

| Status | Cuándo | `detail` |
|---|---|---|
| 400 | `question` vacía o demasiado larga, o body inválido | Mensaje de Spring MVC |
| 502 | Falla OpenAI (embeddings o chat) | `Could not generate the answer with OpenAI` |
| 500 | Cualquier otro error | `Internal service error` |

El detalle técnico de los errores 502 y 500 se escribe en el log, nunca en la respuesta.

## Tests

```bash
./mvnw test
```

Necesita Docker: el test de integración arranca un PostgreSQL con pgvector temporal (Testcontainers).
Los tests usan embeddings falsos y un modelo de chat simulado, así que no llaman a OpenAI.

| Test | Tipo | Qué cubre |
|---|---|---|
| `AnswerServiceTest` | Unitario con el pipeline RAG real sobre un `VectorStore` simulado | Búsqueda con la pregunta original y la reescrita, unión de resultados hasta `top-k`, contexto en el prompt, fuentes sin repetir y ordenadas, prompt sin contexto con la pregunta, umbral y filtro por espacio |
| `AnswerIntegrationTest` | Integración (PostgreSQL real) | Chunks con metadata JSONB, filtro por `spaceKey` en pgvector, fuentes |
| `QuestionControllerTest` | `@WebMvcTest` | Respuesta 200, todos los errores y el preflight CORS |

## Estructura del código

```
com.rag.assistant
├── config/        Propiedades app.rag.* y app.cors.*, rutas de los prompts, CORS, construcción del ChatClient con el advisor RAG y el augmenter sin contexto
├── controller/    Endpoint, DTOs con validación, mapper y manejo de errores
├── exception/     Mensajes de error
└── service/       Servicio de respuesta, modelos y mapper de chunks a fuentes
```

Los prompts están en `src/main/resources/prompts/`: `system.st` (rol del asistente), `rewrite.st`
(reescritura de la pregunta), `rag.st` (pregunta con contexto) y `no-context.st` (sin chunks
relevantes).

Los comentarios del código siguen [docs/comment-guidelines.md](docs/comment-guidelines.md).

## Limitaciones conocidas

- **Sin memoria de conversación**: cada pregunta se responde sin tener en cuenta las anteriores.
- **Sin autenticación**: el endpoint está abierto. CORS solo limita qué orígenes del navegador pueden llamarlo.
- La respuesta es **síncrona**: el cliente espera a que el modelo termine. Cada pregunta hace cuatro
  llamadas a OpenAI (reescritura, dos embeddings y respuesta), tres si la reescritura no cambia la
  pregunta.
- El endpoint `POST /questions` es provisional: no tiene versión en la ruta ni contrato OpenAPI.
