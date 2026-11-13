package com.example.teamforge.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("teamforge.auth")
public record SharedAccountProperties(@NotBlank String username, @NotBlank String password) {
    @Override
    public String toString() {
        return "SharedAccountProperties[username=" + username + "]";
    }
}
