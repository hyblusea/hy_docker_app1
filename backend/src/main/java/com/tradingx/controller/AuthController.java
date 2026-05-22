package com.tradingx.controller;

import com.tradingx.model.R;
import com.tradingx.model.User;
import com.tradingx.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public R<User> login(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.get("username");
        String encodedPassword = body.getOrDefault("password", "");
        String password = decodeBase64(encodedPassword);
        User user = userService.login(username, password);
        session.setAttribute("user", user);
        return R.ok(sanitize(user));
    }

    @PostMapping("/logout")
    public R<Void> logout(HttpSession session) {
        session.invalidate();
        return R.ok(null);
    }

    @GetMapping("/me")
    public R<User> me(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return R.fail("未登录");
        }
        User fresh = userService.getById(user.getId());
        return R.ok(sanitize(fresh));
    }

    @PostMapping("/register")
    public R<User> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String encodedPassword = body.getOrDefault("password", "");
        String password = decodeBase64(encodedPassword);
        User user = userService.register(username, password);
        return R.ok(sanitize(user));
    }

    private User sanitize(User user) {
        User copy = new User();
        copy.setId(user.getId());
        copy.setUsername(user.getUsername());
        copy.setRole(user.getRole());
        copy.setStatus(user.getStatus());
        copy.setCreatedAt(user.getCreatedAt());
        return copy;
    }

    private String decodeBase64(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded));
        } catch (Exception e) {
            return encoded;
        }
    }
}
