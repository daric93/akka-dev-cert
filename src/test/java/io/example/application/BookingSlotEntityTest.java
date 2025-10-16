package io.example.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import akka.Done;
import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class BookingSlotEntityTest {

  Participant studentParticipant = new Participant("Anna", Participant.ParticipantType.STUDENT);
  Participant instructorParticipant =
      new Participant("Fiona", Participant.ParticipantType.INSTRUCTOR);
  Participant aircraftParticipant = new Participant("GB", Participant.ParticipantType.AIRCRAFT);

  String booking_1 = "bookingA";
  String booking_2 = "bookingB";

  @Test
  void testInitialSlotState() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);
    var state = testKit.getState();

    assertNotNull(state);
    assertTrue(state.available().isEmpty());
    assertTrue(state.bookings().isEmpty());
  }

  @Test
  void testMarkSlotAvailablePersistEventAndReplies() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);

    var command = new BookingSlotEntity.Command.MarkSlotAvailable(studentParticipant);
    var result = testKit.method(BookingSlotEntity::markSlotAvailable).invoke(command);
    assertEquals(Done.getInstance(), result.getReply());

    assertEquals(1, result.getAllEvents().size());
    var event = result.getNextEventOfType(BookingEvent.ParticipantMarkedAvailable.class);
    assertEquals(studentParticipant.id(), event.participantId());
    assertEquals(studentParticipant.participantType(), event.participantType());
    assertEquals("testkit-entity-id", event.slotId());

    assertEquals(1, testKit.getState().available().size());
    assertEquals(Set.of(studentParticipant), testKit.getState().available());
  }

  @Test
  void testMarkSlotAvailableAgainPersistEventAndReplies() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);

    var command = new BookingSlotEntity.Command.MarkSlotAvailable(studentParticipant);
    var result_1 = testKit.method(BookingSlotEntity::markSlotAvailable).invoke(command);
    assertEquals(Done.getInstance(), result_1.getReply());
    assertEquals(1, result_1.getAllEvents().size());

    var result_2 = testKit.method(BookingSlotEntity::markSlotAvailable).invoke(command);
    assertEquals(Done.getInstance(), result_2.getReply());
    assertEquals(1, result_2.getAllEvents().size());

    assertEquals(1, testKit.getState().available().size());
    assertEquals(Set.of(studentParticipant), testKit.getState().available());
  }

  @Test
  void testMarkSlotAvailableWhenThereIsBookingReturnsError() {}

  @Test
  void testUnmarkSlotAvailablePersistEventAndRepliesPreviouslyUnavailableSlot() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);

    var command = new BookingSlotEntity.Command.UnmarkSlotAvailable(studentParticipant);
    var result = testKit.method(BookingSlotEntity::unmarkSlotAvailable).invoke(command);
    assertEquals(Done.getInstance(), result.getReply());

    assertEquals(1, result.getAllEvents().size());
    var event = result.getNextEventOfType(BookingEvent.ParticipantUnmarkedAvailable.class);
    assertEquals(studentParticipant.id(), event.participantId());
    assertEquals(studentParticipant.participantType(), event.participantType());
    assertEquals("testkit-entity-id", event.slotId());

    assertEquals(0, testKit.getState().available().size());
  }

  @Test
  void testUnmarkSlotAvailablePersistEventAndRepliesPreviouslyAvailableSlot() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);

    var markAvailableCommand = new BookingSlotEntity.Command.MarkSlotAvailable(studentParticipant);
    testKit.method(BookingSlotEntity::markSlotAvailable).invoke(markAvailableCommand);

    assertEquals(1, testKit.getState().available().size());
    assertEquals(Set.of(studentParticipant), testKit.getState().available());

    var markUnavailableCommand =
        new BookingSlotEntity.Command.UnmarkSlotAvailable(studentParticipant);
    var result =
        testKit.method(BookingSlotEntity::unmarkSlotAvailable).invoke(markUnavailableCommand);
    assertEquals(Done.getInstance(), result.getReply());

    assertEquals(1, result.getAllEvents().size());
    var event = result.getNextEventOfType(BookingEvent.ParticipantUnmarkedAvailable.class);
    assertEquals(studentParticipant.id(), event.participantId());
    assertEquals(studentParticipant.participantType(), event.participantType());
    assertEquals("testkit-entity-id", event.slotId());

    assertEquals(0, testKit.getState().available().size());
    assertEquals(Set.of(), testKit.getState().available());
  }

  @Test
  void testBookSlotPersistEventAndReplies() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);

    var bookSlotCommand =
        new BookingSlotEntity.Command.BookReservation(
            studentParticipant.id(),
            aircraftParticipant.id(),
            instructorParticipant.id(),
            booking_1);
    var expectedEvents =
        List.of(
            new BookingEvent.ParticipantBooked(
                "testkit-entity-id",
                studentParticipant.id(),
                studentParticipant.participantType(),
                booking_1),
            new BookingEvent.ParticipantBooked(
                "testkit-entity-id",
                instructorParticipant.id(),
                instructorParticipant.participantType(),
                booking_1),
            new BookingEvent.ParticipantBooked(
                "testkit-entity-id",
                aircraftParticipant.id(),
                aircraftParticipant.participantType(),
                booking_1));
    var expectedBookings =
        Set.of(
            new Timeslot.Booking(studentParticipant, booking_1),
            new Timeslot.Booking(instructorParticipant, booking_1),
            new Timeslot.Booking(aircraftParticipant, booking_1));

    testKit
        .method(BookingSlotEntity::markSlotAvailable)
        .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(studentParticipant));
    testKit
        .method(BookingSlotEntity::markSlotAvailable)
        .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(aircraftParticipant));
    testKit
        .method(BookingSlotEntity::markSlotAvailable)
        .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(instructorParticipant));

    assertEquals(3, testKit.getState().available().size());

    var result = testKit.method(BookingSlotEntity::bookSlot).invoke(bookSlotCommand);
    assertEquals(Done.getInstance(), result.getReply());
    assertThat(result.getAllEvents()).containsExactlyInAnyOrderElementsOf(expectedEvents);

    assertEquals(3, testKit.getState().bookings().size());
    assertThat(testKit.getState().bookings()).containsExactlyInAnyOrderElementsOf(expectedBookings);
    assertTrue(testKit.getState().available().isEmpty());
  }

  @Test
  void testBookSlotRepliesWithErrorIfNotAllAvailable() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);

    var bookSlotCommand =
        new BookingSlotEntity.Command.BookReservation(
            studentParticipant.id(),
            aircraftParticipant.id(),
            instructorParticipant.id(),
            booking_1);
    testKit
        .method(BookingSlotEntity::markSlotAvailable)
        .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(studentParticipant));
    testKit
        .method(BookingSlotEntity::markSlotAvailable)
        .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(aircraftParticipant));

    assertEquals(2, testKit.getState().available().size());

    var result = testKit.method(BookingSlotEntity::bookSlot).invoke(bookSlotCommand);
    assertTrue(result.isError());

    assertEquals(2, testKit.getState().available().size());
    assertTrue(testKit.getState().bookings().isEmpty());
  }

  @Test
  void testCancelBookingPersistEventsForAllAndReplies() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);

    var bookSlotCommand =
        new BookingSlotEntity.Command.BookReservation(
            studentParticipant.id(),
            aircraftParticipant.id(),
            instructorParticipant.id(),
            booking_1);
    var expectedEvents =
        List.of(
            new BookingEvent.ParticipantCanceled(
                "testkit-entity-id",
                studentParticipant.id(),
                studentParticipant.participantType(),
                booking_1),
            new BookingEvent.ParticipantCanceled(
                "testkit-entity-id",
                instructorParticipant.id(),
                instructorParticipant.participantType(),
                booking_1),
            new BookingEvent.ParticipantCanceled(
                "testkit-entity-id",
                aircraftParticipant.id(),
                aircraftParticipant.participantType(),
                booking_1));

    // mark available
    testKit
        .method(BookingSlotEntity::markSlotAvailable)
        .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(studentParticipant));
    testKit
        .method(BookingSlotEntity::markSlotAvailable)
        .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(aircraftParticipant));
    testKit
        .method(BookingSlotEntity::markSlotAvailable)
        .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(instructorParticipant));

    assertEquals(3, testKit.getState().available().size());

    // book timeslot
    testKit.method(BookingSlotEntity::bookSlot).invoke(bookSlotCommand);

    assertEquals(3, testKit.getState().bookings().size());

    // cancel timeslot booking
    var result = testKit.method(BookingSlotEntity::cancelBooking).invoke(booking_1);
    assertEquals(Done.getInstance(), result.getReply());
    assertThat(result.getAllEvents()).containsExactlyInAnyOrderElementsOf(expectedEvents);
    assertTrue(testKit.getState().available().isEmpty());
    assertTrue(testKit.getState().bookings().isEmpty());
  }

  @Test
  void testCancelRepliesWithoutEventsPersistedIfNoBooking() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);

    var bookSlotCommand =
        new BookingSlotEntity.Command.BookReservation(
            studentParticipant.id(),
            aircraftParticipant.id(),
            instructorParticipant.id(),
            booking_1);
    var expectedEvents =
        List.of(
            new BookingEvent.ParticipantCanceled(
                "testkit-entity-id",
                studentParticipant.id(),
                studentParticipant.participantType(),
                booking_1),
            new BookingEvent.ParticipantCanceled(
                "testkit-entity-id",
                instructorParticipant.id(),
                instructorParticipant.participantType(),
                booking_1),
            new BookingEvent.ParticipantCanceled(
                "testkit-entity-id",
                aircraftParticipant.id(),
                aircraftParticipant.participantType(),
                booking_1));

    // mark available
    testKit
        .method(BookingSlotEntity::markSlotAvailable)
        .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(studentParticipant));
    testKit
        .method(BookingSlotEntity::markSlotAvailable)
        .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(aircraftParticipant));
    testKit
        .method(BookingSlotEntity::markSlotAvailable)
        .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(instructorParticipant));

    assertEquals(3, testKit.getState().available().size());

    // book timeslot
    testKit.method(BookingSlotEntity::bookSlot).invoke(bookSlotCommand);

    assertEquals(3, testKit.getState().bookings().size());

    // cancel timeslot booking
    var result = testKit.method(BookingSlotEntity::cancelBooking).invoke(booking_1);
    assertEquals(Done.getInstance(), result.getReply());
    assertThat(result.getAllEvents()).containsExactlyInAnyOrderElementsOf(expectedEvents);
    assertTrue(testKit.getState().available().isEmpty());
    assertTrue(testKit.getState().bookings().isEmpty());
  }
}
