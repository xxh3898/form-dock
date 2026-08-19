package com.formdock.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorBootstrapProvisioner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapPasswordPolicy passwordPolicy;

    public CreatorBootstrapProvisioner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            BootstrapPasswordPolicy passwordPolicy) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    @Transactional
    public CreatorBootstrapResult provision(CreatorBootstrapProperties properties) {
        if (!properties.enabled()) {
            return CreatorBootstrapResult.DISABLED;
        }

        String email = normalizeEmail(properties.email());
        String displayName = normalizeDisplayName(properties.displayName());
        validatePassword(properties.password());

        if (userRepository.findByEmail(email).isPresent()) {
            return CreatorBootstrapResult.ALREADY_PROVISIONED;
        }
        if (userRepository.count() != 0) {
            throw new CreatorBootstrapException(
                    "Creator bootstrap cannot create an account when another creator exists");
        }

        String passwordHash = passwordEncoder.encode(properties.password());
        try {
            userRepository.saveAndFlush(User.createAdmin(email, passwordHash, displayName));
        }
        catch (DataIntegrityViolationException exception) {
            throw new CreatorBootstrapException(
                    "Creator bootstrap could not create an account because repository state changed");
        }
        return CreatorBootstrapResult.CREATED;
    }

    private String normalizeEmail(String email) {
        try {
            return CreatorEmail.normalize(email);
        }
        catch (IllegalArgumentException exception) {
            throw new CreatorBootstrapException(exception.getMessage());
        }
    }

    private String normalizeDisplayName(String displayName) {
        try {
            return CreatorDisplayName.normalize(displayName);
        }
        catch (IllegalArgumentException exception) {
            throw new CreatorBootstrapException(exception.getMessage());
        }
    }

    private void validatePassword(String password) {
        try {
            passwordPolicy.validate(password);
        }
        catch (IllegalArgumentException exception) {
            throw new CreatorBootstrapException(exception.getMessage());
        }
    }
}
