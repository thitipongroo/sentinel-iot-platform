package com.sentinel.iot.config;

import com.sentinel.iot.model.AppUser;
import com.sentinel.iot.repository.AppUserRepository;
import com.sentinel.iot.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public void run(String... args) {
        UUID defaultOrgId = organizationRepository.findBySlug("default")
                .map(org -> org.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Default organization not found — has V5__multi_tenancy migration run?"));

        if (userRepository.findByUsername("admin").isEmpty()) {
            userRepository.save(new AppUser("admin", passwordEncoder.encode("admin123"), "ADMIN", defaultOrgId));
            log.info("Default admin user created");
        }
        if (userRepository.findByUsername("operator").isEmpty()) {
            userRepository.save(new AppUser("operator", passwordEncoder.encode("op123"), "OPERATOR", defaultOrgId));
            log.info("Default operator user created");
        }
    }
}
