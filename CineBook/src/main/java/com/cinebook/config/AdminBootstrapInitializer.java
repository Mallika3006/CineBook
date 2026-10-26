package com.cinebook.config;

import com.cinebook.user.User;
import com.cinebook.user.UserRepository;
import com.cinebook.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Securely bootstraps the initial ADMIN account and optional demo customer account
 * from environment-provided credentials at application startup.
 * Never logs raw passwords or secrets.
 */
@Component
public class AdminBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapInitializer.class);

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapInitializer(
            AppProperties appProperties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.appProperties = appProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        bootstrapAdminAccount();
        bootstrapDemoCustomerAccount();
    }

    private void bootstrapAdminAccount() {
        AppProperties.Admin adminCfg = appProperties.getAdmin();
        if (!adminCfg.isBootstrapEnabled()) {
            log.info("Initial admin bootstrap is disabled (CINEBOOK_ADMIN_BOOTSTRAP_ENABLED=false).");
            return;
        }
        String email = adminCfg.getEmail() != null ? adminCfg.getEmail().trim().toLowerCase() : "";
        String rawPassword = adminCfg.getPassword() != null ? adminCfg.getPassword() : "";
        if (email.isEmpty() || rawPassword.isBlank()) {
            log.warn("Admin bootstrap enabled, but CINEBOOK_ADMIN_EMAIL or CINEBOOK_ADMIN_PASSWORD is empty. Skipping admin seed.");
            return;
        }

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            log.info("Initial admin account already exists for email: {}", email);
            return;
        }

        User admin = new User();
        admin.setFullName(adminCfg.getName() != null && !adminCfg.getName().isBlank() ? adminCfg.getName().trim() : "CineBook System Admin");
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);
        log.info("Initialized ADMIN account for email: {}", email);
    }

    private void bootstrapDemoCustomerAccount() {
        AppProperties.DemoCustomer custCfg = appProperties.getDemoCustomer();
        if (!custCfg.isEnabled()) {
            return;
        }
        String email = custCfg.getEmail() != null ? custCfg.getEmail().trim().toLowerCase() : "";
        String rawPassword = custCfg.getPassword() != null ? custCfg.getPassword() : "";
        if (email.isEmpty() || rawPassword.isBlank()) {
            return;
        }

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return;
        }

        User customer = new User();
        customer.setFullName(custCfg.getName() != null && !custCfg.getName().isBlank() ? custCfg.getName().trim() : "Aarav Sharma");
        customer.setEmail(email);
        customer.setPasswordHash(passwordEncoder.encode(rawPassword));
        customer.setRole(UserRole.USER);
        customer.setActive(true);
        userRepository.save(customer);
        log.info("Initialized demo USER account for email: {}", email);
    }
}
