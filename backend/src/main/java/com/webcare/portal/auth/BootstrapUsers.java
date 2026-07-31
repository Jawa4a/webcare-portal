package com.webcare.portal.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapUsers implements CommandLineRunner {

    private final PortalUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${webcare.bootstrap.admin.email}")
    private String adminEmail;
    @Value("${webcare.bootstrap.admin.password}")
    private String adminPassword;
    @Value("${webcare.bootstrap.admin.name}")
    private String adminName;
    @Value("${webcare.bootstrap.specialist.email}")
    private String specialistEmail;
    @Value("${webcare.bootstrap.specialist.password}")
    private String specialistPassword;
    @Value("${webcare.bootstrap.specialist.name}")
    private String specialistName;
    @Value("${webcare.bootstrap.viewer.email}")
    private String viewerEmail;
    @Value("${webcare.bootstrap.viewer.password}")
    private String viewerPassword;
    @Value("${webcare.bootstrap.viewer.name}")
    private String viewerName;

    public BootstrapUsers(
            PortalUserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        createIfMissing(adminEmail, adminPassword, adminName, RoleCode.ADMIN);
        createIfMissing(specialistEmail, specialistPassword, specialistName, RoleCode.SPECIALIST);
        createIfMissing(viewerEmail, viewerPassword, viewerName, RoleCode.VIEWER);
    }

    private void createIfMissing(String email, String password, String name, RoleCode roleCode) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            return;
        }
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Required role is missing: " + roleCode));
        userRepository.save(PortalUser.create(
                normalizedEmail,
                passwordEncoder.encode(password),
                name,
                role));
    }
}
