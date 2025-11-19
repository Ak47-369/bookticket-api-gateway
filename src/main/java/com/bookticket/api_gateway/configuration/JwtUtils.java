package com.bookticket.api_gateway.configuration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtUtils {
    private final JwtConfig jwtConfig;

    public JwtUtils(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(jwtConfig.getJwtSecret())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    public String extractRoles(Claims claims) {
        List<Map<String, String>> rolesMap = claims.get("roles", List.class);
        if (rolesMap == null) {
            return "";
        }
        return rolesMap.stream()
                .map(roleMap -> roleMap.get("authority"))
                .collect(Collectors.joining(","));
    }

    public String extractUserId(String token) {
        try {
            if (token == null || token.isEmpty()) {
                return null;
            }
            if (validateToken(token)) {
                return extractSubject(token);
            }
            return null;
        } catch (Exception e) {
            log.debug("Failed to extract user ID from token: {}", e.getMessage());
            return null;
        }
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token) {
        try {
            final String subject = extractSubject(token);
            return (subject != null && !isTokenExpired(token));
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        } catch(ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
            return false;
        }
    }
}
