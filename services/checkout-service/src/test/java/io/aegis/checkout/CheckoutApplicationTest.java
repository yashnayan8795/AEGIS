package io.aegis.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckoutApplicationTest {
    @Autowired WebServerApplicationContext context;
    @Test void startsIndependentHttpServer() {
        assertThat(context.getWebServer().getPort()).isPositive();
    }
}
