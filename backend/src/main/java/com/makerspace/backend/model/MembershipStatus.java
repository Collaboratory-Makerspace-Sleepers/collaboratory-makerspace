package com.makerspace.backend.model;

import java.util.EnumSet;
import java.util.Set;

public enum MembershipStatus {
    NONE,
    ACTIVE,
    TRIALING,
    PAST_DUE,
    GRACE,
    CANCELED,
    INCOMPLETE,
    INCOMPLETE_EXPIRED,
    EXPIRED,
    UNPAID,
    PAUSED;

    public static final Set<MembershipStatus> BOOKING_STATUSES =
            EnumSet.of(MembershipStatus.ACTIVE, MembershipStatus.TRIALING, MembershipStatus.PAST_DUE);
}
