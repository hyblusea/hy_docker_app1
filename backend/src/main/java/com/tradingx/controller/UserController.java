package com.tradingx.controller;

import com.tradingx.model.R;
import com.tradingx.model.User;
import com.tradingx.model.ValidationException;
import com.tradingx.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/list")
    public R<List<User>> list(HttpSession session) {
        checkRoot(session);
        List<User> users = userService.listAll();
        users.forEach(u -> u.setPassword(null));
        return R.ok(users);
    }

    @PutMapping("/{id}/status")
    public R<User> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        checkRoot(session);
        String status = body.get("status");
        User user = userService.updateStatus(id, status);
        user.setPassword(null);
        return R.ok(user);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, HttpSession session) {
        checkRoot(session);
        User currentUser = (User) session.getAttribute("user");
        if (currentUser != null && currentUser.getId().equals(id)) {
            return R.fail("不能删除自己");
        }
        userService.delete(id);
        return R.ok(null);
    }

    @PostMapping
    public R<User> create(@RequestBody Map<String, String> body, HttpSession session) {
        checkRoot(session);
        String username = body.get("username");
        String encodedPassword = body.getOrDefault("password", "");
        String password = decodeBase64(encodedPassword);
        String role = body.getOrDefault("role", "user");
        User user = userService.createUser(username, password, role);
        user.setPassword(null);
        return R.ok(user);
    }

    @GetMapping("/{id}/password")
    public R<String> getPassword(@PathVariable Long id, HttpSession session) {
        checkRoot(session);
        String rawPassword = userService.decodePassword(id);
        return R.ok(rawPassword);
    }

    private void checkRoot(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            throw new ValidationException("未登录");
        }
        if (!userService.isRoot(user)) {
            throw new ValidationException("权限不足，需要管理员权限");
        }
    }

    private String decodeBase64(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded));
        } catch (Exception e) {
            return encoded;
        }
    }
}
