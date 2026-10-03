package com.ingsis.language.languages

import org.springframework.stereotype.Component

/** Elige el [LanguageRunner] que corresponde a cada pedido. */
@Component
class LanguageRegistry(
    private val runners: List<LanguageRunner>,
) {
    fun runnerFor(
        language: String,
        version: String,
    ): LanguageRunner {
        val runner =
            runners.find { it.language.equals(language, ignoreCase = true) }
                ?: throw UnsupportedLanguageException("Lenguaje no soportado: $language")
        if (!runner.supportsVersion(version)) {
            throw UnsupportedLanguageException("Versión no soportada de ${runner.language}: $version")
        }
        return runner
    }
}

class UnsupportedLanguageException(
    message: String,
) : RuntimeException(message)
