package com.bookmyshow.service;

import com.bookmyshow.enums.BookingEventType;
import com.bookmyshow.event.BookingEventPayload;
import com.bookmyshow.model.Booking;
import com.bookmyshow.model.OutboxEvent;
import com.bookmyshow.model.Show;
import com.bookmyshow.model.ShowSeat;
import com.bookmyshow.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Writes booking events to the outbox table (see OutboxEvent for why).
 *
 * Propagation.MANDATORY: this must run inside the caller's booking transaction —
 * calling it without one is a bug, so Spring throws instead of silently opening
 * a separate transaction (which would break the "booking + event are atomic" guarantee).
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordBookingEvent(BookingEventType type, Booking booking, List<ShowSeat> seats) {
        Show show = booking.getShow();
        String eventId = UUID.randomUUID().toString();

        BookingEventPayload payload = new BookingEventPayload(
                eventId,
                type,
                LocalDateTime.now(),
                booking.getId(),
                booking.getBookingReference(),
                booking.getUser().getId(),
                booking.getUser().getEmail(),
                booking.getUser().getName(),
                show.getId(),
                show.getMovie().getTitle(),
                show.getScreen().getTheatre().getName(),
                show.getShowTime(),
                seats.stream().map(ss -> ss.getSeat().getSeatNumber()).toList(),
                booking.getNumberOfSeats(),
                booking.getTotalAmount());

        outboxEventRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("Booking")
                .aggregateId(booking.getId().toString())
                .eventType(type.name())
                .payload(toJson(payload))
                .build());
    }

    private String toJson(BookingEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize booking event", e);
        }
    }
}
