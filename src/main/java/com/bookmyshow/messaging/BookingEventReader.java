package com.bookmyshow.messaging;

import com.bookmyshow.event.BookingEventPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Parses booking-events messages. Shared by all consumers so they agree on the format.
 */
@Component
@RequiredArgsConstructor
public class BookingEventReader {

    private final ObjectMapper objectMapper;

    public BookingEventPayload read(String message) {
        try {
            BookingEventPayload event = objectMapper.readValue(message, BookingEventPayload.class);
            if (event.eventId() == null || event.eventType() == null) {
                throw new InvalidEventException("Booking event is missing eventId or eventType", null);
            }
            return event;
        } catch (JsonProcessingException e) {
            throw new InvalidEventException("Malformed booking event: " + e.getOriginalMessage(), e);
        }
    }
}
