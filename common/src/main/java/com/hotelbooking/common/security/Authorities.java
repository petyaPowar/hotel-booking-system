package com.hotelbooking.common.security;

public final class Authorities {

    public static final String PUBLIC = "permitAll()";
    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String INTERNAL = "hasRole('INTERNAL')";
    public static final String USER_OR_ADMIN = "hasAnyRole('USER', 'ADMIN')";
    public static final String USER_ADMIN_OR_INTERNAL = "hasAnyRole('USER', 'ADMIN', 'INTERNAL')";

    private Authorities() {}
}
