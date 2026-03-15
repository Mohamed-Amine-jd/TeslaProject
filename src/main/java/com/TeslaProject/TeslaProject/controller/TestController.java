package com.TeslaProject.TeslaProject.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TestController {

    @GetMapping("/public/hello")
    public String publicEndpoint() {
        System.out.println("=== PUBLIC ENDPOINT APPELÉ ===");
        return "Accessible sans token !";
    }

    @GetMapping("/user/profile")
    public Map<String, Object> userProfile(Authentication auth) {
        // Récupérer le JWT pour extraire preferred_username
        var jwt = (org.springframework.security.oauth2.jwt.Jwt) auth.getPrincipal();
        String username = jwt.getClaim("preferred_username");
        String email = jwt.getClaim("email");
        String name = jwt.getClaim("name");

        System.out.println("=== PROFILE APPELÉ ===");
        System.out.println("  Username : " + username);
        System.out.println("  Roles    : " + auth.getAuthorities());

        return Map.of(
                "username", username,
                "email", email,
                "name", name,
                "roles", auth.getAuthorities().toString()
        );
    }
}