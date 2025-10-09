package io.example.api;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import io.example.application.BookingSlotEntity;
import io.example.application.ParticipantSlotsView;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import io.grpc.Status;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

public class FlightEndpointIntegrationTest extends TestKitSupport {

    Participant studentParticipant = new Participant("Anna", Participant.ParticipantType.STUDENT);
    Participant instructorParticipant = new Participant("Fiona", Participant.ParticipantType.INSTRUCTOR);
    Participant aircraftParticipant = new Participant("GB", Participant.ParticipantType.AIRCRAFT);

    String booking_1 = "bookingA";
    String booking_2 = "bookingB";

    @Test
    void markSlotAvailableViaHttp() {
        var slotId = "2025-08-08-09";
        var availabilityRequest = new FlightEndpoint.AvailabilityRequest(studentParticipant.id(), studentParticipant.participantType().name());

        var postRequest = httpClient
                .POST("/flight/availability/" + slotId)
                .withRequestBody(availabilityRequest)
                .invoke();

        Assertions.assertEquals(StatusCodes.OK, postRequest.status());

        var getResponse = httpClient
                .GET("/flight/availability/" + slotId)
                .responseBodyAs(Timeslot.class)
                .invoke();

        Assertions.assertEquals(StatusCodes.OK, getResponse.status());
        Assertions.assertEquals(Set.of(studentParticipant), getResponse.body().available());
    }

    @Test
    void markSlotUnavailableViaHttp() {
        var slotId = "2025-08-09-09";
        var availabilityRequest = new FlightEndpoint.AvailabilityRequest(studentParticipant.id(), studentParticipant.participantType().name());

        // mark slot available
        httpClient
                .POST("/flight/availability/" + slotId)
                .withRequestBody(availabilityRequest)
                .invoke();

        var getResponse = httpClient
                .GET("/flight/availability/" + slotId)
                .responseBodyAs(Timeslot.class)
                .invoke();

        Assertions.assertEquals(Set.of(studentParticipant), getResponse.body().available());

        // mark unavailable
        var response = httpClient
                .DELETE("/flight/availability/" + slotId)
                .withRequestBody(availabilityRequest)
                .invoke();

        Assertions.assertEquals(StatusCodes.OK, getResponse.status());

        // check slot
        getResponse = httpClient
                .GET("/flight/availability/" + slotId)
                .responseBodyAs(Timeslot.class)
                .invoke();

        Assertions.assertEquals(Set.of(), getResponse.body().available());

    }

    @Test
    void bookSlotViaHttp() {
        var slotId = "2025-08-08-09";
        var bookingId = "newBooking";

        var availabilityRequestForStudent = new FlightEndpoint.AvailabilityRequest(studentParticipant.id(), studentParticipant.participantType().name());
        var availabilityRequestForInstructor = new FlightEndpoint.AvailabilityRequest(instructorParticipant.id(), instructorParticipant.participantType().name());
        var availabilityRequestForAircraft = new FlightEndpoint.AvailabilityRequest(aircraftParticipant.id(), aircraftParticipant.participantType().name());

        var expectedBookings = Set.of(
                new Timeslot.Booking(studentParticipant, bookingId),
                new Timeslot.Booking(instructorParticipant, bookingId),
                new Timeslot.Booking(aircraftParticipant, bookingId)
        );

        // mark availability for each participant
        httpClient
                .POST("/flight/availability/" + slotId)
                .withRequestBody(availabilityRequestForStudent)
                .invoke();
        httpClient
                .POST("/flight/availability/" + slotId)
                .withRequestBody(availabilityRequestForInstructor)
                .invoke();
        httpClient
                .POST("/flight/availability/" + slotId)
                .withRequestBody(availabilityRequestForAircraft)
                .invoke();

        var getResponse = httpClient
                .GET("/flight/availability/" + slotId)
                .responseBodyAs(Timeslot.class)
                .invoke();

        Assertions.assertEquals(StatusCodes.OK, getResponse.status());
        assertThat(getResponse.body().available()).containsExactlyInAnyOrderElementsOf(Set.of(studentParticipant, instructorParticipant, aircraftParticipant));

        // book
        var bookingRequest = new FlightEndpoint.BookingRequest(studentParticipant.id(), aircraftParticipant.id(), instructorParticipant.id(), bookingId);

        var postResponse = httpClient
                .POST("/flight/bookings/" + slotId)
                .withRequestBody(bookingRequest)
                .invoke();

        Assertions.assertEquals(StatusCodes.CREATED, postResponse.status());

        // check booking
        getResponse = httpClient
                .GET("/flight/availability/" + slotId)
                .responseBodyAs(Timeslot.class)
                .invoke();

        Assertions.assertEquals(StatusCodes.OK, getResponse.status());
        Assertions.assertTrue(getResponse.body().available().isEmpty());
        assertThat(getResponse.body().bookings()).containsExactlyInAnyOrderElementsOf(expectedBookings);
    }

    @Test
    void cancelBookingViaHttp() {
        var slotId = "2025-08-08-09";
        var bookingId = "newBooking";

        var availabilityRequestForStudent = new FlightEndpoint.AvailabilityRequest(studentParticipant.id(), studentParticipant.participantType().name());
        var availabilityRequestForInstructor = new FlightEndpoint.AvailabilityRequest(instructorParticipant.id(), instructorParticipant.participantType().name());
        var availabilityRequestForAircraft = new FlightEndpoint.AvailabilityRequest(aircraftParticipant.id(), aircraftParticipant.participantType().name());

        var expectedBookings = Set.of(
                new Timeslot.Booking(studentParticipant, bookingId),
                new Timeslot.Booking(instructorParticipant, bookingId),
                new Timeslot.Booking(aircraftParticipant, bookingId)
        );

        // mark availability for each participant
        httpClient
                .POST("/flight/availability/" + slotId)
                .withRequestBody(availabilityRequestForStudent)
                .invoke();
        httpClient
                .POST("/flight/availability/" + slotId)
                .withRequestBody(availabilityRequestForInstructor)
                .invoke();
        httpClient
                .POST("/flight/availability/" + slotId)
                .withRequestBody(availabilityRequestForAircraft)
                .invoke();

        var getResponse = httpClient
                .GET("/flight/availability/" + slotId)
                .responseBodyAs(Timeslot.class)
                .invoke();

        Assertions.assertEquals(StatusCodes.OK, getResponse.status());
        assertThat(getResponse.body().available()).containsExactlyInAnyOrderElementsOf(Set.of(studentParticipant, instructorParticipant, aircraftParticipant));

        // book
        var bookingRequest = new FlightEndpoint.BookingRequest(studentParticipant.id(), aircraftParticipant.id(), instructorParticipant.id(), bookingId);

        var postResponse = httpClient
                .POST("/flight/bookings/" + slotId)
                .withRequestBody(bookingRequest)
                .invoke();

        Assertions.assertEquals(StatusCodes.CREATED, postResponse.status());

        // check booking
        getResponse = httpClient
                .GET("/flight/availability/" + slotId)
                .responseBodyAs(Timeslot.class)
                .invoke();

        Assertions.assertEquals(StatusCodes.OK, getResponse.status());
        Assertions.assertTrue(getResponse.body().available().isEmpty());
        assertThat(getResponse.body().bookings()).containsExactlyInAnyOrderElementsOf(expectedBookings);

        // cancel booking
        httpClient
                .DELETE("/flight/bookings/" + slotId + "/" + bookingId)
                .invoke();
        Assertions.assertEquals(StatusCodes.OK, getResponse.status());

        // check cancellation
        // check booking
        getResponse = httpClient
                .GET("/flight/availability/" + slotId)
                .responseBodyAs(Timeslot.class)
                .invoke();

        Assertions.assertEquals(StatusCodes.OK, getResponse.status());
        Assertions.assertTrue(getResponse.body().available().isEmpty());
        Assertions.assertTrue(getResponse.body().bookings().isEmpty());
    }

    @Test
    void getSlotsByStatusViaHttp() {
        var slotId_1 = "2025-01-08-09";
        var slotId_2 = "2025-02-08-09";
        var slotId_3 = "2025-03-08-09";
        var slotId_4 = "2025-04-08-09";

        var availabilityRequestForStudent = new FlightEndpoint.AvailabilityRequest(studentParticipant.id(), studentParticipant.participantType().name());
        var availabilityRequestForInstructor = new FlightEndpoint.AvailabilityRequest(instructorParticipant.id(), instructorParticipant.participantType().name());

        // add availability
        httpClient
                .POST("/flight/availability/" + slotId_1)
                .withRequestBody(availabilityRequestForStudent)
                .invoke();
        httpClient
                .POST("/flight/availability/" + slotId_2)
                .withRequestBody(availabilityRequestForStudent)
                .invoke();
        httpClient
                .POST("/flight/availability/" + slotId_3)
                .withRequestBody(availabilityRequestForStudent)
                .invoke();
        httpClient
                .POST("/flight/availability/" + slotId_3)
                .withRequestBody(availabilityRequestForInstructor)
                .invoke();
        httpClient
                .POST("/flight/availability/" + slotId_4)
                .withRequestBody(availabilityRequestForInstructor)
                .invoke();

        Awaitility.await()
                .ignoreExceptions()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var getResponse = httpClient
                            .GET("/flight/slots/" + studentParticipant.id() + "/available")
                            .responseBodyAs(ParticipantSlotsView.SlotList.class)
                            .invoke();

                    Assertions.assertEquals(StatusCodes.OK, getResponse.status());
                    assertThat(getResponse.body().slots()).containsExactlyInAnyOrderElementsOf(
                            List.of(
                                    new ParticipantSlotsView.SlotRow(slotId_1, studentParticipant.id(), studentParticipant.participantType().name(), "", "available"),
                                    new ParticipantSlotsView.SlotRow(slotId_2, studentParticipant.id(), studentParticipant.participantType().name(), "", "available"),
                                    new ParticipantSlotsView.SlotRow(slotId_3, studentParticipant.id(), studentParticipant.participantType().name(), "", "available")
                            )
                    );
                });
    }
}
