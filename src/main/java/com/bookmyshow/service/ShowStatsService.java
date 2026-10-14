package com.bookmyshow.service;

import com.bookmyshow.dto.response.ShowStatsResponse;
import com.bookmyshow.enums.BookingEventType;
import com.bookmyshow.event.BookingEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Live per-show sales counters in Redis, fed by booking events from Kafka.
 *
 * KEYS (hash-tagged by show so both live in one Redis Cluster slot):
 *   bms:stats:{show:<id>}                  → hash: ticketsSold, activeBookings, revenuePaise
 *   bms:stats:{show:<id>}:seen:<eventId>   → marker that this event was already applied (7-day TTL)
 *
 * Revenue is stored in paise (integer) because HINCRBY only works on integers
 * and floating-point money drifts.
 */
@Service
@RequiredArgsConstructor
public class ShowStatsService {

    private static final Duration SEEN_TTL = Duration.ofDays(7);

    // Atomic "apply once": mark the event as seen and update the counters in one step.
    // If the marker already exists, the event is a redelivery and nothing changes.
    private static final RedisScript<Long> APPLY_ONCE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[4]) then
              redis.call('HINCRBY', KEYS[2], 'ticketsSold', ARGV[1])
              redis.call('HINCRBY', KEYS[2], 'activeBookings', ARGV[2])
              redis.call('HINCRBY', KEYS[2], 'revenuePaise', ARGV[3])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** @return true if applied, false if this event was already applied before */
    public boolean apply(BookingEventPayload event) {
        int sign = event.eventType() == BookingEventType.BOOKING_CONFIRMED ? 1 : -1;
        long revenuePaise = Math.round(event.totalAmount() * 100);

        Long result = redisTemplate.execute(APPLY_ONCE_SCRIPT,
                List.of(seenKey(event.showId(), event.eventId()), statsKey(event.showId())),
                String.valueOf(sign * event.numberOfSeats()),
                String.valueOf(sign),
                String.valueOf(sign * revenuePaise),
                String.valueOf(SEEN_TTL.toSeconds()));
        return result != null && result == 1L;
    }

    public ShowStatsResponse getStats(Long showId) {
        Map<Object, Object> stats = redisTemplate.opsForHash().entries(statsKey(showId));
        long revenuePaise = parse(stats.get("revenuePaise"));
        return ShowStatsResponse.builder()
                .showId(showId)
                .ticketsSold(parse(stats.get("ticketsSold")))
                .activeBookings(parse(stats.get("activeBookings")))
                .revenue(revenuePaise / 100.0)
                .build();
    }

    private static String statsKey(Long showId) {
        return "bms:stats:{show:" + showId + "}";
    }

    private static String seenKey(Long showId, String eventId) {
        return statsKey(showId) + ":seen:" + eventId;
    }

    private static long parse(Object value) {
        return value == null ? 0 : Long.parseLong(value.toString());
    }
}
