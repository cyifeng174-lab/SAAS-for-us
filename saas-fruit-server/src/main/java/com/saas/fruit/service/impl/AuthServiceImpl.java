package com.saas.fruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saas.fruit.common.BusinessException;
import com.saas.fruit.dto.response.LoginResponse;
import com.saas.fruit.entity.User;
import com.saas.fruit.mapper.UserMapper;
import com.saas.fruit.security.JwtTokenUtil;
import com.saas.fruit.security.LoginUser;
import com.saas.fruit.security.LoginUserContext;
import com.saas.fruit.service.AuthService;
import com.saas.fruit.utils.BatchNoGenerator;
import com.saas.fruit.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现类
 * 处理微信小程序登录认证，支持Mock模式（本地开发）和生产模式（真实微信环境）
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BatchNoGenerator batchNoGenerator;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    /** Mock登录开关，本地开发时跳过微信认证 */
    @Value("${app.mock-login:false}")
    private boolean mockLogin;

    /** Mock模式的测试openid */
    @Value("${app.mock-openid:mock_openid_dev_001}")
    private String mockOpenid;

    /** Mock模式的测试用户昵称 */
    @Value("${app.mock-nickname:测试用户}")
    private String mockNickname;

    /**
     * 微信小程序登录
     *
     * 流程说明：
     * 1. Mock模式：直接用配置的mock_openid查库，新用户自动创建并分配tenant_id，老用户更新最后登录时间
     * 2. 生产模式：调用微信code2Session接口获取真实openid，后续逻辑同Mock模式
     * 3. 生成JWT Token返回给前端
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(String code) {
        String openid;
        boolean newUser = false;

        if (mockLogin) {
            // ========== Mock模式：本地开发跳过微信认证 ==========
            openid = mockOpenid;
            log.info("[Mock登录] 使用Mock OpenID: {}", openid);

        } else {
            // ========== 生产模式：调用微信code2Session接口获取openid ==========
            // TODO: 调用微信 code2Session 接口
            // 接口地址：https://api.weixin.qq.com/sns/jscode2session
            // 参数：appid, secret, js_code(code), grant_type=authorization_code
            // 返回：{ openid, session_key, unionid, errcode, errmsg }
            // 实现步骤：
            //   1. 从配置文件读取微信小程序 appid 和 appsecret
            //   2. 使用 RestTemplate 或 HttpClient 发起 GET 请求
            //   3. 解析响应JSON，检查 errcode 是否为0
            //   4. 若 errcode != 0，抛出 BusinessException("微信登录失败: " + errmsg)
            //   5. 若成功，提取 openid 和 session_key
            throw new BusinessException(500, "生产模式微信登录尚未实现");
        }

        // 根据openid查找已有用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getOpenid, openid);
        User user = userMapper.selectOne(queryWrapper);

        // 当前时间戳
        long now = DateUtil.now();

        if (user == null) {
            // ========== 新用户：自动注册 ==========
            newUser = true;
            user = new User();
            user.setOpenid(openid);
            // 生成租户ID：T_时间戳_随机字符
            user.setTenantId(batchNoGenerator.genTenantId());
            // 默认角色为管理员
            user.setRole("admin");
            user.setNickname(mockLogin ? mockNickname : "微信用户");
            user.setStatus("active");
            user.setLastLoginTime(now);
            user.setCreateTime(now);
            user.setUpdateTime(now);

            userMapper.insert(user);
            log.info("[用户注册] 新用户注册成功, userId={}, openid={}, tenantId={}",
                    user.getId(), openid, user.getTenantId());

        } else {
            // ========== 老用户：更新最后登录时间 ==========
            user.setLastLoginTime(now);
            user.setUpdateTime(now);
            // Mock模式下同步更新昵称（生产模式下可从微信获取最新昵称）
            if (mockLogin && user.getNickname() == null) {
                user.setNickname(mockNickname);
            }
            userMapper.updateById(user);
            log.info("[用户登录] 老用户登录成功, userId={}, openid={}", user.getId(), openid);
        }

        // 生成JWT Token
        String token = jwtTokenUtil.generateToken(
                user.getId(),
                user.getOpenid(),
                user.getTenantId(),
                user.getRole()
        );

        // 构建登录响应
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .openid(user.getOpenid())
                .tenantId(user.getTenantId())
                .role(user.getRole())
                .nickname(user.getNickname())
                .newUser(newUser)
                .build();
    }

    /**
     * 校验当前登录用户
     * 从ThreadLocal中获取JWT认证过滤器存入的用户信息
     */
    @Override
    public LoginUser checkAuth() {
        LoginUser loginUser = LoginUserContext.get();
        if (loginUser == null) {
            throw new BusinessException(401, "用户未登录或Token已过期，请重新登录");
        }
        return loginUser;
    }
}
