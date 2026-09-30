# language-service

Parte de **Snippet Searcher** (Ingeniería de Sistemas 2026). Recibe **código fuente + lenguaje + versión** y devuelve un resultado: valida, ejecuta, formatea, lintea y corre tests.

Hoy el único lenguaje es PrintScript, a través de la librería [`Printscript2026`](https://github.com/Ingsis-2026/Printscript2026). La consigna avisa que no va a ser el único, y por eso el servicio no se llama "printscript-service".

## Reglas de diseño

- **No sabe qué es un snippet.** Su API y sus DTOs hablan de código, no de snippets (nada de `SnippetController` ni `SnippetDTO`).
- **No guarda estado ni sabe de usuarios.** Las reglas de lint y formato llegan en cada llamada; las guarda `snippet-service`.
- Solo lo llama `snippet-service`.

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
| `SERVER_PORT` | `8082` (en Docker, `8080`) |

## Calidad y CI/CD

Igual que el resto de los servicios: `./gradlew check` (ktlint, tests y 80% de cobertura). La imagen se publica en `ghcr.io/ingsis-2026/language-service`. En CI y en el build de Docker, la librería se baja con el `GITHUB_TOKEN` del workflow.
