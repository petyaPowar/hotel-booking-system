package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.dto.HotelFilter;
import com.hotelbooking.hotel.entity.Hotel;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class HotelSpecifications {

    private HotelSpecifications() {}

    public static Specification<Hotel> matching(HotelFilter filter) {
        return Specification.allOf(
                containsIgnoreCase("name", filter.name()), containsIgnoreCase("address", filter.address()));
    }

    private static Specification<Hotel> containsIgnoreCase(String attribute, String value) {
        if (!StringUtils.hasText(value)) {
            return Specification.unrestricted();
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(attribute)), pattern);
    }
}
