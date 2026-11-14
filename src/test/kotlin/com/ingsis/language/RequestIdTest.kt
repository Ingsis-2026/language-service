package com.ingsis.language

import com.ingsis.language.languages.LanguageRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.logging.LogLevel
import org.springframework.boot.logging.LoggingSystem
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.assertTrue

/**
 * Los logs de cada pedido, con su `requestId` y su contexto, que en texto van entre corchetes. Los de un código
 * inválido están en DEBUG, así que acá se prende DEBUG para el servicio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension::class)
class RequestIdTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val loggingSystem: LoggingSystem,
) {
    @MockitoSpyBean
    private lateinit var registry: LanguageRegistry

    @BeforeEach
    fun debug() = loggingSystem.setLogLevel("com.ingsis.language", LogLevel.DEBUG)

    @AfterEach
    fun reset() = loggingSystem.setLogLevel("com.ingsis.language", null)

    private fun post(
        path: String,
        code: String,
    ) = mockMvc.post(path) {
        with(asSnippetService())
        header("X-Request-Id", "abc-123")
        contentType = MediaType.APPLICATION_JSON
        content = """{"code": "$code", "language": "printscript", "version": "1.1"}"""
    }

    private fun CapturedOutput.logLine(message: String) = out.lines().single { it.endsWith(message) }

    @Test
    fun `la respuesta lleva el id del pedido`() {
        post("/validate", "println(1);").andExpect { header { string("X-Request-Id", "abc-123") } }
    }

    @Test
    fun `un codigo invalido queda en DEBUG con cuantos errores tiene, sin el codigo`(output: CapturedOutput) {
        post("/validate", "let x: number = ;")

        val line = output.logLine("El código no es válido")
        listOf("requestId=abc-123", "language=printscript", "version=1.1", "errors=1").forEach { assertTrue(it in line, line) }
        assertTrue("let x" !in output.out)
    }

    @Test
    fun `un error inesperado queda en ERROR con el id del pedido y responde 500`(output: CapturedOutput) {
        Mockito.doThrow(IllegalStateException("se rompió")).`when`(registry).runnerFor(anyString(), anyString())

        post("/validate", "println(1);").andExpect { status { isInternalServerError() } }

        assertTrue("requestId=abc-123" in output.logLine("Falló un pedido por un error inesperado"))
    }
}
