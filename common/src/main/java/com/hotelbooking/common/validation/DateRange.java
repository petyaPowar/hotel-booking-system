package com.hotelbooking.common.validation;

import java.time.LocalDate;

public interface DateRange {

    LocalDate rangeStart();

    LocalDate rangeEnd();
}
