package io.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.example.domain.BookingEvent;
import io.example.application.ParticipantSlotEntity.Commands.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This class is responsible for consuming events from the booking
// slot entity and turning those into command calls on the
// participant slot entity
@ComponentId("booking-slot-consumer")
@Consume.FromEventSourcedEntity(BookingSlotEntity.class)
public class SlotToParticipantConsumer extends Consumer {

    private final ComponentClient client;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SlotToParticipantConsumer(ComponentClient client) {
        this.client = client;
    }

    public Effect onEvent(BookingEvent event) {
        // Supply your own implementation
        String entityId = participantSlotId(event);
        switch (event) {
            case BookingEvent.ParticipantMarkedAvailable markedAvailableEvent -> {
                    MarkAvailable command = new MarkAvailable(markedAvailableEvent.slotId(), markedAvailableEvent.participantId(), markedAvailableEvent.participantType());
                    client
                            .forEventSourcedEntity(entityId)
                            .method(ParticipantSlotEntity::markAvailable)
                            .invoke(command);
            }
            case BookingEvent.ParticipantUnmarkedAvailable unmarkedAvailableEvent -> {
                UnmarkAvailable command = new UnmarkAvailable(unmarkedAvailableEvent.slotId(), unmarkedAvailableEvent.participantId(), unmarkedAvailableEvent.participantType());
                client
                        .forEventSourcedEntity(entityId)
                        .method(ParticipantSlotEntity::unmarkAvailable)
                        .invoke(command);
            }
            case BookingEvent.ParticipantBooked bookedEvent -> {
                Book command = new Book(bookedEvent.slotId(), bookedEvent.participantId(), bookedEvent.participantType(), bookedEvent.bookingId());
                client
                        .forEventSourcedEntity(entityId)
                        .method(ParticipantSlotEntity::book)
                        .invoke(command);
            }
            case BookingEvent.ParticipantCanceled canceledEvent -> {
                Cancel command = new Cancel(canceledEvent.slotId(), canceledEvent.participantId(), canceledEvent.participantType(), canceledEvent.bookingId());
                client
                        .forEventSourcedEntity(entityId)
                        .method(ParticipantSlotEntity::cancel)
                        .invoke(command);
            }
        }
        return effects().done();
    }

    // Participant slots are keyed by a derived key made up of
    // {slotId}-{participantId}
    // We don't need the participant type here because the participant IDs
    // should always be unique/UUIDs
    private String participantSlotId(BookingEvent event) {
        return switch (event) {
            case BookingEvent.ParticipantBooked evt -> evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantUnmarkedAvailable evt ->
                evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantMarkedAvailable evt -> evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantCanceled evt -> evt.slotId() + "-" + evt.participantId();
        };
    }
}
