package com.ingsis.language

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US9 y US10: ejecutar por WebSocket, con los outputs a medida que salen y los inputs cuando se piden. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = ["execution.input-timeout=1s"])
@Import(TestJwtDecoder::class)
class ExecutionSocketTest(
    @LocalServerPort private val port: Int,
) {
    private val json = JsonMapper.builder().build()
    private val sessions = mutableListOf<WebSocketSession>()

    /**
     * Lo que llega del servicio, en orden; `closed` al final si cerró la conexión. Se abre con
     * [token] en `Authorization`, como snippet-service, o sin header si es null.
     */
    private inner class Connection(
        token: String? = TestJwtDecoder.TOKEN,
    ) {
        val received = LinkedBlockingQueue<JsonNode>()
        val session: WebSocketSession =
            StandardWebSocketClient()
                .execute(
                    object : TextWebSocketHandler() {
                        override fun handleTextMessage(
                            session: WebSocketSession,
                            message: TextMessage,
                        ) {
                            received.put(json.readTree(message.payload))
                        }

                        override fun afterConnectionClosed(
                            session: WebSocketSession,
                            status: CloseStatus,
                        ) {
                            received.put(json.readTree("""{"type": "closed", "code": ${status.code}}"""))
                        }
                    },
                    WebSocketHttpHeaders().apply { token?.let { setBearerAuth(it) } },
                    URI("ws://localhost:$port/execution"),
                ).get(5, TimeUnit.SECONDS)
                .also(sessions::add)

        fun send(message: Any) = session.sendMessage(TextMessage(json.writeValueAsString(message)))

        fun start(
            code: String,
            inputs: List<String> = emptyList(),
            interactive: Boolean = false,
            language: String = "printscript",
            version: String = "1.1",
        ) = send(
            mapOf(
                "type" to "start",
                "code" to code,
                "language" to language,
                "version" to version,
                "inputs" to inputs,
                "interactive" to interactive,
            ),
        )

        fun next(): JsonNode = received.poll(5, TimeUnit.SECONDS) ?: error("No llegó ningún mensaje")
    }

    @AfterEach
    fun close() = sessions.filter { it.isOpen }.forEach { it.close() }

    private fun JsonNode.type() = get("type").asString()

    @Test
    fun `sin token no se puede abrir el WebSocket`() {
        assertFailsWith<ExecutionException> { Connection(token = null) }
    }

    @Test
    fun `con un token que no es valido no se puede abrir el WebSocket`() {
        assertFailsWith<ExecutionException> { Connection(token = "otro-token") }
    }

    @Test
    fun `cada println sale como un output y al final llega done sin error`() {
        val connection = Connection()
        connection.start("println(\"uno\");\nprintln(\"dos\");")

        assertEquals("uno", connection.next().get("value").asString())
        assertEquals("dos", connection.next().get("value").asString())
        val done = connection.next()
        assertEquals("done", done.type())
        assertTrue(done.get("error").isNull)
        assertEquals(1000, connection.next().get("code").asInt())
    }

    @Test
    fun `interactiva pide cada input con su mensaje cuando el codigo lo necesita`() {
        val connection = Connection()
        val code =
            """
            println("Hola");
            let nombre: string = readInput("Nombre?");
            println("Chau " + nombre);
            """.trimIndent()
        connection.start(code, interactive = true)

        assertEquals("Hola", connection.next().get("value").asString())
        val request = connection.next()
        assertEquals("input-request", request.type())
        assertEquals("Nombre?", request.get("message").asString())

        connection.send(mapOf("type" to "input", "value" to "Ana"))

        val output = connection.next()
        assertEquals("output", output.type())
        assertEquals("Chau Ana", output.get("value").asString())
        assertEquals("done", connection.next().type())
    }

    @Test
    fun `los inputs que llegan con start se usan antes de pedir otros`() {
        val connection = Connection()
        connection.start(
            "let a: string = readInput(\"a\");\nlet b: string = readInput(\"b\");\nprintln(a + b);",
            inputs = listOf("1"),
            interactive = true,
        )

        assertEquals("b", connection.next().get("message").asString())
        connection.send(mapOf("type" to "input", "value" to "2"))
        assertEquals("12", connection.next().get("value").asString())
    }

    @Test
    fun `sin interactiva, un test que se queda sin inputs falla en el readInput como en POST run`() {
        val connection = Connection()
        connection.start("println(\"antes\");\nlet n: string = readInput(\"Nombre?\");")

        assertEquals("antes", connection.next().get("value").asString())
        val done = connection.next()
        assertEquals("done", done.type())
        assertEquals("No hay más inputs para \"Nombre?\"", done.get("error").get("rule").asString())
        assertEquals(2, done.get("error").get("line").asInt())
    }

    @Test
    fun `un error de ejecucion llega en done despues de lo impreso antes`() {
        val connection = Connection()
        connection.start("println(1);\nprintln(noExiste);")

        assertEquals("1", connection.next().get("value").asString())
        val done = connection.next()
        assertEquals(2, done.get("error").get("line").asInt())
    }

    @Test
    fun `si nadie manda el input pedido, la ejecucion se corta`() {
        val connection = Connection()
        connection.start("let n: string = readInput(\"?\");", interactive = true)

        assertEquals("input-request", connection.next().type())
        val done = connection.next()
        assertEquals("done", done.type())
        assertTrue(
            done
                .get("error")
                .get("rule")
                .asString()
                .startsWith("No llegó el input"),
        )
        assertTrue(done.get("error").get("line").isNull)
    }

    @Test
    fun `un codigo de mas de 8 KB entra en un solo mensaje`() {
        val connection = Connection()
        connection.start((1..1000).joinToString("") { "let x$it: number = 1;\n" } + "println(x1);")

        assertEquals("1", connection.next().get("value").asString())
        assertEquals("done", connection.next().type())
    }

    @Test
    fun `un lenguaje desconocido es un failure y se cierra`() {
        val connection = Connection()
        connection.start("x", language = "cobol")

        val failure = connection.next()
        assertEquals("failure", failure.type())
        assertEquals("Lenguaje no soportado: cobol", failure.get("message").asString())
        assertEquals(CloseStatus.POLICY_VIOLATION.code, connection.next().get("code").asInt())
    }

    @Test
    fun `los mensajes que no son del protocolo son un failure`() {
        listOf(
            "no es json",
            json.writeValueAsString(mapOf("type" to "otro")),
            json.writeValueAsString(mapOf("type" to "input")),
            json.writeValueAsString(mapOf("type" to "start", "language" to "printscript", "version" to "1.1")),
            json.writeValueAsString(mapOf("type" to "start", "code" to "x", "version" to "1.1")),
            json.writeValueAsString(mapOf("type" to "start", "code" to "x", "language" to "printscript")),
        ).forEach { payload ->
            val connection = Connection()
            connection.session.sendMessage(TextMessage(payload))
            assertEquals("failure", connection.next().type(), payload)
        }
    }

    @Test
    fun `un segundo start es un failure`() {
        val connection = Connection()
        connection.start("let n: string = readInput(\"?\");", interactive = true)
        assertEquals("input-request", connection.next().type())

        connection.start("println(1);")

        assertEquals("failure", connection.next().type())
    }

    @Test
    fun `si quien la pidio se va, la ejecucion que esperaba un input se corta`() {
        val connection = Connection()
        connection.start("let n: string = readInput(\"?\");", interactive = true)
        assertEquals("input-request", connection.next().type())

        connection.session.close()

        assertEquals("closed", connection.next().type())
        assertNull(connection.received.poll(1500, TimeUnit.MILLISECONDS))
    }
}
