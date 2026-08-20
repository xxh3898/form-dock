package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formdock.auth.CreatorBootstrapException;
import com.formdock.auth.CreatorBootstrapProperties;
import com.formdock.auth.CreatorBootstrapProvisioner;
import com.formdock.auth.CreatorBootstrapResult;
import com.formdock.auth.User;
import com.formdock.auth.UserRepository;
import com.formdock.auth.UserRole;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
@Transactional
class CreatorBootstrapIntegrationTest {

    private static final String VALID_PASSWORD = "test-only-local-passphrase";

    @Autowired
    private CreatorBootstrapProvisioner provisioner;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void should_createNothing_when_bootstrapIsDisabled() {
        CreatorBootstrapResult result = provisioner.provision(
                new CreatorBootstrapProperties(false, null, null, null));

        assertThat(result).isEqualTo(CreatorBootstrapResult.DISABLED);
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void should_createCanonicalAdmin_when_bootstrapIsEnabledWithValidConfiguration() {
        CreatorBootstrapResult result = provisioner.provision(validProperties(
                " Admin@Example.com ", VALID_PASSWORD, " Local Creator "));

        User creator = userRepository.findByEmail("admin@example.com").orElseThrow();
        assertThat(result).isEqualTo(CreatorBootstrapResult.CREATED);
        assertThat(userRepository.count()).isOne();
        assertThat(creator.getDisplayName()).isEqualTo("Local Creator");
        assertThat(creator.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(creator.getPasswordHash())
                .startsWith("{bcrypt}")
                .contains("$10$");
        assertThat(passwordEncoder.matches(VALID_PASSWORD, creator.getPasswordHash())).isTrue();
        assertThat(creator.getCreatedAt()).isNotNull();
        assertThat(creator.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_preserveExistingCreator_when_bootstrapIsRepeatedWithSameCanonicalEmail() {
        provisioner.provision(validProperties(
                "admin@example.com", VALID_PASSWORD, "Original Creator"));
        User original = userRepository.findByEmail("admin@example.com").orElseThrow();
        String originalHash = original.getPasswordHash();

        CreatorBootstrapResult result = provisioner.provision(validProperties(
                " ADMIN@EXAMPLE.COM ", "another-valid-passphrase", "Changed Creator"));

        User replayed = userRepository.findByEmail("admin@example.com").orElseThrow();
        assertThat(result).isEqualTo(CreatorBootstrapResult.ALREADY_PROVISIONED);
        assertThat(userRepository.count()).isOne();
        assertThat(replayed.getPasswordHash()).isEqualTo(originalHash);
        assertThat(replayed.getDisplayName()).isEqualTo("Original Creator");
        assertThat(replayed.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void should_failClosed_when_bootstrapEmailIsMissing() {
        assertThatThrownBy(() -> provisioner.provision(validProperties(
                null, VALID_PASSWORD, "Local Creator")))
                .isInstanceOf(CreatorBootstrapException.class)
                .hasMessage("Creator email is required");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void should_failClosed_when_bootstrapPasswordIsMissing() {
        assertThatThrownBy(() -> provisioner.provision(validProperties(
                "creator@example.test", null, "Local Creator")))
                .isInstanceOf(CreatorBootstrapException.class)
                .hasMessage("Creator bootstrap password is required");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void should_failClosed_when_bootstrapDisplayNameIsMissing() {
        assertThatThrownBy(() -> provisioner.provision(validProperties(
                "creator@example.test", VALID_PASSWORD, null)))
                .isInstanceOf(CreatorBootstrapException.class)
                .hasMessage("Creator display name is required");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void should_failClosed_when_bootstrapPasswordIsTooShort() {
        assertThatThrownBy(() -> provisioner.provision(validProperties(
                "creator@example.test", "12345678901234", "Local Creator")))
                .isInstanceOf(CreatorBootstrapException.class)
                .hasMessageContaining("at least 15 characters");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void should_failClosed_when_bootstrapPasswordExceedsUtf8ByteLimit() {
        assertThatThrownBy(() -> provisioner.provision(validProperties(
                "creator@example.test", "가".repeat(25), "Local Creator")))
                .isInstanceOf(CreatorBootstrapException.class)
                .hasMessageContaining("72 UTF-8 bytes");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void should_failClosed_when_differentCreatorAlreadyExists() {
        userRepository.saveAndFlush(User.createAdmin(
                "existing@example.test",
                passwordEncoder.encode(VALID_PASSWORD),
                "Existing Creator"));

        assertThatThrownBy(() -> provisioner.provision(validProperties(
                "new@example.test", VALID_PASSWORD, "New Creator")))
                .isInstanceOf(CreatorBootstrapException.class)
                .hasMessageContaining("another creator exists");
        assertThat(userRepository.count()).isOne();
    }

    private CreatorBootstrapProperties validProperties(
            String email,
            String password,
            String displayName) {
        return new CreatorBootstrapProperties(true, email, password, displayName);
    }
}
