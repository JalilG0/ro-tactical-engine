package com.mil.trdss.ro.repository.cache;

import com.mil.trdss.ro.domain.dto.TelemetryHeartbeatDTO;
import com.mil.trdss.ro.domain.enums.AssetStatus;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Repository
public class FleetStatusCacheRepository {

    private static final String FLEET_STATUS_KEY_PREFIX = "fleet:status:";
    private static final String SHADOW_LOCK_KEY_PREFIX = "fleet:lock:";
    private static final String IDEMPOTENCY_KEY_PREFIX = "event:idempotent:";

    // Absolute Rule (b): the Shadow Lock TTL is architecturally fixed at 30s
    // regardless of what a caller passes in.
    private static final Duration SHADOW_LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofSeconds(10);

    private final RedisTemplate<String, TelemetryHeartbeatDTO> telemetryRedisTemplate;
    private final RedisTemplate<String, String> stringRedisTemplate;

    public FleetStatusCacheRepository(RedisConnectionFactory connectionFactory) {
        this.telemetryRedisTemplate = buildTelemetryTemplate(connectionFactory);
        this.stringRedisTemplate = buildStringTemplate(connectionFactory);
    }

    private static RedisTemplate<String, TelemetryHeartbeatDTO> buildTelemetryTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, TelemetryHeartbeatDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JacksonJsonRedisSerializer<>(TelemetryHeartbeatDTO.class));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new JacksonJsonRedisSerializer<>(TelemetryHeartbeatDTO.class));
        template.afterPropertiesSet();
        return template;
    }

    private static RedisTemplate<String, String> buildStringTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    public void saveTelemetry(TelemetryHeartbeatDTO telemetry) {
        String key = FLEET_STATUS_KEY_PREFIX + telemetry.assetId();
        telemetryRedisTemplate.opsForValue().set(key, telemetry);
    }

    public List<TelemetryHeartbeatDTO> getActiveFreePool() {
        List<String> keys = scanKeys(FLEET_STATUS_KEY_PREFIX + "*");
        if (keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .map(key -> telemetryRedisTemplate.opsForValue().get(key))
                .filter(telemetry -> telemetry != null && telemetry.status() == AssetStatus.FREE)
                .toList();
    }

    // Uses SCAN rather than KEYS: KEYS blocks the whole Redis instance while it
    // walks the keyspace, which doesn't scale once the fleet pool grows.
    private List<String> scanKeys(String pattern) {
        List<String> result = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
        try (Cursor<byte[]> cursor = telemetryRedisTemplate.execute((RedisConnection connection) -> connection.scan(options))) {
            while (cursor != null && cursor.hasNext()) {
                result.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    public void applyShadowLock(String assetId) {
        String key = SHADOW_LOCK_KEY_PREFIX + assetId;
        stringRedisTemplate.opsForValue().set(key, assetId, SHADOW_LOCK_TTL);
    }

    public boolean isIdempotent(String eventId) {
        String key = IDEMPOTENCY_KEY_PREFIX + eventId;
        Boolean firstSeen = stringRedisTemplate.opsForValue().setIfAbsent(key, eventId, IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(firstSeen);
    }
}
