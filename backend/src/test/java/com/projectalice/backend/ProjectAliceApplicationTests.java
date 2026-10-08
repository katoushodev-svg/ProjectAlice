package com.projectalice.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties="ALICE_CURSOR_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class ProjectAliceApplicationTests {

    @Test
    void contextLoads() {
    }
}
