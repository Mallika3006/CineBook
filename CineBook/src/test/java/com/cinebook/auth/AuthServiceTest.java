package com.cinebook.auth;

import com.cinebook.auth.dto.AuthResponse;
import com.cinebook.auth.dto.LoginRequest;
import com.cinebook.auth.dto.RegisterRequest;
import com.cinebook.config.AppProperties;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.UnauthorizedException;
import com.cinebook.security.JwtTokenProvider;
import com.cinebook.user.User;
import com.cinebook.user.UserRepository;
import com.cinebook.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret("TestSecretKeyForCineBookUnitAndIntegrationSuite2026_AtLeast32Bytes!");
        props.getJwt().setExpirationMs(3600000L);

        passwordEncoder = new BCryptPasswordEncoder(10);
        jwtTokenProvider = new JwtTokenProvider(props);
        jwtTokenProvider.init();

        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, props);
    }

    @Test
    @DisplayName("Successful customer registration hashes password with BCrypt and forces USER role")
    void registerSuccessForcesUserRole() {
        RegisterRequest req = new RegisterRequest(
                "Aarav Sharma",
                "Aarav@Example.com",
                "StrongPass123",
                "StrongPass123"
        );

        when(userRepository.existsByEmail("aarav@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            u.setCreatedAt(OffsetDateTime.now());
            return u;
        });

        AuthResponse res = authService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();

        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("StrongPass123");
        assertThat(passwordEncoder.matches("StrongPass123", savedUser.getPasswordHash())).isTrue();

        assertThat(res.token()).isNotBlank();
        assertThat(res.user().role()).isEqualTo("USER");
        assertThat(res.user().email()).isEqualTo("aarav@example.com");
    }

    @Test
    @DisplayName("Duplicate email registration is rejected with ConflictException")
    void registerDuplicateEmailRejected() {
        RegisterRequest req = new RegisterRequest(
                "Aarav Sharma",
                "existing@cinebook.local",
                "StrongPass123",
                "StrongPass123"
        );
        when(userRepository.existsByEmail("existing@cinebook.local")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("Mismatched password confirmation is rejected with BadRequestException")
    void registerPasswordMismatchRejected() {
        RegisterRequest req = new RegisterRequest(
                "Aarav Sharma",
                "aarav@cinebook.local",
                "StrongPass123",
                "DifferentPass999"
        );

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    @DisplayName("Login with valid credentials returns signed JWT and user profile")
    void loginSuccess() {
        User u = new User();
        u.setId(10L);
        u.setFullName("Priya Nair");
        u.setEmail("priya@cinebook.local");
        u.setPasswordHash(passwordEncoder.encode("SecretPass123"));
        u.setRole(UserRole.USER);
        u.setActive(true);

        when(userRepository.findByEmail("priya@cinebook.local")).thenReturn(Optional.of(u));

        AuthResponse res = authService.login(new LoginRequest("priya@cinebook.local", "SecretPass123"));

        assertThat(res.token()).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(res.token())).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromToken(res.token())).isEqualTo(10L);
    }

    @Test
    @DisplayName("Login with incorrect password throws UnauthorizedException")
    void loginWrongPasswordRejected() {
        User u = new User();
        u.setId(10L);
        u.setFullName("Priya Nair");
        u.setEmail("priya@cinebook.local");
        u.setPasswordHash(passwordEncoder.encode("SecretPass123"));
        u.setRole(UserRole.USER);
        u.setActive(true);

        when(userRepository.findByEmail("priya@cinebook.local")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.login(new LoginRequest("priya@cinebook.local", "WrongPassword1")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email address or password");
    }
}
