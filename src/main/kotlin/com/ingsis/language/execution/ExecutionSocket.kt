package com.ingsis.language.execution

import com.ingsis.language.languages.ExecutionAbortedException
import com.ingsis.language.languages.ExecutionIO
import com.ingsis.language.languages.LanguageRegistry
import com.ingsis.language.languages.UnsupportedLanguageException
import com.ingsis.language.validation.CodeError
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Lo que manda quien pide la ejecución. El primer mensaje es `start` y es el único con el código;
 * después solo `input`, con el valor para el último `input-request`.
 *
 * Con [interactive] en false (los tests, US9) los inputs son solo [inputs]: si se acaban, la
 * ejecución falla como en `POST /run`. Con true (US10) se usan primero los de [inputs] y después
 * se le piden a quien la corre.
 */
data class ClientMessage(
    val type: String,
    val code: String? = null,
    val language: String? = null,
    val version: String? = null,
    val inputs: List<String> = emptyList(),
    val interactive: Boolean = false,
    val value: String? = null,
)

/**
 * Lo que manda el servicio, en orden: un `output` por cada línea impresa, apenas se imprime; un
 * `input-request` cada vez que el código necesita un input que no tiene; y al final `done`, con el
 * error que cortó la ejecución o null. `failure` es un pedido que no se pudo correr, como un
 * lenguaje que no existe. Después de `done` o `failure` el servicio cierra el WebSocket.
 */
sealed interface ServerMessage {
    val type: String
}

data class OutputMessage(
    val value: String,
) : ServerMessage {
    override val type = "output"
}

data class InputRequestMessage(
    val message: String,
) : ServerMessage {
    override val type = "input-request"
}

data class DoneMessage(
    val error: CodeError?,
) : ServerMessage {
    override val type = "done"
}

data class FailureMessage(
    val message: String,
) : ServerMessage {
    override val type = "failure"
}

/**
 * US9 y US10: ejecutar código viendo cada output a medida que se imprime y dando cada input
 * cuando se pide. Es un WebSocket porque los mensajes van en los dos sentidos mientras el código
 * corre: con un pedido HTTP, los inputs tendrían que estar todos antes de empezar.
 *
 * Solo lo usa snippet-service, que es quien sabe de usuarios y permisos; este servicio no se
 * publica. Cada conexión es una ejecución.
 */
@Component
class ExecutionHandler(
    private val registry: LanguageRegistry,
    private val jsonMapper: JsonMapper,
    @Value("\${execution.input-timeout:5m}") private val inputTimeout: Duration,
) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    // Un hilo virtual por ejecución: casi todo el tiempo están esperando un input, y eso no ocupa
    // un hilo del sistema operativo.
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val executions = ConcurrentHashMap<String, Execution>()

    private class Execution(
        val session: WebSocketSession,
    ) {
        val inputs = LinkedBlockingQueue<String>()

        @Volatile
        var running: Future<*>? = null
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        // sendMessage no se puede llamar desde dos hilos a la vez: lo llaman el que ejecuta y el
        // que responde a los mensajes.
        executions[session.id] = Execution(ConcurrentWebSocketSessionDecorator(session, SEND_TIMEOUT_MS, SEND_BUFFER_BYTES))
    }

    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        val execution = executions[session.id] ?: return
        val request =
            try {
                jsonMapper.readValue(message.payload, ClientMessage::class.java)
            } catch (e: JacksonException) {
                return fail(execution, "Mensaje inválido")
            }
        when (request.type) {
            "start" -> start(execution, request)
            "input" -> request.value?.let(execution.inputs::put) ?: fail(execution, "Falta el valor del input")
            else -> fail(execution, "Tipo de mensaje desconocido: ${request.type}")
        }
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        // Si quien la pidió se fue, la ejecución no tiene a quién darle el output ni de quién
        // esperar inputs: se corta.
        executions.remove(session.id)?.running?.cancel(true)
    }

    private fun start(
        execution: Execution,
        request: ClientMessage,
    ) {
        if (execution.running != null) return fail(execution, "La ejecución ya empezó")
        val code = request.code ?: return fail(execution, "Falta el código")
        val language = request.language ?: return fail(execution, "Falta el lenguaje")
        val version = request.version ?: return fail(execution, "Falta la versión")
        val runner =
            try {
                registry.runnerFor(language, version)
            } catch (e: UnsupportedLanguageException) {
                return fail(execution, e.message!!)
            }
        val io = SocketIO(execution, request.inputs, request.interactive)
        execution.running =
            executor.submit {
                val done =
                    try {
                        DoneMessage(runner.execute(code, version, io))
                    } catch (e: ExecutionAbortedException) {
                        DoneMessage(CodeError(e.message!!, line = null, column = null))
                    } catch (e: Exception) {
                        log.error("Falló una ejecución de $language $version", e)
                        FailureMessage("No se pudo terminar la ejecución")
                    }
                if (execution.session.isOpen) {
                    send(execution, done)
                    execution.session.close(CloseStatus.NORMAL)
                }
            }
    }

    private fun fail(
        execution: Execution,
        message: String,
    ) {
        send(execution, FailureMessage(message))
        execution.session.close(CloseStatus.POLICY_VIOLATION)
    }

    private fun send(
        execution: Execution,
        message: ServerMessage,
    ) {
        execution.session.sendMessage(TextMessage(jsonMapper.writeValueAsString(message)))
    }

    /** Lo que imprime el código sale por el WebSocket; los inputs llegan por el mismo. */
    private inner class SocketIO(
        private val execution: Execution,
        given: List<String>,
        private val interactive: Boolean,
    ) : ExecutionIO {
        private val given = ArrayDeque(given)

        override fun output(value: String) = sendOrAbort(OutputMessage(value))

        override fun input(message: String): String? {
            given.removeFirstOrNull()?.let { return it }
            if (!interactive) return null
            sendOrAbort(InputRequestMessage(message))
            return try {
                execution.inputs.poll(inputTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    ?: throw ExecutionAbortedException("No llegó el input para \"$message\" en ${inputTimeout.toMinutes()} minutos")
            } catch (e: InterruptedException) {
                throw ExecutionAbortedException("Se cortó la ejecución")
            }
        }

        private fun sendOrAbort(message: ServerMessage) {
            try {
                send(execution, message)
            } catch (e: Exception) {
                throw ExecutionAbortedException("Se cortó la conexión")
            }
        }
    }

    private companion object {
        const val SEND_TIMEOUT_MS = 10_000
        const val SEND_BUFFER_BYTES = 512 * 1024
    }
}

@Configuration
@EnableWebSocket
class ExecutionSocketConfig(
    private val handler: ExecutionHandler,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/execution")
    }
}
