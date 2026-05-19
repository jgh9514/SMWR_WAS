package com.sysconf.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;

@Component
public class JwtTokenProvider {
    private static final Logger logger = LogManager.getLogger(JwtTokenProvider.class);
    @Autowired
    private JwtTokenEncryptor jwtTokenEncryptor;

    @Value("${smw.security.jwt-signing-secret}")
    private String secretKey;

    private SecretKey signingKey;

    @Value("${smw.security.access-token-valid-time-ms:10800000}")
    private long tokenValidTime;

    /** 자동 로그인 JWT·쿠키 유효 기간(초) — smw.cookie-live-time 과 동일 */
    @Value("${smw.cookie-live-time:2592000}")
    private int cookieLiveTimeSeconds;

    private static final String AUTO_LOGIN_CLAIM = "al";

    @PostConstruct
    protected void init() {
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
        signingKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(String userid) throws Exception {
        return createToken(userid, false);
    }

    /**
     * @param autoLogin true 이면 쿠키 live-time 과 동일한 긴 만료, false 이면 access-token-valid-time-ms
     */
    public String createToken(String userid, boolean autoLogin) throws Exception {
        long validityMs = autoLogin
                ? (long) cookieLiveTimeSeconds * 1000L
                : tokenValidTime;
        Claims claims = Jwts.claims()
                .subject(jwtTokenEncryptor.encrypt(userid))
                .add(AUTO_LOGIN_CLAIM, autoLogin)
                .build();
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + validityMs))
                .signWith(signingKey)
                .compact();
    }

    public boolean isAutoLoginToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            Claims claims = parseClaimsAllowingExpired(token);
            return isAutoLoginClaim(claims);
        } catch (Exception e) {
            return false;
        }
    }

    public String getUserIdAllowExpired(String token) throws Exception {
        Claims claims = parseClaimsAllowingExpired(token);
        return getUserIdFromClaims(claims);
    }

    private Claims parseClaimsAllowingExpired(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    private boolean isAutoLoginClaim(Claims claims) {
        Object al = claims.get(AUTO_LOGIN_CLAIM);
        return Boolean.TRUE.equals(al) || "true".equalsIgnoreCase(String.valueOf(al));
    }

    private String getUserIdFromClaims(Claims claims) throws Exception {
        String encryptUserid = claims.getSubject();
        return jwtTokenEncryptor.decrypt(encryptUserid);
    }

    public String createRefreshToken(String userid) throws Exception{
        return null;
    }

    public String getUserIdByToken(String token) throws Exception {
        String encryptUserid = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return jwtTokenEncryptor.decrypt(encryptUserid);
    }

    public String isValidToken(String token){
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return "ACCESS";
        } catch (ExpiredJwtException e) {
            return "EXPIRED";
        } catch (JwtException | IllegalArgumentException e) {
            logger.info("JWT EXCEPTION" + e.toString());
            return "DENIED";
        }
    }
}

