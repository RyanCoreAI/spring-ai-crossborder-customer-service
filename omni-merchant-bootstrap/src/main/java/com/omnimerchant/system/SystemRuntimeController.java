package com.omnimerchant.system;

import com.omnimerchant.common.dto.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemRuntimeController {

    private final Environment environment;
    private final boolean demoDataEnabled;
    private final String seedVersion;

    public SystemRuntimeController(Environment environment,
                                   @Value("${omnimerchant.demo.enabled:false}") boolean demoDataEnabled,
                                   @Value("${omnimerchant.demo.seed-version:none}") String seedVersion) {
        this.environment = environment;
        this.demoDataEnabled = demoDataEnabled;
        this.seedVersion = seedVersion;
    }

    @GetMapping("/runtime")
    public R<RuntimeInfo> runtime() {
        var demoProfile = environment.acceptsProfiles(Profiles.of("demo"));
        var mode = demoProfile ? "DEMO" : environment.acceptsProfiles(Profiles.of("prod")) ? "PRODUCTION" : "LOCAL";
        return R.ok(new RuntimeInfo(mode, demoProfile && demoDataEnabled, demoProfile ? seedVersion : "none"));
    }

    public record RuntimeInfo(String mode, boolean demoDataEnabled, String seedVersion) {
    }
}
