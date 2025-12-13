package com.sysconf.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenProvider {
    private static final Logger logger = LogManager.getLogger(JwtTokenProvider.class);
    @Autowired
    private JwtTokenEncryptor jwtTokenEncryptor;

    // toekn secret key
    private String secretKey = "6D97487E2BB22B4FE55EC793BEFB9";

    // token valid time 60 minute
    private long tokenValidTime = 180 * 60 * 1000L;

    @PostConstruct
    protected void init(){
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    public String createToken(String userid) throws Exception {

        return Jwts.builder()
                .setClaims(Jwts.claims().setSubject(jwtTokenEncryptor.encrypt(userid)))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + tokenValidTime))
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public String createRefreshToken(String userid) throws Exception{
        return null;
    }

    public String getUserIdByToken(String token) throws Exception {
        String encryptUserid = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().getSubject();
        return jwtTokenEncryptor.decrypt(encryptUserid);
    }

    public String isValidToken(String token){
        try {
            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
            return "ACCESS";
        } catch (ExpiredJwtException e) {
            return "EXPIRED";
        } catch (JwtException | IllegalArgumentException e) {
            logger.info("JWT EXCEPTION" + e.toString());
            return "DENIED";
        }
    }
}

