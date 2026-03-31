package com.FlashSale.Controller;

import com.FlashSale.Entity.User;
import com.FlashSale.Service.AuthService;
import com.FlashSale.Util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController{
    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        Optional<User> userOptional = authService.login(username, password);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            String token = authService.generateToken(user.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("username", user.getUsername());
            response.put("token", token); // 返回 JWT
            return ResponseEntity.ok(response);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("message", "用户名或密码错误");
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            User savedUser = authService.register(user);
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedUser.getId());
            response.put("username", savedUser.getUsername());
            response.put("message", "注册成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/user-info")
    public ResponseEntity<?> getUserInfo(@RequestHeader("Authorization") String token) {
        try {
            int userId = JwtUtil.validateToken(token.replace("Bearer ", ""));
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", userId);
            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Invalid token");
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> req) {
        try {
            int userId = Integer.parseInt(req.get("userId"));
            String oldPassword = req.get("oldPassword");
            String newPassword = req.get("newPassword");
            authService.changePassword(userId, oldPassword, newPassword);
            return ResponseEntity.ok(Map.of("message", "密码修改成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}