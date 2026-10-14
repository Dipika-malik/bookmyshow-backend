package com.bookmyshow.service;

import com.bookmyshow.dto.request.SeatHoldRequest;
import com.bookmyshow.dto.response.SeatHoldResponse;
import com.bookmyshow.enums.ShowSeatStatus;
import com.bookmyshow.event.BookingConfirmedEvent;
import com.bookmyshow.exception.SeatHoldUnavailableException;
import com.bookmyshow.exception.SeatNotAvailableException;
import com.bookmyshow.model.ShowSeat;
import com.bookmyshow.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Temporary seat holds in Redis — the "seat is in someone's cart" state.
 *
 * WHY REDIS AND NOT THE DB?
 *   A checkout can take minutes (payment page, OTP). Holding a PESSIMISTIC_WRITE row lock
 *   for that long would block every other request touching those rows. Instead:
 *     - Redis holds the seat for a short TTL (default 10 min) with no DB lock held.
 *     - The TTL releases abandoned holds automatically — no cleanup job needed.
 *     - The short DB lock in BookingService stays as the final guarantee against double-booking.
 *
 * KEY DESIGN:  bms:hold:{show:<showId>}:seat:<showSeatId>  →  value = userId
 *   The {show:<id>} hash tag puts all seats of one show in the same Redis Cluster slot,
 *   so the multi-key Lua scripts below still work if Redis is ever sharded.
 *
 * ATOMICITY:
 *   Holding N seats must be all-or-nothing. A Lua script runs atomically on the Redis server,
 *   so "check every seat is free, then set every seat" cannot interleave with another user.
 *
 * FAILURE MODE:
 *   Placing a hold needs Redis (503 if it is down). Everything else fails open:
 *   booking still works through the DB lock, and the seat map falls back to DB status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldService {

    // Returns the 1-based positions of seats held by a different user; holds nothing if any conflict.
    private static final RedisScript<List> HOLD_SCRIPT = new DefaultRedisScript<>("""
            local conflicts = {}
            for i, key in ipairs(KEYS) do
              local owner = redis.call('GET', key)
              if owner and owner ~= ARGV[1] then
                table.insert(conflicts, i)
              end
            end
            if #conflicts > 0 then
              return conflicts
            end
            for _, key in ipairs(KEYS) do
              redis.call('SET', key, ARGV[1], 'PX', ARGV[2])
            end
            return conflicts
            """, List.class);

    // Deletes only the holds owned by this user, so one user can never release another's seats.
    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local released = 0
            for _, key in ipairs(KEYS) do
              if redis.call('GET', key) == ARGV[1] then
                redis.call('DEL', key)
                released = released + 1
              end
            end
            return released
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ShowSeatRepository showSeatRepository;

    @Value("${seat-hold.ttl:10m}")
    private Duration holdTtl;

    @Transactional(readOnly = true)
    public SeatHoldResponse holdSeats(Long userId, SeatHoldRequest request) {
        Long showId = request.getShowId();
        List<Long> seatIds = request.getShowSeatIds().stream().distinct().toList();

        // Only seats that exist for this show and are not already BOOKED can be held
        List<ShowSeat> seats = showSeatRepository.findByShowIdAndIdIn(showId, seatIds);
        if (seats.size() != seatIds.size()) {
            throw new SeatNotAvailableException("One or more seats not found for this show", true);
        }
        List<String> booked = seats.stream()
                .filter(ss -> ss.getStatus() == ShowSeatStatus.BOOKED)
                .map(ss -> ss.getSeat().getSeatNumber())
                .toList();
        if (!booked.isEmpty()) {
            throw new SeatNotAvailableException("Seats already booked: " + booked, true);
        }

        List<Long> conflictPositions;
        try {
            conflictPositions = redisTemplate.execute(HOLD_SCRIPT, holdKeys(showId, seatIds),
                    userId.toString(), String.valueOf(holdTtl.toMillis()));
        } catch (DataAccessException e) {
            throw new SeatHoldUnavailableException("Seat hold service is temporarily unavailable", e);
        }

        if (conflictPositions != null && !conflictPositions.isEmpty()) {
            throw new SeatNotAvailableException("Seats currently held by another user: "
                    + seatNumbers(seats, conflictPositions.stream().map(pos -> seatIds.get(pos.intValue() - 1)).toList()), true);
        }

        return SeatHoldResponse.builder()
                .showId(showId)
                .showSeatIds(seatIds)
                .holdSeconds(holdTtl.toSeconds())
                .expiresAt(LocalDateTime.now().plus(holdTtl))
                .build();
    }

    public long releaseHolds(Long userId, Long showId, List<Long> showSeatIds) {
        try {
            Long released = redisTemplate.execute(RELEASE_SCRIPT, holdKeys(showId, showSeatIds), userId.toString());
            return released == null ? 0 : released;
        } catch (DataAccessException e) {
            // Not fatal: the TTL will release the holds anyway
            log.warn("Could not release seat holds for show {} (TTL will expire them): {}", showId, e.getMessage());
            return 0;
        }
    }

    /**
     * Called by BookingService before taking the DB lock. Rejects seats held by a different user.
     * Seats with no hold are allowed, so clients that skip the hold step still work.
     * If Redis is down this check is skipped — the DB lock alone still prevents double-booking.
     */
    public void assertNotHeldByOthers(Long userId, Long showId, List<Long> showSeatIds) {
        List<String> owners;
        try {
            owners = redisTemplate.opsForValue().multiGet(holdKeys(showId, showSeatIds));
        } catch (DataAccessException e) {
            log.warn("Seat hold check skipped for show {} (Redis unavailable): {}", showId, e.getMessage());
            return;
        }
        if (owners == null) {
            return;
        }

        String me = userId.toString();
        List<Long> heldByOthers = new ArrayList<>();
        for (int i = 0; i < showSeatIds.size(); i++) {
            String owner = owners.get(i);
            if (owner != null && !owner.equals(me)) {
                heldByOthers.add(showSeatIds.get(i));
            }
        }
        if (!heldByOthers.isEmpty()) {
            throw new SeatNotAvailableException("Seats currently held by another user (showSeatIds): " + heldByOthers, true);
        }
    }

    /** Which of these seats are currently held by anyone — used to show them as LOCKED on the seat map. */
    public Set<Long> heldSeatIds(Long showId, List<Long> showSeatIds) {
        if (showSeatIds.isEmpty()) {
            return Set.of();
        }
        List<String> owners;
        try {
            owners = redisTemplate.opsForValue().multiGet(holdKeys(showId, showSeatIds));
        } catch (DataAccessException e) {
            log.warn("Seat map hold overlay skipped for show {} (Redis unavailable): {}", showId, e.getMessage());
            return Set.of();
        }
        Set<Long> held = new HashSet<>();
        for (int i = 0; owners != null && i < showSeatIds.size(); i++) {
            if (owners.get(i) != null) {
                held.add(showSeatIds.get(i));
            }
        }
        return held;
    }

    /** Once a booking is committed, its seats are BOOKED in MySQL, so the holds are no longer needed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        releaseHolds(event.userId(), event.showId(), event.showSeatIds());
    }

    static String holdKey(Long showId, Long showSeatId) {
        return "bms:hold:{show:" + showId + "}:seat:" + showSeatId;
    }

    private List<String> holdKeys(Long showId, List<Long> showSeatIds) {
        return showSeatIds.stream().map(id -> holdKey(showId, id)).toList();
    }

    private List<String> seatNumbers(List<ShowSeat> seats, List<Long> showSeatIds) {
        return seats.stream()
                .filter(ss -> showSeatIds.contains(ss.getId()))
                .map(ss -> ss.getSeat().getSeatNumber())
                .toList();
    }
}
