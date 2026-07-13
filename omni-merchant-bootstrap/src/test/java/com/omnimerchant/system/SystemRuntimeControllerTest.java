package com.omnimerchant.system;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SystemRuntimeControllerTest {

    @Test
    void exposesOnlyDemoModeAndSeedMetadata() {
        var environment = new MockEnvironment().withProperty("spring.profiles.active", "demo");
        environment.setActiveProfiles("demo");
        var response = new SystemRuntimeController(environment, true, "v4-ui-test").runtime();

        assertThat(response.getData().mode()).isEqualTo("DEMO");
        assertThat(response.getData().demoDataEnabled()).isTrue();
        assertThat(response.getData().seedVersion()).isEqualTo("v4-ui-test");
        assertThat(response.getData().toString()).doesNotContain("password", "secret", "token");
    }

    @Test
    void neverReportsDemoDataOutsideDemoProfile() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        var response = new SystemRuntimeController(environment, true, "should-not-leak").runtime();

        assertThat(response.getData().mode()).isEqualTo("PRODUCTION");
        assertThat(response.getData().demoDataEnabled()).isFalse();
        assertThat(response.getData().seedVersion()).isEqualTo("none");
    }
}
