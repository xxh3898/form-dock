package com.formdock.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("formdock.bootstrap")
public record CreatorBootstrapProperties(
        boolean enabled,
        String email,
        String password,
        String displayName) {
}
