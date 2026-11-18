package com.ingsis.language

import com.ingsis.language.observability.REQUEST_ID
import com.ingsis.language.observability.REQUEST_ID_HEADER
import com.ingsis.language.observability.RequestIdFilter
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** El filtro que pone en el MDC el id del pedido mientras se atiende, y lo saca al terminar. */
class RequestIdFilterTest {
    private val response = MockHttpServletResponse()
    private var seen: String? = null
    private val recordingChain = FilterChain { _, _ -> seen = MDC.get(REQUEST_ID) }

    private fun requestWith(requestId: String?) = MockHttpServletRequest().apply { requestId?.let { addHeader(REQUEST_ID_HEADER, it) } }

    @Test
    fun `usa el id que manda snippet-service y lo devuelve en la respuesta`() {
        RequestIdFilter().doFilter(requestWith("abc-123"), response, recordingChain)

        assertEquals("abc-123", seen)
        assertEquals("abc-123", response.getHeader(REQUEST_ID_HEADER))
        assertNull(MDC.get(REQUEST_ID))
    }

    @Test
    fun `si no viene un id, o no parece un id, genera uno`() {
        RequestIdFilter().doFilter(requestWith("abc\nINFO falso"), response, recordingChain)

        assertEquals(response.getHeader(REQUEST_ID_HEADER), seen)
        UUID.fromString(seen)
    }

    @Test
    fun `saca el id del MDC aunque el pedido falle`() {
        assertThrows<IllegalStateException> {
            RequestIdFilter().doFilter(requestWith("abc-123"), response) { _, _ -> throw IllegalStateException("falló") }
        }

        assertNull(MDC.get(REQUEST_ID))
    }
}
