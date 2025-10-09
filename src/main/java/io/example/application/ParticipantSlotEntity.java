package io.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.example.domain.Participant.ParticipantType;

@ComponentId("participant-slot")
public class ParticipantSlotEntity
        extends EventSourcedEntity<ParticipantSlotEntity.State, ParticipantSlotEntity.Event> {

    public Effect<Done> unmarkAvailable(ParticipantSlotEntity.Commands.UnmarkAvailable unmark) {
        // Supply your own implementation
        Event.UnmarkedAvailable event = new Event.UnmarkedAvailable(unmark.slotId, unmark.participantId, unmark.participantType);
        return effects()
                .persist(event)
                .thenReply(state -> Done.done());
    }

    public Effect<Done> markAvailable(ParticipantSlotEntity.Commands.MarkAvailable mark) {
        // Supply your own implementation
        Event.MarkedAvailable event = new Event.MarkedAvailable(mark.slotId, mark.participantId, mark.participantType);
        return effects()
                .persist(event)
                .thenReply(state -> Done.done());
    }

    public Effect<Done> book(ParticipantSlotEntity.Commands.Book book) {
        // Supply your own implementation
        Event.Booked event = new Event.Booked(book.slotId, book.participantId, book.participantType, book.bookingId);
        return effects()
                .persist(event)
                .thenReply(state -> Done.done());
    }

    public Effect<Done> cancel(ParticipantSlotEntity.Commands.Cancel cancel) {
        // Supply your own implementation
        Event.Canceled event = new Event.Canceled(cancel.slotId, cancel.participantId, cancel.participantType, cancel.bookingId);
        return effects()
                .persist(event)
                .thenReply(state -> Done.done());
    }

    record State(
            String slotId, String participantId, ParticipantType participantType, String status) {
    }

    public enum Status {
        booked,
        available
    }

    public sealed interface Commands {
        record MarkAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Commands {
        }

        record UnmarkAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Commands {
        }

        record Book(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Commands {
        }

        record Cancel(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Commands {
        }
    }

    public sealed interface Event {
        @TypeName("marked-available")
        record MarkedAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Event {
        }

        @TypeName("unmarked-available")
        record UnmarkedAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Event {
        }

        @TypeName("participant-booked")
        record Booked(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Event {
        }

        @TypeName("participant-canceled")
        record Canceled(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Event {
        }
    }

    @Override
    public ParticipantSlotEntity.State applyEvent(ParticipantSlotEntity.Event event) {
        // Supply your own implementation
        return switch (event) {
            case Event.MarkedAvailable markedAvailableEvent -> new ParticipantSlotEntity.State(markedAvailableEvent.slotId, markedAvailableEvent.participantId, markedAvailableEvent.participantType, Status.available.name());
            case Event.UnmarkedAvailable unmarkedAvailableEvent -> new ParticipantSlotEntity.State(unmarkedAvailableEvent.slotId, unmarkedAvailableEvent.participantId, unmarkedAvailableEvent.participantType, null);
            case Event.Booked bookedEvet -> new ParticipantSlotEntity.State(bookedEvet.slotId, bookedEvet.participantId, bookedEvet.participantType, Status.booked.name());
            case Event.Canceled canceledEvent -> new ParticipantSlotEntity.State(canceledEvent.slotId, canceledEvent.participantId, canceledEvent.participantType, null);
        };
    }
}
