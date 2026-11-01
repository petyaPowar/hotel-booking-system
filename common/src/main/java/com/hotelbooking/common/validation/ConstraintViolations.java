package com.hotelbooking.common.validation;

import jakarta.validation.ConstraintValidatorContext;

public final class ConstraintViolations {

    private ConstraintViolations() {}

    public static void attachToField(ConstraintValidatorContext context, String field) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
