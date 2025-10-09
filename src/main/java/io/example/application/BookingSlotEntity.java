package io.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("booking-slot")
public class BookingSlotEntity extends EventSourcedEntity<Timeslot, BookingEvent> {

    private final String entityId;
    private static final Logger logger = LoggerFactory.getLogger(BookingSlotEntity.class);

    public BookingSlotEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    public Effect<Done> markSlotAvailable(Command.MarkSlotAvailable cmd) {
        return effects()
                .persist(new BookingEvent.ParticipantMarkedAvailable(entityId, cmd.participant.id(), cmd.participant.participantType()))
                .thenReply(timeslot -> Done.done());
    }

    public Effect<Done> unmarkSlotAvailable(Command.UnmarkSlotAvailable cmd) {
        return effects()
                .persist(new BookingEvent.ParticipantUnmarkedAvailable(entityId, cmd.participant.id(), cmd.participant.participantType()))
                .thenReply(timeslot -> Done.done());
    }

    // NOTE: booking a slot should produce 3
    // `ParticipantBooked` events
    public Effect<Done> bookSlot(Command.BookReservation cmd) {
        boolean canBook = currentState().isBookable(cmd.studentId, cmd.aircraftId, cmd.instructorId);
        if (canBook)
            return effects()
                    .persistAll(
                            List.of(
                                    new BookingEvent.ParticipantBooked(entityId, cmd.aircraftId, Participant.ParticipantType.AIRCRAFT, cmd.bookingId),
                                    new BookingEvent.ParticipantBooked(entityId, cmd.instructorId, Participant.ParticipantType.INSTRUCTOR, cmd.bookingId),
                                    new BookingEvent.ParticipantBooked(entityId, cmd.studentId, Participant.ParticipantType.STUDENT, cmd.bookingId)
                            ))
                    .thenReply(timeslot -> Done.done());
        else
            return effects().error("Timeslot is not bookable");
    }

    // NOTE: canceling a booking should produce 3
    // `ParticipantCanceled` events
    public Effect<Done> cancelBooking(String bookingId) {
        List<BookingEvent> events = new ArrayList<>();
        for (Timeslot.Booking booking : currentState().findBooking(bookingId)) {
            BookingEvent.ParticipantCanceled participantCanceled = new BookingEvent.ParticipantCanceled(entityId, booking.participant().id(), booking.participant().participantType(), bookingId);
            events.add(participantCanceled);
        }
        return effects()
                .persistAll(events)
                .thenReply(timeslot -> Done.done());

    }

    public ReadOnlyEffect<Timeslot> getSlot() {
        return effects().reply(currentState());
    }

    @Override
    public Timeslot emptyState() {
        return new Timeslot(
                // NOTE: these are just estimates for capacity based on it being a sample
                HashSet.newHashSet(10), HashSet.newHashSet(10));
    }

    @Override
    public Timeslot applyEvent(BookingEvent event) {
        // Supply your own implementation to update state based
        // on the event
        return switch (event) {
            case BookingEvent.ParticipantMarkedAvailable markedAvailable -> currentState().reserve(markedAvailable);
            case BookingEvent.ParticipantUnmarkedAvailable unmarkedAvailable -> currentState().unreserve(unmarkedAvailable);
            case BookingEvent.ParticipantBooked booked ->
                currentState()
                        .book(booked)
                        .unreserve(new BookingEvent.ParticipantUnmarkedAvailable(booked.slotId(), booked.participantId(), booked.participantType()));
            case BookingEvent.ParticipantCanceled cancelled -> currentState().cancelBooking(cancelled.bookingId());
        };
    }

    public sealed interface Command {
        record MarkSlotAvailable(Participant participant) implements Command {
        }

        record UnmarkSlotAvailable(Participant participant) implements Command {
        }

        record BookReservation(
                String studentId, String aircraftId, String instructorId, String bookingId)
                implements Command {
        }
    }
}
