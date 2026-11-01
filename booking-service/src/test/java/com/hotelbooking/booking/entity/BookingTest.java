package com.hotelbooking.booking.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.hotelbooking.common.error.ConflictException;
import org.junit.jupiter.api.Test;

class BookingTest {

    private static final long ROOM_ID = 7L;

    @Test
    void confirm_movesPendingToConfirmedWithRoom() {
        Booking booking = bookingIn(BookingStatus.PENDING);

        booking.confirm(ROOM_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getRoomId()).isEqualTo(ROOM_ID);
    }

    @Test
    void cancel_movesPendingToCancelled() {
        Booking booking = bookingIn(BookingStatus.PENDING);

        booking.cancel();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancel_movesConfirmedToCancelled() {
        Booking booking = bookingIn(BookingStatus.CONFIRMED);

        booking.cancel();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void confirm_throwsConflict_whenAlreadyConfirmed() {
        Booking booking = bookingIn(BookingStatus.CONFIRMED);

        Throwable thrown = catchThrowable(() -> booking.confirm(ROOM_ID));

        assertThat(thrown).isInstanceOf(ConflictException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirm_throwsConflict_whenCancelled() {
        Booking booking = bookingIn(BookingStatus.CANCELLED);

        Throwable thrown = catchThrowable(() -> booking.confirm(ROOM_ID));

        assertThat(thrown).isInstanceOf(ConflictException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancel_throwsConflict_whenAlreadyCancelled() {
        Booking booking = bookingIn(BookingStatus.CANCELLED);

        Throwable thrown = catchThrowable(booking::cancel);

        assertThat(thrown).isInstanceOf(ConflictException.class);
    }

    private Booking bookingIn(BookingStatus status) {
        return Booking.builder().roomId(1L).status(status).requestId("req").build();
    }
}
