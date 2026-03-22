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

    @PostConstruct
    protected void init() {
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
        signingKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(String userid) throws Exception {
        Claims claims = Jwts.claims().subject(jwtTokenEncryptor.encrypt(userid)).build();
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + tokenValidTime))
                .signWith(signingKey)
                .compact();
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

