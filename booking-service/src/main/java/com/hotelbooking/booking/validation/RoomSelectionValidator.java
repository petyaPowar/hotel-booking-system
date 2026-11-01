package com.hotelbooking.booking.validation;

import com.hotelbooking.booking.dto.CreateBookingRequest;
import com.hotelbooking.common.validation.ConstraintViolations;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RoomSelectionValidator implements ConstraintValidator<ValidRoomSelection, CreateBookingRequest> {

    private static final String ROOM_ID_FIELD = "roomId";

    @Override
    public boolean isValid(CreateBookingRequest request, ConstraintValidatorContext context) {
        if (request == null || request.autoSelect() || request.roomId() != null) {
            return true;
        }
        ConstraintViolations.attachToField(context, ROOM_ID_FIELD);
        return false;
    }
}
