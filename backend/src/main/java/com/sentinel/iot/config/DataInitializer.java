package com.sentinel.iot.config;

import com.sentinel.iot.model.AppUser;
import com.sentinel.iot.repository.AppUserRepository;
import com.sentinel.iot.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AppUserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${init.admin.password:}")
    private String adminInitPassword;

    @Value("${init.operator.password:}")
    private String operatorInitPassword;

    @Override
    public void run(String... args) {
        UUID defaultOrgId = organizationRepository.findBySlug("default")
                .map(org -> org.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Default organization not found — has V5__multi_tenancy migration run?"));

        if (adminInitPassword == null || adminInitPassword.isBlank()) {
            log.warn("INIT_ADMIN_PASSWORD not set — skipping default admin user creation. " +
                    "Set INIT_ADMIN_PASSWORD env var to seed the admin account on first run.");
        } else if (userRepository.findByUsername("admin").isEmpty()) {
            userRepository.save(new AppUser("admin", passwordEncoder.encode(adminInitPassword), "ADMIN", defaultOrgId));
            log.info("Default admin user created from INIT_ADMIN_PASSWORD");
        }

        if (operatorInitPassword == null || operatorInitPassword.isBlank()) {
            log.warn("INIT_OPERATOR_PASSWORD not set — skipping default operator user creation. " +
                    "Set INIT_OPERATOR_PASSWORD env var to seed the operator account on first run.");
        } else if (userRepository.findByUsername("operator").isEmpty()) {
            userRepository.save(new AppUser("operator", passwordEncoder.encode(operatorInitPassword), "OPERATOR", defaultOrgId));
            log.info("Default operator user created from INIT_OPERATOR_PASSWORD");
        }
    }
}
