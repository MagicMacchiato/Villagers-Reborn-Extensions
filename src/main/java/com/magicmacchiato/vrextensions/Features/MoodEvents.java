package com.magicmacchiato.vrextensions.Features;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/** World events that move a villager's mood. Registered on the NeoForge game bus. */
public class MoodEvents {

    // Any health regeneration (Villagers Reborn passive heal, potions, ...) raises security.
    // LOWEST + receiveCanceled=false: see the final amount, skip cancelled heals.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !(entity instanceof MoodHolder holder)) return;

        float before = entity.getHealth();
        float max = entity.getMaxHealth();
        float healed = Math.min(event.getAmount(), max - before);   // heal is capped by max health
        if (healed <= 0f) return;

        Mood mood = holder.getVrMood();
        mood.applyHeal(healed, max, before + healed);
        mood.logState(entity.getUUID());
        if (entity instanceof net.minecraft.world.entity.npc.Villager villager) MoodSync.sendToDialoguePlayer(villager);
    }
}
