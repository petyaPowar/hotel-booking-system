package com.hotelbooking.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, DateRange> {

    private String field;

    @Override
    public void initialize(ValidDateRange annotation) {
        this.field = annotation.field();
    }

    @Override
    public boolean isValid(DateRange range, ConstraintValidatorContext context) {
        if (range == null || range.rangeStart() == null || range.rangeEnd() == null) {
            return true;
        }
        if (range.rangeEnd().isAfter(range.rangeStart())) {
            return true;
        }
        ConstraintViolations.attachToField(context, field);
        return false;
    }
}
