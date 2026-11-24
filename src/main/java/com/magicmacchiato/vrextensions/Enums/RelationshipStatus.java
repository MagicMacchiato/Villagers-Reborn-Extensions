package com.magicmacchiato.vrextensions.Enums;

/** How a villager regards another entity (player or villager), from least to most close. */
public enum RelationshipStatus {
    STRANGER,
    ACQUAINTANCE,
    FRIEND,
    BEST_FRIEND,
    DATING,
    PARTNER,
    FAMILY;

    /** MOCK - lowest hearts (0..10) of each hearts-based status; below ACQUAINTANCE_MIN_HEARTS is a stranger. */
    public static final int ACQUAINTANCE_MIN_HEARTS = 1;
    public static final int FRIEND_MIN_HEARTS = 3;
    /** Hearts 5 and up, including the whole golden range when the villager is not dating. */
    public static final int BEST_FRIEND_MIN_HEARTS = 5;

    /**
     * Resolves a status. Bonds come first: family, then partner (spouse), then dating (the villager is dating and the
     * target is in the golden range, hearts above 5). Otherwise the hearts decide: 0 stranger, then acquaintance,
     * friend, best friend. MOCK thresholds - to be tuned.
     */
    public static RelationshipStatus resolve(int hearts, boolean family, boolean partner, boolean dating) {
        if (family) return FAMILY;
        if (partner) return PARTNER;
        if (dating && hearts > 5) return DATING;
        if (hearts >= BEST_FRIEND_MIN_HEARTS) return BEST_FRIEND;
        if (hearts >= FRIEND_MIN_HEARTS) return FRIEND;
        if (hearts >= ACQUAINTANCE_MIN_HEARTS) return ACQUAINTANCE;
        return STRANGER;
    }

    /** Display name, e.g. {@code "Best friend"}. Use {@link #name()} for the constant's identifier. */
    @Override
    public String toString() {
        String name = name().replace('_', ' ');
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
