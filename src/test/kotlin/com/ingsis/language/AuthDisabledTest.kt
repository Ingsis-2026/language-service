package com.ingsis.language

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** Con `auth.enabled=false`, como en el docker compose local, no se pide token. */
@SpringBootTest(properties = ["auth.enabled=false"])
@AutoConfigureMockMvc
class AuthDisabledTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `sin auth se consulta sin token`() {
        mockMvc.get("/version").andExpect { status { isOk() } }
    }
}
