package io.github.pratikpanchal22.authserver.controller;

import io.github.pratikpanchal22.authserver.service.HrdService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hrd")
public class HrdController {

    private final HrdService hrdService;

    public HrdController(HrdService hrdService) {
        this.hrdService = hrdService;
    }

    record HrdResponse(String method, String registrationId) {}

    @GetMapping("/lookup")
    public HrdResponse lookup(@RequestParam String email) {
        HrdService.HrdResult result = hrdService.lookup(email);
        return new HrdResponse(result.method(), result.registrationId());
    }
}
