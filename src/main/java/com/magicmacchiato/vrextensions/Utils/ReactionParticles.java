package com.magicmacchiato.vrextensions.Utils;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class ReactionParticles {
    private ReactionParticles() {}

    /**
     * Same effects as Villagers Reborn's success/fail: green happy particles (7 bursts) for a positive reaction,
     * angry particles (4 bursts) for a negative one, scattered horizontally (gaussian * 0.3) around the villager at
     * y + 1. Mirrors playPositiveReactionFeedback / playNegativeReactionFeedback in the base mod. Nothing at 0.
     */
    public static void spawn(ServerLevel level, Villager villager, float reaction) {
        if (reaction == 0f || Float.isNaN(reaction)) return;
        boolean positive = reaction > 0f;
        SimpleParticleType type = positive ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ANGRY_VILLAGER;
        int bursts = positive ? 7 : 4;
        for (int i = 0; i < bursts; i++) {
            level.sendParticles(type,
                    villager.getX() + villager.getRandom().nextGaussian() * 0.3D,
                    villager.getY() + 1.0D,
                    villager.getZ() + villager.getRandom().nextGaussian() * 0.3D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
