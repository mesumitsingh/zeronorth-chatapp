package com.zeronorth.chatapp.service;

import com.zeronorth.chatapp.dto.LoginRequest;
import com.zeronorth.chatapp.dto.RegisterRequest;
import com.zeronorth.chatapp.model.User;
import com.zeronorth.chatapp.repository.UserRepository;
import com.zeronorth.chatapp.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Map<String, Object> register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Email already registered"
            );
        }

        User user = new User();

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        user.setOnline(false);
        user.setLastSeen(LocalDateTime.now());

        userRepository.save(user);

        Map<String, Object> response = new HashMap<>();

        response.put("message", "Registration successful");

        return response;
    }

    public Map<String, Object> login(LoginRequest request) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid email or password"
                        )
                );

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new IllegalArgumentException(
                    "Invalid email or password"
            );
        }

        String token = jwtService.generateToken(user.getEmail());

        Map<String, Object> response = new HashMap<>();

        response.put("token", token);
        response.put("name", user.getName());
        response.put("email", user.getEmail());
        response.put("userId", user.getId());

        return response;
    }
}