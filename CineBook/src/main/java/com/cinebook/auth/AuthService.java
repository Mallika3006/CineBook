package com.cinebook.auth;

import com.cinebook.auth.dto.AuthResponse;
import com.cinebook.auth.dto.LoginRequest;
import com.cinebook.auth.dto.RegisterRequest;
import com.cinebook.config.AppProperties;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.UnauthorizedException;
import com.cinebook.security.JwtTokenProvider;
import com.cinebook.security.UserPrincipal;
import com.cinebook.user.User;
import com.cinebook.user.UserRepository;
import com.cinebook.user.UserRole;
import com.cinebook.user.dto.UserDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppProperties appProperties;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.appProperties = appProperties;
    }

    /**
     * Registers a new customer.
     * Enforces UserRole.USER strictly on the server — a customer can never register as ADMIN.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BadRequestException("Password and confirm password do not match.");
        }

        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email address is already registered.");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setActive(true);

        User saved = userRepository.save(user);
        UserPrincipal principal = UserPrincipal.fromUser(saved);
        String token = jwtTokenProvider.generateToken(principal);

        return new AuthResponse(
                token,
                "Bearer",
                appProperties.getJwt().getExpirationMs(),
                UserDto.from(saved)
        );
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email address or password."));

        if (!user.isActive()) {
            throw new UnauthorizedException("Your account has been deactivated. Please contact support.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email address or password.");
        }

        UserPrincipal principal = UserPrincipal.fromUser(user);
        String token = jwtTokenProvider.generateToken(principal);

        return new AuthResponse(
                token,
                "Bearer",
                appProperties.getJwt().getExpirationMs(),
                UserDto.from(user)
        );
    }
}
