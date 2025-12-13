package com.cf.login.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.role.service.RoleService;
import com.admin.user.mapper.UserMapper;
import com.admin.user.service.UserService;
import com.smw.auth.service.EmailService;
import com.smw.guild.service.GuildService;
import com.sysconf.constants.Constant;
import com.sysconf.util.CookieUtil;
import com.sysconf.util.DateUtil;
import com.sysconf.util.StringUtil;
import com.sysconf.security.SHA256;

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
    RoleService roleService;
    
    @Autowired
    EmailService emailService;
    
    @Autowired
    GuildService guildService;
    
    @Autowired
    CookieUtil cookieUtil;

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
            
            // 회원가입 데이터 준비
            param.put("user_id", userId);
            if (param.get("password") != null) {
                param.put("user_pw", SHA256.encrypt(StringUtil.nvl(param.get("password").toString())));
            }
            param.put("role_id", Constant.ROLE_GENERAL);
            param.put("usg_yn", param.get("usg_yn") != null && !"".equals(param.get("usg_yn").toString()) 
                    ? param.get("usg_yn").toString() : "Y");
            param.put("del_yn", param.get("del_yn") != null && !"".equals(param.get("del_yn").toString()) 
                    ? param.get("del_yn").toString() : "N");
            
            // 회원가입 시 세션 정보가 없으므로 자기 자신의 user_id를 사용
            param.put("sess_user_id", userId);
            
            // 사용자 및 역할 생성
            userService.insertUserDtl(param);
            roleService.insertUserRole(param);
            
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
        log.info("===== LoginService.login() 시작 =====");
        log.info("로그인 파라미터: user_id={}", param.get("user_id"));
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> userInfo = userService.selectUserInfo(param);
            log.info("사용자 정보 조회 결과: {}", userInfo != null ? "존재함" : "없음");
            
            String errorMessage = validateUser(userInfo);
            if (errorMessage != null) {
                log.warn("사용자 검증 실패: {}", errorMessage);
                result.put("result", errorMessage);
                return result;
            }
            
            String encPwd = SHA256.encrypt(StringUtil.nvl(param.get("password").toString()));
            String userPwd = userInfo.get("user_pw").toString();
            
            if (!encPwd.equals(userPwd)) {
                log.info("==> Password does not match.");
                result.put("result", "PWDNOTMATCHED");
                return result;
            }
            
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
            log.error("로그인 실패", e);
            result.put("result", "FAIL");
            result.put("message", e.getMessage());
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
        String userIp = request.getHeader("X-Forwarded-For");
        if (userIp == null) {
            userIp = request.getRemoteAddr();
        } else {
            userIp = userIp.split(",")[0].trim();
        }
        userInfo.put("ip", userIp);
        
        insertUserLoginLog(userInfo);
        
        userInfo.remove("user_pw");
        
        cookieUtil.refreshtoken(request, response, userInfo, Constant.LOGIN_TOKEN_NAME);
        
        log.info("로그인 완료");
    }
}
