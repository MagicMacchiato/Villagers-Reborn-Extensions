package com.magicmacchiato.vrextensions.Utils;

import com.javic.slimpatch.entity.FamilyVillager;
import com.javic.slimpatch.entity.FemaleVillagerEntity;
import com.javic.slimpatch.entity.MaleVillagerEntity;
import com.javic.slimpatch.entity.VillagerRelationshipData;
import com.javic.slimpatch.entity.VillagerRelationshipStage;
import com.magicmacchiato.vrextensions.Dialogue.DialogueNode;
import com.magicmacchiato.vrextensions.Features.Mood;
import com.magicmacchiato.vrextensions.Features.MoodHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

import java.util.function.Predicate;

/** Server-side evaluation of a dialogue node's {@code requires} for one villager/player pair. */
public final class Requirements {
    private Requirements() {}

    /** A predicate that is true for nodes whose requirements this villager/player pair currently meets. */
    public static Predicate<DialogueNode> forPair(Villager villager, ServerPlayer player) {
        float relationship = relationship(villager, player);
        VillagerRelationshipStage stage = stage(villager, player);
        Mood mood = villager instanceof MoodHolder holder ? holder.getVrMood() : new Mood();
        return node -> node.requires() == null || node.requires().isMetBy(relationship, stage, mood);
    }

    private static float relationship(Villager villager, ServerPlayer player) {
        float global = villager instanceof MaleVillagerEntity male ? male.getRelationship()
                : villager instanceof FemaleVillagerEntity female ? female.getRelationship() : 0.5f;
        return VillagerRelationshipData.getRelationshipForPlayer(villager, player.getUUID(), global);
    }

    /** Mirrors how Villagers Reborn chooses its married/dating dialogue sets for a player. */
    private static VillagerRelationshipStage stage(Villager villager, ServerPlayer player) {
        if (!(villager instanceof FamilyVillager family)) return VillagerRelationshipStage.FRIENDSHIP;
        boolean perPlayer = VillagerRelationshipData.usesPerPlayerRelationships(villager);

        if (family.getRelationshipStage() == VillagerRelationshipStage.MARRIED) {
            return !perPlayer || player.getUUID().equals(family.getSpousePlayerUuid())
                    ? VillagerRelationshipStage.MARRIED : VillagerRelationshipStage.FRIENDSHIP;
        }
        float golden = VillagerRelationshipData.getGoldenRelationshipForPlayer(villager, player.getUUID(), family.getGoldenRelationship());
        boolean dating = golden > 0.0F || (!perPlayer && family.getRelationshipStage() == VillagerRelationshipStage.DATING);
        return dating ? VillagerRelationshipStage.DATING : VillagerRelationshipStage.FRIENDSHIP;
    }
}
