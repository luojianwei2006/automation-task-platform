package com.task.platform.user.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import com.task.platform.common.utils.JwtUtil;
import com.task.platform.common.utils.PasswordUtil;
import com.task.platform.user.entity.User;
import com.task.platform.user.entity.SysConfig;
import com.task.platform.user.mapper.UserMapper;
import com.task.platform.user.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务 - 注册、登录、Token管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final SysConfigMapper sysConfigMapper;

    // Redis Key前缀
    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final String SMS_LIMIT_PREFIX = "sms:limit:";
    // 验证码有效期60秒
    private static final Duration SMS_CODE_TTL = Duration.ofSeconds(300);
    // 每日发送上限
    private static final int SMS_DAILY_LIMIT = 10;

    /**
     * 用户注册（密码+验证码）
     *
     * @param phone      手机号
     * @param code       短信验证码
     * @param password   密码
     * @param nickname   昵称
     * @param inviteCode 邀请码（可选）
     * @return JWT Token + 用户信息
     */
    public Map<String, Object> register(String phone, String code,
                                        String password, String nickname,
                                        String inviteCode) {
        // 1. 校验验证码（如开启手机号验证）
        if (isPhoneVerifyRequired()) {
            verifySmsCode(phone, code);
        }

        // 2. 检查手机号是否已注册
        if (userMapper.selectByPhone(phone) != null) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }

        // 3. 处理邀请关系（如有邀请码）
        Long inviterId = null;
        if (inviteCode != null && !inviteCode.isBlank()) {
            User inviter = userMapper.selectByInviteCode(inviteCode);
            if (inviter == null) {
                throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
            }
            inviterId = inviter.getId();
        }

        // 4. 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setPassword(PasswordUtil.encode(password));
        user.setNickname(nickname);
        user.setInviteCode(generateInviteCode());
        user.setInviterId(inviterId);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        save(user);

        // 5. 生成JWT Token
        String token = JwtUtil.generateToken(user.getId(), "USER");
        String refreshToken = JwtUtil.generateRefreshToken(user.getId());

        // 6. 删除验证码（防重复使用）
        redisTemplate.delete(SMS_CODE_PREFIX + phone);

        return buildLoginResponse(user, token, refreshToken);
    }

    /**
     * 密码登录
     *
     * @param phone    手机号
     * @param password 密码
     * @return JWT Token + 用户信息
     */
    public Map<String, Object> loginWithPassword(String phone, String password) {
        // 1. 查找用户
        User user = userMapper.selectByPhone(phone);
        if (user == null) {
            throw new BusinessException(ErrorCode.PHONE_NOT_REGISTERED);
        }

        // 2. 检查状态
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // 3. 验证密码
        if (!PasswordUtil.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // 4. 生成Token
        String token = JwtUtil.generateToken(user.getId(), "USER");
        String refreshToken = JwtUtil.generateRefreshToken(user.getId());

        return buildLoginResponse(user, token, refreshToken);
    }

    /**
     * 短信验证码登录
     *
     * @param phone 手机号
     * @param code  验证码
     * @return JWT Token + 用户信息
     */
    public Map<String, Object> loginWithSms(String phone, String code) {
        // 1. 校验验证码
        verifySmsCode(phone, code);

        // 2. 查找用户，不存在则自动注册
        User user = userMapper.selectByPhone(phone);
        boolean isNewUser = false;

        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setPassword(PasswordUtil.encode(IdUtil.fastSimpleUUID()));
            user.setInviteCode(generateInviteCode());
            user.setStatus(1);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            save(user);
            isNewUser = true;
        } else {
            if (user.getStatus() == 0) {
                throw new BusinessException(ErrorCode.USER_DISABLED);
            }
        }

        // 3. 生成Token
        String token = JwtUtil.generateToken(user.getId(), "USER");
        String refreshToken = JwtUtil.generateRefreshToken(user.getId());

        // 4. 删除验证码
        redisTemplate.delete(SMS_CODE_PREFIX + phone);

        Map<String, Object> response = buildLoginResponse(user, token, refreshToken);
        response.put("isNewUser", isNewUser);
        return response;
    }

    /**
     * 是否为测试环境：sys_config 中 test_env = true 时返回 true
     */
    private boolean isTestEnv() {
        try {
            SysConfig cfg = sysConfigMapper.selectByConfigKey("test_env");
            return cfg != null && "true".equalsIgnoreCase(cfg.getConfigValue());
        } catch (Exception e) {
            log.warn("[SMS] 读取 test_env 配置失败，按生产环境处理: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 注册时是否需要验证手机号（短信验证码）。
     * sys_config 中 require_phone_verify='false' 时返回 false（免验证）；
     * 缺失或非 'false' 一律返回 true（默认强制验证，最安全兜底）。
     */
    private boolean isPhoneVerifyRequired() {
        try {
            SysConfig cfg = sysConfigMapper.selectByConfigKey("require_phone_verify");
            return cfg == null || !"false".equalsIgnoreCase(cfg.getConfigValue());
        } catch (Exception e) {
            log.warn("[SMS] 读取 require_phone_verify 配置失败，按需验证处理: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 发送短信验证码（Redis双Key设计）
     *
     * @param phone   手机号
     * @param type    类型：1注册 2登录 3重置密码
     */
    public void sendSmsCode(String phone, Integer type) {
        if (isTestEnv()) {
            // 测试环境：统一验证码 666666，跳过频率/冷却限制
            redisTemplate.opsForValue().set(SMS_CODE_PREFIX + phone, "666666", SMS_CODE_TTL);
            log.info("[SMS][测试环境] 手机号: {}, 类型: {}, 固定验证码: 666666", phone, type);
            return;
        }
        // 1. 检查每日发送频率限制
        String limitKey = SMS_LIMIT_PREFIX + phone;
        String countStr = redisTemplate.opsForValue().get(limitKey);
        if (countStr != null && Integer.parseInt(countStr) >= SMS_DAILY_LIMIT) {
            throw new BusinessException(ErrorCode.SMS_CODE_SEND_TOO_OFTEN);
        }

        // 2. 检查60秒冷却时间
        String cooldownKey = "sms:cooldown:" + phone;
        Boolean hasCooldown = redisTemplate.hasKey(cooldownKey);
        if (Boolean.TRUE.equals(hasCooldown)) {
            throw new BusinessException(ErrorCode.SMS_CODE_SEND_TOO_OFTEN);
        }

        // 3. 生成6位随机验证码
        String smsCode = String.format("%06d", (int)(Math.random() * 1000000));

        // 4. 存储到Redis（60秒有效期）
        redisTemplate.opsForValue().set(
                SMS_CODE_PREFIX + phone,
                smsCode,
                SMS_CODE_TTL
        );

        // 5. 设置冷却时间（60秒内不能重复发）
        redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(60));

        // 6. 增加每日计数
        redisTemplate.opsForValue().increment(limitKey);
        // 设置每日计数过期时间：当天23:59:59（距明天0点）
        redisTemplate.expire(limitKey, 1L, TimeUnit.DAYS);

        // TODO: 调用腾讯云SMS接口发送短信
        log.info("[SMS] 手机号: {}, 类型: {}, 验证码: {}（开发环境直接输出）", phone, type, smsCode);
    }

    /**
     * 重置密码
     *
     * @param phone          手机号
     * @param code           新验证码
     * @param newPassword    新密码
     */
    public void resetPassword(String phone, String code, String newPassword) {
        verifySmsCode(phone, code);

        User user = userMapper.selectByPhone(phone);
        if (user == null) {
            throw new BusinessException(ErrorCode.PHONE_NOT_REGISTERED);
        }

        user.setPassword(PasswordUtil.encode(newPassword));
        updateById(user);

        // 删除验证码
        redisTemplate.delete(SMS_CODE_PREFIX + phone);
    }

    /**
     * 刷新Token
     *
     * @param oldToken 旧Token（即将过期）
     * @return 新Token
     */
    public Map<String, Object> refreshToken(String oldToken) {
        if (!JwtUtil.canRefresh(oldToken)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Token已过期太久，请重新登录");
        }

        Long userId = JwtUtil.getUserId(oldToken);
        User user = getById(userId);
        if (user == null || user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        String newToken = JwtUtil.generateToken(userId, "USER");
        String newRefreshToken = JwtUtil.generateRefreshToken(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("token", newToken);
        response.put("refreshToken", newRefreshToken);
        response.put("expiresIn", EXPIRATION);
        return response;
    }

    /**
     * 校验短信验证码
     */
    private void verifySmsCode(String phone, String code) {
        String key = SMS_CODE_PREFIX + phone;
        String storedCode = redisTemplate.opsForValue().get(key);

        if (storedCode == null) {
            throw new BusinessException(ErrorCode.SMS_CODE_EXPIRED);
        }
        if (!storedCode.equals(code)) {
            throw new BusinessException(ErrorCode.SMS_CODE_ERROR);
        }
    }

    /**
     * 构建登录响应
     */
    private Map<String, Object> buildLoginResponse(User user, String token, String refreshToken) {
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("refreshToken", refreshToken);
        response.put("expiresIn", 30 * 24 * 60 * 60); // 30天

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("phone", maskPhone(user.getPhone()));
        userInfo.put("nickname", user.getNickname());
        userInfo.put("avatarUrl", user.getAvatarUrl());
        userInfo.put("realAuthStatus", user.getRealAuthStatus());
        userInfo.put("inviteCode", user.getInviteCode());
        response.put("userInfo", userInfo);

        return response;
    }

    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 生成邀请码（BASE62编码，8位）
     * 排除易混淆字符：0/O/1/I/L
     */
    private String generateInviteCode() {
        final String CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CHARS.charAt((int) (Math.random() * CHARS.length())));
        }
        return sb.toString();
        // 生产环境应使用userId编码保证唯一性
    }

    // Token过期时间常量
    private static final long EXPIRATION = 30L * 24 * 60 * 60;
}
