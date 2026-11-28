package com.magicmacchiato.vrextensions.Utils;

import com.javic.slimpatch.entity.FamilyVillager;
import com.javic.slimpatch.entity.FemaleVillagerEntity;
import com.javic.slimpatch.entity.MaleVillagerEntity;
import com.javic.slimpatch.entity.VillagerRelationshipData;
import com.javic.slimpatch.entity.VillagerRelationshipStage;
import com.magicmacchiato.vrextensions.Dialogue.Dialogue;
import com.magicmacchiato.vrextensions.Enums.RelationshipStatus;
import com.magicmacchiato.vrextensions.Features.Mood;
import com.magicmacchiato.vrextensions.Features.MoodHolder;
import com.magicmacchiato.vrextensions.Features.VillagerRelationship;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

import java.util.UUID;
import java.util.function.Predicate;

/** Server-side evaluation of a dialogue node's {@code requirement} for one villager/player pair. */
public final class Requirements {
    private Requirements() {}

    /**
     * A predicate that is true for nodes whose requirements this villager/player pair currently meets: the villager's
     * hearts for the player, the status that follows from them and the base mod's bonds, and the villager's mood.
     */
    public static Predicate<Dialogue> forPair(Villager villager, ServerPlayer player) {
        int hearts = hearts(villager, player);
        RelationshipStatus status = status(villager, player, hearts);
        Mood mood = villager instanceof MoodHolder holder ? holder.getVrMood() : new Mood();
        return node -> node.requirement().isMetBy(hearts, status, mood);
    }

    /** Whole hearts 0..10: the base mod's normal hearts (0..5) plus its golden hearts. */
    private static int hearts(Villager villager, ServerPlayer player) {
        return (int) exactHearts(villager, player);
    }

    /** Like {@link #hearts} but keeping the fraction. */
    public static float exactHearts(Villager villager, ServerPlayer player) {
        float global = villager instanceof MaleVillagerEntity male ? male.getRelationship()
                : villager instanceof FemaleVillagerEntity female ? female.getRelationship() : 0f;
        float hearts = VillagerRelationshipData.getRelationshipForPlayer(villager, player.getUUID(), global);
        if (villager instanceof FamilyVillager family) {
            hearts += VillagerRelationshipData.getGoldenRelationshipForPlayer(villager, player.getUUID(), family.getGoldenRelationship());
        }
        return Math.max(0f, Math.min(VillagerRelationship.MAX_HEARTS, hearts));
    }

    /** Marriage and dating are read the way Villagers Reborn chooses its married/dating dialogue sets for a player. */
    private static RelationshipStatus status(Villager villager, ServerPlayer player, int hearts) {
        if (!(villager instanceof FamilyVillager family)) return RelationshipStatus.resolve(hearts, false, false, false);
        UUID id = player.getUUID();
        boolean perPlayer = VillagerRelationshipData.usesPerPlayerRelationships(villager);
        boolean married = family.getRelationshipStage() == VillagerRelationshipStage.MARRIED;
        boolean partner = married && (!perPlayer || id.equals(family.getSpousePlayerUuid()));
        float golden = VillagerRelationshipData.getGoldenRelationshipForPlayer(villager, id, family.getGoldenRelationship());
        boolean dating = !married
                && (golden > 0.0F || (!perPlayer && family.getRelationshipStage() == VillagerRelationshipStage.DATING));
        return RelationshipStatus.resolve(hearts, id.equals(family.getParentPlayerUuid()), partner, dating);
    }
}
