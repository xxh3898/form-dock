package com.formdock.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CreatorAuthenticationProviderTest {

    private static final String DUMMY_HASH = "{bcrypt}dummy-hash";
    private static final String STORED_HASH = "{bcrypt}stored-hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private CreatorAuthenticationProvider authenticationProvider;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(DUMMY_HASH);
        authenticationProvider = new CreatorAuthenticationProvider(userRepository, passwordEncoder);
    }

    @Test
    void should_authenticateCanonicalCreator_when_passwordMatches() {
        User creator = User.createAdmin(
                "admin@example.com",
                STORED_HASH,
                "Creator");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(creator));
        when(passwordEncoder.matches("correct-passphrase", STORED_HASH)).thenReturn(true);

        Authentication result = authenticationProvider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        " Admin@Example.com ",
                        "correct-passphrase"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getCredentials()).isNull();
        assertThat(result.getPrincipal()).isInstanceOf(CreatorPrincipal.class);
        CreatorPrincipal principal = (CreatorPrincipal) result.getPrincipal();
        assertThat(principal.email()).isEqualTo("admin@example.com");
        assertThat(principal.displayName()).isEqualTo("Creator");
        assertThat(principal.role()).isEqualTo(UserRole.ADMIN);
        assertThat(result.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void should_useDummyPasswordVerification_when_creatorDoesNotExist() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("wrong-passphrase", DUMMY_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authenticationProvider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "unknown@example.com",
                        "wrong-passphrase")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder).matches("wrong-passphrase", DUMMY_HASH);
    }

    @Test
    void should_useStoredPasswordVerification_when_passwordIsWrong() {
        User creator = User.createAdmin(
                "admin@example.com",
                STORED_HASH,
                "Creator");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(creator));
        when(passwordEncoder.matches("wrong-passphrase", STORED_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authenticationProvider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "admin@example.com",
                        "wrong-passphrase")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder).matches("wrong-passphrase", STORED_HASH);
    }

    @Test
    void should_useDummyPasswordVerification_when_emailIsMalformed() {
        when(passwordEncoder.matches("wrong-passphrase", DUMMY_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authenticationProvider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "   ",
                        "wrong-passphrase")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder).matches("wrong-passphrase", DUMMY_HASH);
    }
}
