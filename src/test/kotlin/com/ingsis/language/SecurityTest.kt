package com.ingsis.language

import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** Solo snippet-service, con su token machine-to-machine, puede pedir que se valide o corra código. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val request = """{"code": "println(1);", "language": "printscript", "version": "1.1"}"""

    @Test
    fun `sin token no se valida ni se corre codigo`() {
        listOf("/validate", "/run", "/format", "/lint").forEach { path ->
            mockMvc
                .post(path) {
                    contentType = MediaType.APPLICATION_JSON
                    content = request
                }.andExpect {
                    status { isUnauthorized() }
                    header { string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")) }
                }
        }
        mockMvc.get("/version").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `el health check no pide token`() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
    }
}
