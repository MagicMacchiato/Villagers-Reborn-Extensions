package com.magicmacchiato.vrextensions.Features;

import com.magicmacchiato.vrextensions.Utils.TopicPool;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.LOGGER;

/** Gives villagers their interests. Registered on the NeoForge game bus. */
public class InterestEvents {

    // Fires after saved data is read, for new spawns and loaded villagers alike: only unrolled ones are assigned
    // (new spawns, and villagers saved before interests existed).
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        var entity = event.getEntity();
        if (event.getLevel().isClientSide() || !(entity instanceof InterestHolder holder)) return;
        VillagerInterests interests = holder.getVrInterests();
        if (interests.isAssigned()) return;
        interests.assignRandom(TopicPool.all(), entity.getRandom());
        LOGGER.info("{} is interested in {}", entity.getUUID(), interests.all());
    }
}
