package com.ingsis.language.languages

import com.ingsis.language.validation.CodeError
import diagnostics.PrintScriptException
import lexer.Lexer
import lexer.TokenMapper
import org.springframework.stereotype.Component
import parser.Parser
import version.Version

@Component
class PrintScriptRunner : LanguageRunner {
    override val language = "PrintScript"

    override fun supportsVersion(version: String): Boolean = Version.of(version) != null

    /**
     * Recorrer todos los nodos alcanza para validar: el Lexer y el Parser fallan sobre la primera
     * sentencia que no pueden resolver, así que hay a lo sumo un error.
     */
    override fun validate(
        code: String,
        version: String,
    ): List<CodeError> {
        val parsed = Version.parse(version)
        return try {
            val tokens = Lexer(TokenMapper(parsed)).convertToTokens(code.lineSequence())
            Parser.forVersion(parsed).execute(tokens).count()
            emptyList()
        } catch (e: PrintScriptException) {
            listOf(e.toCodeError())
        }
    }

    // Las posiciones de la librería son 0-based; al usuario se le muestran 1-based.
    private fun PrintScriptException.toCodeError(): CodeError =
        CodeError(
            rule = message ?: "Error de sintaxis",
            line = startPosition?.row?.plus(1),
            column = startPosition?.column?.plus(1),
        )
}
