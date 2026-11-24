package com.magicmacchiato.vrextensions.Features;

import com.magicmacchiato.vrextensions.Enums.RelationshipStatus;
import net.minecraft.nbt.CompoundTag;

/**
 * One villager's data about a single target: hearts (0..10) plus the bond flags that decide its
 * {@link RelationshipStatus}. Asymmetric on purpose - the target's own data about this villager is separate, so a bond
 * (e.g. family) may be stored on both sides redundantly.
 */
public class RelationshipStatusData {
    private static final String HEARTS_KEY = "hearts";
    private static final String FAMILY_KEY = "family";
    private static final String PARTNER_KEY = "partner";
    private static final String DATING_KEY = "dating";

    private int hearts;
    private boolean family;
    private boolean partner;
    private boolean dating;

    public int getHearts() {
        return this.hearts;
    }

    /** Sets the hearts, clamped to 0..MAX_HEARTS. */
    public void setHearts(int value) {
        this.hearts = Math.max(VillagerRelationship.MIN_HEARTS, Math.min(VillagerRelationship.MAX_HEARTS, value));
    }

    public boolean isFamily() {
        return this.family;
    }

    public boolean isPartner() {
        return this.partner;
    }

    public boolean isDating() {
        return this.dating;
    }

    public void setFamily(boolean family) {
        this.family = family;
    }

    public void setPartner(boolean partner) {
        this.partner = partner;
    }

    public void setDating(boolean dating) {
        this.dating = dating;
    }

    /** Sets all three bond flags at once. */
    public void setBonds(boolean family, boolean partner, boolean dating) {
        this.family = family;
        this.partner = partner;
        this.dating = dating;
    }

    public RelationshipStatus status() {
        return RelationshipStatus.resolve(this.hearts, this.family, this.partner, this.dating);
    }

    /** True when nothing is worth storing (no hearts, no bonds). */
    public boolean isEmpty() {
        return this.hearts == 0 && !this.family && !this.partner && !this.dating;
    }

    public void save(CompoundTag tag) {
        tag.putInt(HEARTS_KEY, this.hearts);
        tag.putBoolean(FAMILY_KEY, this.family);
        tag.putBoolean(PARTNER_KEY, this.partner);
        tag.putBoolean(DATING_KEY, this.dating);
    }

    public static RelationshipStatusData load(CompoundTag tag) {
        RelationshipStatusData data = new RelationshipStatusData();
        data.setHearts(tag.getInt(HEARTS_KEY));
        data.setBonds(tag.getBoolean(FAMILY_KEY), tag.getBoolean(PARTNER_KEY), tag.getBoolean(DATING_KEY));
        return data;
    }
}
