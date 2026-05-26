package com.cf.login.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.user.mapper.UserMapper;
import com.admin.user.service.UserService;
import com.smw.auth.service.EmailService;
import com.smw.guild.service.GuildService;
import com.sysconf.constants.Constant;
import com.sysconf.util.CookieUtil;
import com.sysconf.util.DateUtil;
import com.sysconf.util.StringUtil;
import com.sysconf.security.AuthCredentialsValidator;
import com.sysconf.security.ClientIpResolver;
import com.sysconf.security.LoginAttemptTracker;
import com.sysconf.security.SHA256;

import org.springframework.beans.factory.annotation.Value;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class LoginServiceImpl implements LoginService {

    @Autowired
    DateUtil dateUtil;

	@Autowired 
	UserMapper userMapper;
    
    @Autowired
    UserService userService;
    
    @Autowired
    EmailService emailService;
    
    @Autowired
    GuildService guildService;
    
    @Autowired
    CookieUtil cookieUtil;

    @Autowired
    LoginAttemptTracker loginAttemptTracker;

    @Value("${smw.security.auth.password-min-length:8}")
    private int passwordMinLength;

    @Value("${smw.security.auth.password-max-length:128}")
    private int passwordMaxLength;

    @Override
    public Map<String, Object> selectDvcUserInfo(Map<String, Object> param) {
        Map<String, Object> userInfo = userMapper.selectDvcId(param);
        return userInfo;
    }

    @Override
    public void insertUserLoginLog(Map<String, Object> param) {
        param.put("login_date", dateUtil.now());
        param.put("ip_addr", param.get("ip"));
        userMapper.insertUserLoginLog(param);
    }

    @Override
    public List<Map<String, ?>> selectLastLoginHst(Map<String, Object> param) {
        return userMapper.selectLastLoginHst(param);
    }

    @Override
    @Transactional
    public Map<String, Object> signup(Map<String, Object> param) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String email = param.get("email") != null ? param.get("email").toString().trim() : null;
            String userId = param.get("user_id") != null ? param.get("user_id").toString().trim() : null;
            String userName = param.get("user_name") != null ? param.get("user_name").toString().trim() : null;

            String userIdError = AuthCredentialsValidator.validateUserId(userId);
            if (userIdError != null) {
                result.put("result", "FAIL");
                result.put("message", userIdError);
                return result;
            }
            Object rawPassword = param.get("password");
            String passwordError = AuthCredentialsValidator.validatePassword(
                    rawPassword != null ? rawPassword.toString() : null, passwordMinLength, passwordMaxLength);
            if (passwordError != null) {
                result.put("result", "FAIL");
                result.put("message", passwordError);
                return result;
            }
            
            // 이메일 인증 완료 여부 확인
            if (email == null || !emailService.isEmailVerified(email)) {
                result.put("result", "FAIL");
                result.put("message", "이메일 인증이 완료되지 않았습니다.");
                return result;
            }
            
            // 중복 계정 체크
            Map<String, Object> checkParam = new HashMap<>();
            checkParam.put("user_id", userId);
            Map<String, Object> existingUser = userService.selectUserInfo(checkParam);
            if (existingUser != null && !"dehs-NOTEXISTS".equals(existingUser.get("user_id"))) {
                result.put("result", "FAIL");
                result.put("message", "이미 사용 중인 아이디입니다.");
                return result;
            }

			// 이메일 중복 체크 (이미 가입된 이메일 방지)
			if (email != null && !email.isEmpty()) {
				Map<String, Object> emailParam = new HashMap<>();
				emailParam.put("email", email);
				int emailCnt = userService.countUserByEmail(emailParam);
				if (emailCnt > 0) {
					result.put("result", "FAIL");
					result.put("message", "이미 등록된 이메일입니다.");
					return result;
				}
			}
            
            // 회원가입 데이터 준비
            param.put("user_id", userId);
            // 닉네임 입력을 제거했으므로, 비어있으면 user_id를 기본값으로 사용
            if (userName == null || userName.isEmpty()) {
                param.put("user_name", userId);
            }
            if (param.get("password") != null) {
                param.put("user_pw", SHA256.encrypt(StringUtil.nvl(param.get("password").toString())));
            }
            param.put("usg_yn", param.get("usg_yn") != null && !"".equals(param.get("usg_yn").toString()) 
                    ? param.get("usg_yn").toString() : "Y");
            param.put("del_yn", param.get("del_yn") != null && !"".equals(param.get("del_yn").toString()) 
                    ? param.get("del_yn").toString() : "N");
            
            // 회원가입 시 세션 정보가 없으므로 자기 자신의 user_id를 사용
            param.put("sess_user_id", userId);
            
            userService.insertUserDtl(param);
            
            // 이메일 인증 정보 삭제 (재사용 방지)
            emailService.removeVerifiedEmail(email);
            
            result.put("result", "SUCCESS");
            result.put("user_id", userId);
            result.put("message", "회원가입이 완료되었습니다.");
            
            return result;
            
        } catch (Exception e) {
            log.error("일반 회원가입 실패 - user_id: {}", param.get("user_id"), e);
            result.put("result", "FAIL");
            result.put("message", "회원가입 처리 중 오류가 발생했습니다.");
            return result;
        }
    }

    @Override
    public Map<String, Object> login(Map<String, Object> param, HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();
        String clientIp = ClientIpResolver.resolve(request);
        String userId = param.get("user_id") != null ? param.get("user_id").toString().trim() : "";
        
        try {
            if (loginAttemptTracker.isBlocked(clientIp, userId)) {
                log.warn("로그인 일시 차단 — ip={}, user_id={}", clientIp, userId);
                result.put("result", "LOCKuserINFO");
                return result;
            }

            String userIdError = AuthCredentialsValidator.validateUserId(userId);
            if (userIdError != null) {
                result.put("result", "FAIL");
                result.put("message", userIdError);
                return result;
            }
            Object rawPassword = param.get("password");
            String passwordError = AuthCredentialsValidator.validatePassword(
                    rawPassword != null ? rawPassword.toString() : null, passwordMinLength, passwordMaxLength);
            if (passwordError != null) {
                result.put("result", "FAIL");
                result.put("message", passwordError);
                return result;
            }

            Map<String, Object> lookupParam = new HashMap<>(param);
            lookupParam.put("user_id", userId);
            Map<String, Object> userInfo = userService.selectUserInfo(lookupParam);
            
            String errorMessage = validateUser(userInfo);
            if (errorMessage != null) {
                log.warn("로그인 실패 — ip={}, code={}", clientIp, errorMessage);
                loginAttemptTracker.onFailure(clientIp, userId);
                result.put("result", errorMessage);
                return result;
            }
            if (userInfo == null) {
                loginAttemptTracker.onFailure(clientIp, userId);
                result.put("result", "NOuserINFO");
                return result;
            }
            
            String encPwd = SHA256.encrypt(StringUtil.nvl(rawPassword.toString()));
            Object storedPw = userInfo.get("user_pw");
            if (storedPw == null || !encPwd.equals(storedPw.toString())) {
                log.warn("로그인 실패(비밀번호) — ip={}, user_id={}", clientIp, userId);
                loginAttemptTracker.onFailure(clientIp, userId);
                result.put("result", "PWDNOTMATCHED");
                return result;
            }

            loginAttemptTracker.onSuccess(clientIp, userId);
            
            if (param.get("isMobile") != null) {
                userService.updateDvcId(param);
            }
            
            String autoLogin = param.get("auto_login") != null ? param.get("auto_login").toString() : "false";
            userInfo.put("auto_login", autoLogin);
            
            // 현재 소속 길드 정보 조회
            Map<String, Object> guildParam = new HashMap<>();
            guildParam.put("user_id", userInfo.get("user_id"));
            Map<String, ?> userGuild = guildService.selectUserGuild(guildParam);
            if (userGuild != null) {
                userInfo.put("guild_id", userGuild.get("guild_id"));
                userInfo.put("guild_name", userGuild.get("guild_name"));
                userInfo.put("guild_role", userGuild.get("role"));
            }
            
            processUserLogin(request, response, userInfo);
            
            result.put("result", "SUCCESS");
            result.put("userInfo", userInfo);
            
            return result;
            
        } catch (Exception e) {
            log.error("로그인 처리 오류 — ip={}, user_id={}", clientIp, userId, e);
            result.put("result", "FAIL");
            result.put("message", "로그인 처리 중 오류가 발생했습니다.");
            return result;
        }
    }

    @Override
    public Map<String, Object> biometricLogin(Map<String, Object> param, HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> userInfo = selectDvcUserInfo(param);
            
            if (userInfo == null) {
                log.info("==> User not found in database");
                result.put("result", "NOuserINFO");
                return result;
            }
            
            String autoLogin = param.get("auto_login") != null ? param.get("auto_login").toString() : "false";
            userInfo.put("auto_login", autoLogin);
            
            // 현재 소속 길드 정보 조회
            Map<String, Object> guildParam = new HashMap<>();
            guildParam.put("user_id", userInfo.get("user_id"));
            Map<String, ?> userGuild = guildService.selectUserGuild(guildParam);
            if (userGuild != null) {
                userInfo.put("guild_id", userGuild.get("guild_id"));
                userInfo.put("guild_name", userGuild.get("guild_name"));
                userInfo.put("guild_role", userGuild.get("role"));
            }

            userService.enrichUserRolesAndAdminFlag(userInfo);
            
            processUserLogin(request, response, userInfo);
            
            result.put("result", "SUCCESS");
            result.put("userInfo", userInfo);
            
            return result;
            
        } catch (Exception e) {
            log.error("생체 인증 로그인 실패", e);
            result.put("result", "FAIL");
            result.put("message", e.getMessage());
            return result;
        }
    }

    @Override
    public String validateUser(Map<String, Object> userInfo) {
        if (userInfo == null) {
            log.info("==> User not found in database");
            return "NOuserINFO";
        }
        
        if ("dehs-NOTEXISTS".equals(userInfo.get("user_id"))) {
            log.info("==> User not found in database");
            return "NOuserINFO";
        }
        
        if ("Y".equals(userInfo.get("lock_yn"))) {
            log.info("==> User account is locked");
            return "LOCKuserINFO";
        }
        
        return null;
    }

    @Override
    public void processUserLogin(HttpServletRequest request, HttpServletResponse response, Map<String, Object> userInfo) throws Exception {
        String userIp = ClientIpResolver.resolve(request);
        userInfo.put("ip", userIp);
        
        insertUserLoginLog(userInfo);
        
        userInfo.remove("user_pw");
        
        cookieUtil.refreshtoken(request, response, userInfo, Constant.LOGIN_TOKEN_NAME);
        
        log.info("로그인 완료");
    }
}
