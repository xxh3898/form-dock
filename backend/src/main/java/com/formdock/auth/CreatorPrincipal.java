package com.formdock.auth;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;

public record CreatorPrincipal(
        Long id,
        String email,
        String displayName,
        UserRole role) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return email;
    }
}
