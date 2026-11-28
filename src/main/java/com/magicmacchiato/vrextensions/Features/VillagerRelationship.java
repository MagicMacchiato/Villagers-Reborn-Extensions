package com.magicmacchiato.vrextensions.Features;

import com.javic.slimpatch.entity.FamilyVillager;
import com.magicmacchiato.vrextensions.Enums.RelationshipStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What one villager (the establisher) thinks of other entities, as a {@link RelationshipStatusData} (hearts + bond flags) per target UUID. Directed: A's entry for B
 * is independent of B's entry for A. Targets can be players or villagers. Hearts are a number in
 * 0..{@link #MAX_HEARTS} (fractions add up; requirements and statuses use whole hearts): 0-5 is the normal range, 6-10 the golden range. Absent targets count as 0.
 */
public class VillagerRelationship {
    private static final String KEY = "vrextensions:relationship";
    private static final String ID_KEY = "id";

    public static final int MIN_HEARTS = 0;
    /** Highest heart of the normal range; anything above is golden. */
    public static final int NORMAL_MAX_HEARTS = 5;
    public static final int MAX_HEARTS = 10;

    private final Map<UUID, RelationshipStatusData> data = new HashMap<>();

    /** Data for the target, or null if none is stored. */
    public RelationshipStatusData getData(UUID target) {
        return this.data.get(target);
    }

    /** Data for the target, created and stored if absent. */
    public RelationshipStatusData getOrCreate(UUID target) {
        return this.data.computeIfAbsent(target, id -> new RelationshipStatusData());
    }

    /** Hearts this villager has for the target, 0 if unknown. */
    public float get(UUID target) {
        RelationshipStatusData d = this.data.get(target);
        return d == null ? 0 : d.getHearts();
    }

    /** Sets the hearts, clamped to 0..MAX_HEARTS. An entry left with no hearts and no bonds is dropped. */
    public void set(UUID target, float value) {
        RelationshipStatusData d = getOrCreate(target);
        d.setHearts(value);
        if (d.isEmpty()) this.data.remove(target);
    }

    /** Adds {@code delta} (may be negative) to the hearts, clamped. */
    public void adjust(UUID target, float delta) {
        set(target, get(target) + delta);
    }

    public boolean isGolden(UUID target) {
        return get(target) > NORMAL_MAX_HEARTS;
    }

    /**
     * How {@code self} (the villager this relationship belongs to) regards the target. Refreshes the target's stored
     * bond flags first. Family: the target is one of self's parents, or self is one of the target's parents (looked up
     * in the level if loaded). Partner: the target is self's spouse (player or villager). Dating: self's stage is
     * DATING and the target is in the golden range.
     */
    public RelationshipStatus statusOf(Villager self, UUID target) {
        refreshBonds(self, target);
        RelationshipStatusData d = this.data.get(target);
        return d == null ? RelationshipStatus.STRANGER : d.status();
    }

    /** Recomputes the bond flags for the target from the base mod's family data and stores them. */
    public void refreshBonds(Villager self, UUID target) {
        if (!(self instanceof FamilyVillager fv)) return;
        boolean family = target.equals(fv.getParentPlayerUuid()) || target.equals(fv.getParentVillagerUuid())
                || target.equals(fv.getParentVillager2Uuid()) || isParentOf(self, target);
        boolean partner = target.equals(fv.getSpousePlayerUuid()) || target.equals(fv.getSpouseVillagerUuid());
        boolean dating = fv.isDating();
        RelationshipStatusData d = this.data.get(target);
        if (d == null && !family && !partner && !dating) return;
        if (d == null) d = getOrCreate(target);
        d.setBonds(family, partner, dating);
        if (d.isEmpty()) this.data.remove(target);
    }

    private static boolean isParentOf(Villager self, UUID target) {
        if (!(self.level() instanceof ServerLevel level)) return false;
        if (!(level.getEntity(target) instanceof FamilyVillager child)) return false;
        UUID id = self.getUUID();
        return id.equals(child.getParentVillagerUuid()) || id.equals(child.getParentVillager2Uuid());
    }

    public void remove(UUID target) {
        this.data.remove(target);
    }

    /** Read-only view of every stored target. */
    public Map<UUID, RelationshipStatusData> all() {
        return Collections.unmodifiableMap(this.data);
    }

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        this.data.forEach((id, d) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(ID_KEY, id);
            d.save(entry);
            list.add(entry);
        });
        tag.put(KEY, list);
    }

    public void load(CompoundTag tag) {
        this.data.clear();
        if (!tag.contains(KEY, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID(ID_KEY)) continue;
            RelationshipStatusData d = RelationshipStatusData.load(entry);
            if (!d.isEmpty()) this.data.put(entry.getUUID(ID_KEY), d);
        }
    }
}
