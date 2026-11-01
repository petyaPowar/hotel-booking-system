package com.hotelbooking.booking.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RoomSelectionValidator.class)
public @interface ValidRoomSelection {

    String message() default "is required when autoSelect is false";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
