# language-service

Parte de **Snippet Searcher** (Ingeniería de Sistemas 2026). Recibe **código fuente + lenguaje + versión** y devuelve un resultado: valida, ejecuta, formatea, lintea y corre tests.

Hoy el único lenguaje es PrintScript, a través de la librería [`Printscript2026`](https://github.com/Ingsis-2026/Printscript2026). La consigna avisa que no va a ser el único, y por eso el servicio no se llama "printscript-service".

## Reglas de diseño

- **No sabe qué es un snippet.** Su API y sus DTOs hablan de código, no de snippets (nada de `SnippetController` ni `SnippetDTO`).
- **No guarda estado ni sabe de usuarios.** Las reglas de lint y formato llegan en cada llamada; las guarda `snippet-service`.
- **Consume los Redis Streams** que publica `snippet-service` (formateo, linteo y tests automáticos) y le devuelve los resultados. No publica en su propio stream ni expone endpoints `/redis/*`.
- No se expone hacia afuera: solo lo llama `snippet-service` dentro de la red interna.

## Pendiente de decidir

Las US9 y US10 piden ver la salida a medida que se imprime y dar inputs a medida que se piden. Eso necesita SSE o WebSocket entre la UI, `snippet-service` y este servicio. Hay que decidir el borde antes de implementarlas.

## Correr en local

La librería está en GitHub Packages, que pide autenticarse aunque el repo sea público. Hay que agregar a `~/.gradle/gradle.properties` un token con el scope `read:packages`:

```properties
gpr.user=<tu usuario de GitHub>
gpr.key=<token con read:packages>
```

```sh
./gradlew bootRun   # levanta en :8082
```

| Variable | Default |
|---|---|
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `SERVER_PORT` | `8082` (en Docker, `8080`) |

## Calidad y CI/CD

Igual que el resto de los servicios: `./gradlew check` (ktlint, tests y 80% de cobertura). La imagen se publica en `ghcr.io/ingsis-2026/language-service`. En CI y en el build de Docker, la librería se baja con el `GITHUB_TOKEN` del workflow.
