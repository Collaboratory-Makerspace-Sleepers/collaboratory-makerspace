package com.makerspace.backend.model;

public enum Role {
    MEMBER, STAFF, ADMIN, GUEST, INSTRUCTOR, RENTEE, STUDENT;

    /** Returns true for roles that grant management authority (STAFF, ADMIN, INSTRUCTOR).
     *  Customer-type roles (MEMBER, GUEST, STUDENT, RENTEE) must not be used as authority grants. */
    public boolean isAuthorityRole() {
        return this == STAFF || this == ADMIN || this == INSTRUCTOR;
    }
}
