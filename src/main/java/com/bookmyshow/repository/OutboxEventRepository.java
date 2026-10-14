package com.bookmyshow.repository;

import com.bookmyshow.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Next batch of unpublished events, oldest first, row-locked for this relay.
     *
     * FOR UPDATE SKIP LOCKED (MySQL 8+): if several app instances run the relay,
     * each one skips rows another instance has already locked instead of waiting,
     * so the same event is never picked up by two relays at once.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextUnpublished(@Param("limit") int limit);

    long countByPublishedAtIsNull();
}
