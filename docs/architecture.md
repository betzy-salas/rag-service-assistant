# Arquitectura de assistant

Diagramas del diseño actual: C4 (contexto, contenedores y componentes), secuencia de una
pregunta y de los errores, y clases. Están en Mermaid, así que se ven directamente en GitHub o
GitLab.

## C4 nivel 1: contexto

```mermaid
flowchart LR
  user["👤 Usuario<br/><small>Hace preguntas sobre la documentación</small>"]
  service["<b>assistant</b><br/><small>Spring Boot · Java 21<br/>Responde preguntas con los chunks de Confluence</small>"]
  openai["OpenAI<br/><small>Modelo de chat y de embeddings</small>"]
  db[("PostgreSQL + pgvector<br/><small>rag-service-db</small>")]
  consul["Consul<br/><small>Configuración y secretos</small>"]
  ingestion["confluence-ingestion-service<br/><small>Crea los chunks</small>"]
  front["Cliente web<br/><small>rag-service-front · localhost:5173</small>"]

  user -- "Navegador" --> front
  front -- "HTTP: POST /questions<br/>(CORS: app.cors.allowed-origins)" --> service
  service -- "Reescribe la pregunta, genera<br/>el embedding y redacta la respuesta" --> openai
  service -- "Busca chunks por similitud" --> db
  service -- "Lee config/assistant/" --> consul
  ingestion -- "Guarda chunks con embeddings" --> db
  ingestion -- "Lee config/assistant/" --> consul
```

## C4 nivel 2: contenedores

```mermaid
flowchart LR
  user["👤 Usuario"]
  front["Cliente web<br/><small>localhost:5173</small>"]

  subgraph svc["assistant · puerto 8081"]
    api["API REST<br/><small>Spring MVC · Validation · CORS</small>"]
    console["Consola de preguntas<br/><small>/index.html (mismo origen)</small>"]
    core["Pipeline RAG<br/><small>Spring AI: ChatClient + RetrievalAugmentationAdvisor</small>"]
  end

  subgraph pg["PostgreSQL + pgvector · rag-service-db"]
    chunk[("confluence_chunk<br/><small>content · metadata jsonb · embedding vector(3072)</small>")]
  end

  consul["Consul KV<br/><small>config/assistant/*</small>"]
  openai["OpenAI"]

  user --> front -- "CORS" --> api
  user --> console --> api
  api --> core
  core -- "ChatModel<br/>(reescritura y respuesta)" --> openai
  core -- "EmbeddingModel" --> openai
  core -- "VectorStore (solo lectura)" --> chunk
  svc -. "configuración al arrancar" .-> consul
```

El servicio no crea ni modifica tablas: `confluence_chunk` la gestiona el servicio de ingesta con
Liquibase, y `spring.ai.vectorstore.pgvector.initialize-schema` está a `false` en Consul.

## C4 nivel 3: componentes

```mermaid
flowchart TB
  subgraph controller["controller"]
    questionController["QuestionController<br/><small>POST /questions</small>"]
    handler["GlobalExceptionHandler<br/><small>errores → ProblemDetail</small>"]
  end

  subgraph service["service"]
    answerService["AnswerService<br/><small>pregunta → respuesta + fuentes</small>"]
    sourceMapper["SourceMapper<br/><small>chunks → páginas sin repetir</small>"]
  end

  subgraph config["config"]
    chatClientConfig["ChatClientConfig<br/><small>monta el pipeline RAG</small>"]
    questionAugmenter["QuestionAwareQueryAugmenter<br/><small>no-context.st con la pregunta</small>"]
    ragProperties["RagProperties<br/><small>app.rag.top-k · similarity-threshold</small>"]
    corsConfig["CorsConfig<br/><small>CORS de POST /questions</small>"]
    corsProperties["CorsProperties<br/><small>app.cors.allowed-origins</small>"]
    prompts["prompts/*.st<br/><small>system · rewrite · rag · no-context</small>"]
  end

  subgraph springai["Spring AI"]
    chatClient["ChatClient"]
    advisor["RetrievalAugmentationAdvisor"]
    expander["QueryExpander<br/><small>original + reescrita</small>"]
    rewriter["RewriteQueryTransformer"]
    retriever["VectorStoreDocumentRetriever"]
    joiner["ConcatenationDocumentJoiner<br/><small>+ recorte a top-k</small>"]
    augmenter["ContextualQueryAugmenter"]
    vectorStore["VectorStore<br/><small>PgVectorStore</small>"]
  end

  questionController --> answerService
  answerService --> chatClient
  answerService --> sourceMapper
  chatClientConfig --> ragProperties
  corsConfig --> corsProperties
  corsConfig -. "orígenes permitidos" .-> questionController
  chatClientConfig --> prompts
  chatClientConfig -- "construye" --> chatClient
  chatClient --> advisor
  advisor --> expander
  expander --> rewriter
  advisor --> retriever
  advisor --> joiner
  advisor --> questionAugmenter
  questionAugmenter -- "con chunks delega en" --> augmenter
  retriever --> vectorStore
```

`AnswerService` no busca ni construye prompts: eso lo hace el advisor dentro del `ChatClient`. El
service solo aporta el filtro por `spaceKey` por petición y lee del contexto de la respuesta los
chunks que el advisor recuperó, para convertirlos en fuentes. El expander busca con la pregunta
original y con la reescrita, porque la reescritura puede cambiar el sentido de una palabra ambigua;
el joiner une ambos resultados sin repetir y por score, y un post-procesador los recorta a `top-k`.
`QuestionAwareQueryAugmenter` conserva la pregunta en el prompt sin contexto, para que el modelo
responda en su idioma.

## Secuencia: pregunta

`POST /questions` con `{"question": "...", "spaceKey": "DS"}`. El `spaceKey` es opcional.

```mermaid
sequenceDiagram
  actor U as Usuario
  participant C as QuestionController
  participant S as AnswerService
  participant CC as ChatClient
  participant A as RetrievalAugmentationAdvisor
  participant VS as VectorStore
  participant AI as OpenAI
  participant M as SourceMapper

  U->>C: POST /questions {question, spaceKey}
  C->>C: valida el body (@Valid)
  C->>S: answer(question, spaceKey)
  S->>CC: prompt().user(question).advisors(filtro spaceKey).call()
  CC->>A: before(request)
  A->>AI: chat: reescribe la pregunta (rewrite.st)
  AI-->>A: pregunta reescrita
  loop original y reescrita (solo la original si coinciden)
    A->>VS: similaritySearch(pregunta, topK, umbral, filtro)
    VS->>AI: embedding de la pregunta
    AI-->>VS: vector
    VS-->>A: chunks con score
  end
  A->>A: une sin repetir, ordena por score, recorta a topK
  alt hay chunks
    A->>A: prompt rag.st con los chunks y la pregunta original
  else sin chunks
    A->>A: prompt no-context.st con la pregunta original
  end
  A->>AI: chat: system.st + prompt (gpt-4o-mini)
  AI-->>A: respuesta
  A-->>CC: respuesta + chunks en el contexto (DOCUMENT_CONTEXT)
  CC-->>S: ChatClientResponse
  S->>M: toSources(chunks)
  M-->>S: páginas sin repetir, por score
  S-->>C: Answer(text, sources)
  C-->>U: 200 {"answer": "...", "sources": [{"title", "url"}]}
```

Cada pregunta hace cuatro llamadas a OpenAI: reescritura, dos embeddings y respuesta; tres si la
reescritura no cambia la pregunta. La pregunta reescrita solo se usa para buscar; el modelo responde
a la pregunta original.

## Secuencia: errores

```mermaid
sequenceDiagram
  actor U as Usuario
  participant C as QuestionController
  participant S as AnswerService
  participant H as GlobalExceptionHandler

  U->>C: POST /questions
  alt question vacía, demasiado larga o body inválido
    C->>H: MethodArgumentNotValidException / HttpMessageNotReadableException
    H-->>U: 400 ProblemDetail
  else
    C->>S: answer(question, spaceKey)
    S--xC: excepción
    C->>H: excepción
    alt Error de OpenAI (OpenAIException)
      H-->>U: 502 "Could not generate the answer with OpenAI"
    else Cualquier otro
      H-->>U: 500 "Internal service error"
    end
  end
```

## Diagrama de clases

```mermaid
classDiagram
  direction LR

  class QuestionController {
    +ask(QuestionRequest) ResponseEntity~AnswerResponse~
  }
  class GlobalExceptionHandler {
    <<RestControllerAdvice>>
  }

  class AnswerService {
    +answer(question, spaceKey) Answer
  }
  class SourceMapper {
    <<Mapper>>
    +toSources(List~Document~) List~Source~
    +toSource(Document) Source
  }

  class ChatClientConfig {
    <<Configuration>>
    +chatClient(builder, vectorStore, properties, prompts) ChatClient
  }
  class CorsConfig {
    <<Configuration>>
    +addCorsMappings(CorsRegistry)
  }
  class CorsProperties {
    <<record>>
    PREFIX$ = "app.cors"
    allowedOrigins
  }
  class RagProperties {
    <<record>>
    PREFIX$ = "app.rag"
    topK
    similarityThreshold
  }
  class ChatClient {
    <<Spring AI>>
  }
  class QuestionAwareQueryAugmenter {
    <<record>>
    delegate QueryAugmenter
    noContextPrompt PromptTemplate
    +augment(Query, List~Document~) Query
  }
  class RetrievalAugmentationAdvisor {
    <<Spring AI>>
    QueryExpander
    RewriteQueryTransformer
    VectorStoreDocumentRetriever
    DocumentPostProcessor
  }
  class VectorStore {
    <<Spring AI>>
  }

  class QuestionRequest {
    <<record>>
    question
    spaceKey
  }
  class Answer {
    <<record>>
    text
    sources
  }
  class Source {
    <<record>>
    title
    url
  }
  class AnswerResponse {
    <<record>>
    answer
    sources
  }

  QuestionController --> AnswerService
  QuestionController ..> QuestionRequest
  QuestionController ..> AnswerResponse
  AnswerService --> ChatClient
  AnswerService --> SourceMapper
  AnswerService ..> Answer
  SourceMapper ..> Source
  Answer o-- Source
  ChatClientConfig --> RagProperties
  CorsConfig --> CorsProperties
  CorsConfig ..> QuestionController : CORS de /questions
  ChatClientConfig ..> ChatClient
  ChatClientConfig ..> QuestionAwareQueryAugmenter
  ChatClient --> RetrievalAugmentationAdvisor
  RetrievalAugmentationAdvisor --> QuestionAwareQueryAugmenter
  RetrievalAugmentationAdvisor --> VectorStore
```

Para no sobrecargar el diagrama se omiten `AnswerResponseMapper`, `SourceResponse` y las clases de
constantes (`ApiPaths`, `PromptPaths`, `ChunkMetadata`, `ErrorMessages`).
