package com.formdock.auth;

import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class CreatorAuthenticationProvider implements AuthenticationProvider {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public CreatorAuthenticationProvider(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String rawEmail = authentication.getPrincipal() instanceof String email ? email : null;
        String rawPassword = authentication.getCredentials() instanceof String password ? password : "";

        User creator = findCreator(rawEmail);
        String passwordHash = creator == null ? dummyPasswordHash : creator.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(rawPassword, passwordHash);

        if (creator == null || !passwordMatches) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        CreatorPrincipal principal = new CreatorPrincipal(
                creator.getId(),
                creator.getEmail(),
                creator.getDisplayName(),
                creator.getRole());

        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + creator.getRole().name())));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private User findCreator(String rawEmail) {
        String email;
        try {
            email = CreatorEmail.normalize(rawEmail);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return userRepository.findByEmail(email).orElse(null);
    }
}
