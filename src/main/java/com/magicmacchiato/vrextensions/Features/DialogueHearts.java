package com.magicmacchiato.vrextensions.Features;

import com.javic.slimpatch.entity.FamilyVillager;
import com.javic.slimpatch.entity.FemaleVillagerEntity;
import com.javic.slimpatch.entity.MaleVillagerEntity;
import com.javic.slimpatch.entity.VillagerRelationshipData;
import com.javic.slimpatch.network.RelationshipSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.LOGGER;

/** Turns the mood change caused by a heard line into the listener's hearts for whoever said it. */
public final class DialogueHearts {
    private DialogueHearts() {}

    /** MOCK - hearts gained per point of total mood change. */
    public static final float HEART_SCALE = 0.1f;

    /**
     * Villager version of {@link #apply}: same amount, added to the listener's stored relationship for the speaker.
     * Likewise it moves within the normal range only (0..5) and leaves golden hearts untouched.
     */
    public static void applyBetweenVillagers(Villager listener, Villager speaker, float moodChange) {
        if (moodChange == 0f || !Float.isFinite(moodChange)) return;
        if (!(listener instanceof RelationshipHolder holder)) return;
        VillagerRelationship relationship = holder.getVrRelationship();
        UUID target = speaker.getUUID();
        float before = relationship.get(target);
        if (relationship.isGolden(target)) return;
        float after = Math.max(0f, Math.min(VillagerRelationship.NORMAL_MAX_HEARTS, before + moodChange * HEART_SCALE));
        if (after == before) return;
        relationship.set(target, after);
        LOGGER.info("{}: hearts for {}: {} -> {} (mood change {})",
                listener.getUUID(), speaker.getName().getString(), before, after, moodChange);
    }

    /**
     * Adds {@code moodChange * HEART_SCALE} (may be negative) to the base mod's normal hearts (clamped by the base mod
     * to 0..5; golden hearts are not touched) and syncs them to the player.
     *
     * @param moodChange change of positiveness + eagerness + security ({@link Mood#total()}) caused by the choice
     */
    public static void apply(Villager villager, ServerPlayer player, float moodChange) {
        if (moodChange == 0f || !Float.isFinite(moodChange)) return;
        float delta = moodChange * HEART_SCALE;

        float global;
        if (villager instanceof MaleVillagerEntity male) global = male.getRelationship();
        else if (villager instanceof FemaleVillagerEntity female) global = female.getRelationship();
        else return;

        // Same storage rule as the base mod: per player on dedicated/LAN servers, else on the villager.
        float before = VillagerRelationshipData.getRelationshipForPlayer(villager, player.getUUID(), global);
        float after;
        if (VillagerRelationshipData.usesPerPlayerRelationships(villager)) {
            after = VillagerRelationshipData.setRelationshipForPlayer(villager, player.getUUID(), before + delta, global);
        } else if (villager instanceof MaleVillagerEntity male) {
            male.setRelationship(before + delta);
            after = male.getRelationship();
        } else {
            FemaleVillagerEntity female = (FemaleVillagerEntity) villager;
            female.setRelationship(before + delta);
            after = female.getRelationship();
        }

        float golden = VillagerRelationshipData.getGoldenRelationshipForPlayer(villager, player.getUUID(),
                ((FamilyVillager) villager).getGoldenRelationship());
        PacketDistributor.sendToPlayer(player, new RelationshipSyncPacket(villager.getId(), after, golden));
        LOGGER.info("{}: hearts for {}: {} -> {} (mood change {})",
                villager.getUUID(), player.getName().getString(), before, after, moodChange);
    }
}
