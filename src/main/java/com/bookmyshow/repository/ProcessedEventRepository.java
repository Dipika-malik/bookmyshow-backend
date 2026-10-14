package com.bookmyshow.repository;

import com.bookmyshow.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    boolean existsByConsumerAndEventId(String consumer, String eventId);
}
