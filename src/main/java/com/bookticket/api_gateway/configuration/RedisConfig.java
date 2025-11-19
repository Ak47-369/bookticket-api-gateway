package com.bookticket.api_gateway.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;

import java.io.IOException;
import java.util.List;

@Configuration
@Slf4j
public class RedisConfig {

    private final RedisSerializer<String> keySerializer = new StringRedisSerializer();
    private final RedisSerializer<Object> valueSerializer = new GenericJackson2JsonRedisSerializer();

    /**
     * This @Bean configures the "Manual" RedisTemplate for direct injections.
     * It ensures that any manual operations use JSON serialization for values
     * and String serialization for keys.
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();

        log.info("Primary RedisTemplate configured with JSON serialization");
        return template;
    }

    /**
     * This is a specialized RedisTemplate specifically for executing Lua scripts.
     * It uses StringRedisSerializer for all its parts to ensure that keys and arguments
     * are passed to the Lua script as plain strings, which is what Lua expects.
     * The return type is String to handle script execution results.
     */
    @Bean("scriptRedisTemplate")
    public RedisTemplate<String, String> scriptRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setDefaultSerializer(new StringRedisSerializer()); // Important for args
        template.afterPropertiesSet();

        log.info("Script RedisTemplate configured with String serialization for Lua scripts");
        return template;
    }

    /**
     * Redis script bean for rate limiting using Token Bucket algorithm.
     * The script returns a List with two elements:
     * - Index 0: allowed (1 if request is allowed, 0 if rate limit exceeded)
     * - Index 1: remaining tokens (double value)
     *
     * @return RedisScript that executes the Lua rate limiting logic
     */
    @Bean("rateLimitScript")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RedisScript<List> rateLimitScript() {
        try {
            ClassPathResource resource = new ClassPathResource("scripts/rate_limit.lua");
            ResourceScriptSource scriptSource = new ResourceScriptSource(resource);
            String scriptText = scriptSource.getScriptAsString();
            log.info("Successfully loaded rate limit Lua script from classpath: {}", resource.getPath());
            return (RedisScript<List>) RedisScript.of(scriptText, List.class);
        } catch (IOException e) {
            log.error("Failed to load rate limit Lua script from classpath", e);
            throw new IllegalStateException("Failed to load rate limit Lua script", e);
        }
    }
}

