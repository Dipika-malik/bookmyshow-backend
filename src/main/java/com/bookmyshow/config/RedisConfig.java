package com.bookmyshow.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis-backed caching.
 *
 * CACHE-ASIDE PATTERN:
 *   Read  → look in Redis; on a miss, load from MySQL and store the result with a TTL.
 *   Write → evict the affected entries so the next read reloads fresh data.
 *
 * WHAT IS CACHED (and what is not):
 *   movie, movieLists → movie catalogue changes rarely and is read on every home-screen load.
 *   Shows / seat maps are NOT cached: seat availability changes on every booking,
 *   so a cached copy would show seats as free after they were taken.
 *
 * VALUES ARE STORED AS JSON (not Java serialization) so they are readable in redis-cli
 * and not tied to a class's serialVersionUID.
 *
 * FAIL-OPEN: if Redis is unreachable, the CacheErrorHandler logs and swallows the error,
 * so @Cacheable methods simply fall through to MySQL instead of returning 500s.
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    public static final String MOVIE_CACHE = "movie";
    public static final String MOVIE_LISTS_CACHE = "movieLists";

    @Value("${cache.movies.ttl:10m}")
    private Duration moviesTtl;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("bms:cache:")
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper())));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults.entryTtl(moviesTtl))
                .withInitialCacheConfigurations(Map.of(
                        MOVIE_CACHE, defaults.entryTtl(moviesTtl),
                        MOVIE_LISTS_CACHE, defaults.entryTtl(moviesTtl)))
                .build();
    }

    /**
     * Jackson mapper for cache values.
     * Default typing writes the concrete class name into the JSON so a cached
     * List<MovieResponse> comes back as MovieResponse objects, not LinkedHashMaps.
     * The validator only allows our own DTOs and java.* types to be deserialized.
     */
    private ObjectMapper cacheObjectMapper() {
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.bookmyshow.")
                .allowIfSubType("java.")
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis cache GET failed for {}::{} — falling back to DB: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Redis cache PUT failed for {}::{}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis cache EVICT failed for {}::{}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Redis cache CLEAR failed for {}: {}", cache.getName(), e.getMessage());
            }
        };
    }
}
