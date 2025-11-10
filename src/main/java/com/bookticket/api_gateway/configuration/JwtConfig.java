package com.bookticket.api_gateway.configuration;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
@Service
public class JwtConfig {
    private String jwtSecret;
    private long jwtExpiration;
    public SecretKey getJwtSecret() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }
}
