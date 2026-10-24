package com.eclipse.tarjanapa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"app.load-on-startup=false", "tranzy.api-key=", "tranzy.agency-id="})
class TarjanApaApplicationTests {

    @Test
    void contextLoads() {
    }

}
