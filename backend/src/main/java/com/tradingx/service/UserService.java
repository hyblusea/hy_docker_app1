package com.tradingx.service;

import com.tradingx.model.User;
import com.tradingx.model.ValidationException;
import com.tradingx.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-zA-Z])(?=.*\\d).{6,}$");

    @PostConstruct
    public void init() {
        if (!userRepository.existsByUsername("root")) {
            User root = new User();
            root.setUsername("root");
            root.setPassword(encodePassword("J%4nQ7y!bV9$wR3d"));
            root.setRole("root");
            root.setStatus("approved");
            userRepository.save(root);
            log.info("默认管理员账号 root 已创建");
        }
    }

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ValidationException("用户名或密码错误"));
        if (!user.getPassword().equals(encodePassword(password))) {
            throw new ValidationException("用户名或密码错误");
        }
        if (!"approved".equals(user.getStatus())) {
            throw new ValidationException("账号待审核，请等待管理员审批");
        }
        return user;
    }

    public User register(String username, String password) {
        if (username == null || !EMAIL_PATTERN.matcher(username).matches()) {
            throw new ValidationException("用户名必须是有效的邮箱地址");
        }
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new ValidationException("密码必须大于等于6位，且必须包含字母和数字");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ValidationException("该邮箱已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(encodePassword(password));
        user.setRole("user");
        user.setStatus("pending");
        return userRepository.save(user);
    }

    public List<User> listAll() {
        return userRepository.findAll();
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ValidationException("用户不存在"));
    }

    public User updateStatus(Long id, String status) {
        User user = getById(id);
        if (!"approved".equals(status) && !"pending".equals(status) && !"rejected".equals(status)) {
            throw new ValidationException("无效的状态值");
        }
        user.setStatus(status);
        return userRepository.save(user);
    }

    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ValidationException("用户不存在");
        }
        userRepository.deleteById(id);
    }

    public User createUser(String username, String password, String role) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("用户名不能为空");
        }
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new ValidationException("密码必须大于等于6位，且必须包含字母和数字");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ValidationException("该用户名已存在");
        }
        if (!"user".equals(role) && !"root".equals(role)) {
            throw new ValidationException("无效的角色");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(encodePassword(password));
        user.setRole(role);
        user.setStatus("approved");
        return userRepository.save(user);
    }

    public String decodePassword(Long id) {
        User user = getById(id);
        return decodePassword(user.getPassword());
    }

    public boolean isRoot(User user) {
        return user != null && "root".equals(user.getRole());
    }

    public static String encodePassword(String rawPassword) {
        return Base64.getEncoder().encodeToString(rawPassword.getBytes());
    }

    private String decodePassword(String encodedPassword) {
        return new String(Base64.getDecoder().decode(encodedPassword));
    }
}
