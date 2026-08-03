package com.webcare.portal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class BootstrapUsersTest {

    @Test
    void synchronizesExistingPasswordsWhenDemoModeIsEnabled() {
        PortalUserRepository userRepository = mock(PortalUserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        Role role = mock(Role.class);

        PortalUser admin = PortalUser.create(
                "admin@webcare.local", passwordEncoder.encode("old-admin-password"), "Admin", role);
        PortalUser specialist = PortalUser.create(
                "specialist@webcare.local", passwordEncoder.encode("old-specialist-password"), "Specialist", role);
        PortalUser viewer = PortalUser.create(
                "viewer@webcare.local", passwordEncoder.encode("old-viewer-password"), "Viewer", role);

        when(userRepository.findByEmailIgnoreCase("admin@webcare.local")).thenReturn(Optional.of(admin));
        when(userRepository.findByEmailIgnoreCase("specialist@webcare.local")).thenReturn(Optional.of(specialist));
        when(userRepository.findByEmailIgnoreCase("viewer@webcare.local")).thenReturn(Optional.of(viewer));

        BootstrapUsers bootstrapUsers = new BootstrapUsers(userRepository, roleRepository, passwordEncoder);
        ReflectionTestUtils.setField(bootstrapUsers, "demoEnabled", true);
        ReflectionTestUtils.setField(bootstrapUsers, "adminEmail", "admin@webcare.local");
        ReflectionTestUtils.setField(bootstrapUsers, "adminPassword", "WebcareAdmin!2026");
        ReflectionTestUtils.setField(bootstrapUsers, "adminName", "Admin");
        ReflectionTestUtils.setField(bootstrapUsers, "specialistEmail", "specialist@webcare.local");
        ReflectionTestUtils.setField(bootstrapUsers, "specialistPassword", "WebcareSpecialist!2026");
        ReflectionTestUtils.setField(bootstrapUsers, "specialistName", "Specialist");
        ReflectionTestUtils.setField(bootstrapUsers, "viewerEmail", "viewer@webcare.local");
        ReflectionTestUtils.setField(bootstrapUsers, "viewerPassword", "WebcareViewer!2026");
        ReflectionTestUtils.setField(bootstrapUsers, "viewerName", "Viewer");

        bootstrapUsers.run();

        assertThat(passwordEncoder.matches("WebcareAdmin!2026", admin.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("WebcareSpecialist!2026", specialist.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("WebcareViewer!2026", viewer.getPasswordHash())).isTrue();
    }
}
