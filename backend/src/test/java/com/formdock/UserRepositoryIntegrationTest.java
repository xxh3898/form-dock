package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formdock.auth.User;
import com.formdock.auth.UserRepository;
import com.formdock.auth.UserRole;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
@Transactional
class UserRepositoryIntegrationTest {

    private static final String PLAINTEXT_PASSWORD = "test-only-local-passphrase";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_persistCanonicalCreator_when_validUserIsSaved() {
        String passwordHash = passwordEncoder.encode(PLAINTEXT_PASSWORD);

        User saved = userRepository.saveAndFlush(User.createAdmin(
                " Admin@Example.com ", passwordHash, " Local Creator "));
        entityManager.clear();

        User persisted = userRepository.findById(saved.getId()).orElseThrow();
        String rawPasswordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?",
                String.class,
                saved.getId());

        assertThat(persisted.getEmail()).isEqualTo("admin@example.com");
        assertThat(persisted.getDisplayName()).isEqualTo("Local Creator");
        assertThat(persisted.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
        assertThat(rawPasswordHash)
                .startsWith("{bcrypt}")
                .isNotEqualTo(PLAINTEXT_PASSWORD);
        assertThat(passwordEncoder.matches(PLAINTEXT_PASSWORD, rawPasswordHash)).isTrue();
    }

    @Test
    void should_rejectDuplicateEmail_when_canonicalEmailAlreadyExists() {
        String passwordHash = passwordEncoder.encode(PLAINTEXT_PASSWORD);
        userRepository.saveAndFlush(User.createAdmin(
                "duplicate@example.com", passwordHash, "First Creator"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.createAdmin(
                " DUPLICATE@EXAMPLE.COM ", passwordHash, "Second Creator")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
