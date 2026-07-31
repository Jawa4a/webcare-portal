package com.webcare.portal.auth;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final PortalUserRepository userRepository;

    public UserController(PortalUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    List<UserResponse> list() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(PortalUser::getDisplayName))
                .map(UserResponse::from)
                .toList();
    }
}
