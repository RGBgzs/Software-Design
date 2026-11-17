package com.example.security.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.security.dto.RegisterDTO;
import com.example.security.entity.Device;
import com.example.security.entity.User;
import com.example.security.mapper.DeviceMapper;
import com.example.security.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        Map<String, Object> result = new HashMap<>();

        if (username == null || password == null) {
            result.put("code", 400);
            result.put("msg", "用户名和密码不能为空");
            return result;
        }

        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("username", username));

        if (user == null || !password.equals(user.getPassword())) {
            result.put("code", 401);
            result.put("msg", "用户名或密码错误");
            return result;
        }

        // Simple token (replace with JWT in production)
        String token = "token-" + user.getId() + "-" + System.currentTimeMillis();

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("userId", user.getId());
        userData.put("username", user.getUsername());
        userData.put("role", user.getRole());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", userData);

        result.put("code", 200);
        result.put("msg", "登录成功");
        result.put("data", data);
        return result;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterDTO dto) {
        Map<String, Object> result = new HashMap<>();

        // 1. 非空校验
        if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
            result.put("code", 400);
            result.put("msg", "用户名不能为空");
            result.put("data", null);
            return result;
        }
        if (dto.getPassword() == null || dto.getPassword().isEmpty()) {
            result.put("code", 400);
            result.put("msg", "密码不能为空");
            result.put("data", null);
            return result;
        }
        if (dto.getConfirmPassword() == null || dto.getConfirmPassword().isEmpty()) {
            result.put("code", 400);
            result.put("msg", "确认密码不能为空");
            result.put("data", null);
            return result;
        }

        // 2. 两次密码一致
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            result.put("code", 400);
            result.put("msg", "两次密码不一致");
            result.put("data", null);
            return result;
        }

        // 3. 用户名已存在
        User exist = userMapper.selectOne(
                new QueryWrapper<User>().eq("username", dto.getUsername().trim()));
        if (exist != null) {
            result.put("code", 400);
            result.put("msg", "用户名已存在");
            result.put("data", null);
            return result;
        }

        // 4. 插入新用户
        User newUser = new User();
        newUser.setUsername(dto.getUsername().trim());
        newUser.setPassword(dto.getPassword());
        newUser.setRole("user");
        newUser.setCreateTime(LocalDateTime.now());
        userMapper.insert(newUser);

        result.put("code", 200);
        result.put("msg", "注册成功");
        result.put("data", null);
        return result;
    }

    /** 保安：获取自己管理的用户列表 */
    @GetMapping("/guard/managed-users")
    public Map<String, Object> getManagedUsers(@RequestParam Long guardId) {
        Map<String, Object> result = new HashMap<>();
        List<User> users = userMapper.selectList(
                new QueryWrapper<User>().eq("guard_id", guardId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            item.put("realName", u.getRealName());
            item.put("phone", u.getPhone());
            item.put("status", u.getStatus());
            Long deviceCount = deviceMapper.selectCount(
                    new QueryWrapper<Device>().eq("user_id", u.getId()));
            item.put("deviceCount", deviceCount != null ? deviceCount.intValue() : 0);
            list.add(item);
        }
        result.put("code", 200);
        result.put("data", list);
        result.put("total", list.size());
        return result;
    }

    /** 保安：获取未分配的用户列表 */
    @GetMapping("/users/unassigned")
    public Map<String, Object> getUnassignedUsers() {
        Map<String, Object> result = new HashMap<>();
        List<User> users = userMapper.selectList(
                new QueryWrapper<User>().isNull("guard_id").ne("role", "admin"));
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            item.put("realName", u.getRealName());
            item.put("role", u.getRole());
            list.add(item);
        }
        result.put("code", 200);
        result.put("data", list);
        result.put("total", list.size());
        return result;
    }

    /** 保安：将用户分配到自己名下 */
    @PostMapping("/guard/assign-user")
    public Map<String, Object> assignUser(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        Long userId = null;
        Long guardId = null;
        try {
            Object uid = body.get("userId");
            Object gid = body.get("guardId");
            if (uid instanceof Number) userId = ((Number) uid).longValue();
            if (gid instanceof Number) guardId = ((Number) gid).longValue();
        } catch (Exception ignored) {}
        if (userId == null || guardId == null) {
            result.put("code", 400);
            result.put("message", "参数错误");
            return result;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            result.put("code", 404);
            result.put("message", "用户不存在");
            return result;
        }
        user.setGuardId(guardId);
        userMapper.updateById(user);
        result.put("code", 200);
        result.put("message", "已添加");
        return result;
    }
}
