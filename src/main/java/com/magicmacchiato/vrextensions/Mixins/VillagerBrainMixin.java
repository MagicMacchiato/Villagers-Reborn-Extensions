package com.magicmacchiato.vrextensions.Mixins;

import com.google.common.collect.ImmutableSet;
import com.javic.slimpatch.entity.FamilyVillager;
import com.magicmacchiato.vrextensions.Behaviors.VrVillagerPackages;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-registers the villager brain's activities for the base mod's villagers. Same as vanilla
 * ({@code Villager.registerBrainGoals}, 1.21.1) except the WORK activity is not registered, so the schedule can never
 * leave IDLE for it and villagers stay idle through the working hours. Anything that is not a {@link FamilyVillager}
 * (vanilla villagers, other mods) keeps the untouched vanilla registration.
 * <p>
 * CORE is our own package ({@link VrVillagerPackages#corePackage}): no job-site/POI/profession behaviors, plus the
 * relationship scan.
 */
@Mixin(Villager.class)
public abstract class VillagerBrainMixin {

    @Inject(method = "registerBrainGoals", at = @At("HEAD"), cancellable = true)
    private void vr$registerBrainGoals(Brain<Villager> brain, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (!(self instanceof FamilyVillager)) return;

        VillagerProfession profession = self.getVillagerData().getProfession();
        if (self.isBaby()) {
            brain.setSchedule(Schedule.VILLAGER_BABY);
            brain.addActivity(Activity.PLAY, VillagerGoalPackages.getPlayPackage(0.5F));
        } else {
            brain.setSchedule(Schedule.VILLAGER_DEFAULT);
            // WORK intentionally not registered: villagers stay in IDLE all day.
        }

        brain.addActivity(Activity.CORE, VrVillagerPackages.corePackage(0.5F));
        brain.addActivityWithConditions(
                Activity.MEET,
                VillagerGoalPackages.getMeetPackage(profession, 0.5F),
                ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT))
        );
        brain.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(profession, 0.5F));
        brain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(profession, 0.5F));
        brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(profession, 0.5F));
        brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(profession, 0.5F));
        brain.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(profession, 0.5F));
        brain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(profession, 0.5F));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.updateActivityFromSchedule(self.level().getDayTime(), self.level().getGameTime());
        ci.cancel();
    }

    /** No gossip exchange between villagers (this is also the only caller of the golem spawn check). */
    @Inject(method = "gossip", at = @At("HEAD"), cancellable = true)
    private void vr$noGossip(ServerLevel level, Villager target, long gameTime, CallbackInfo ci) {
        if ((Object) this instanceof FamilyVillager) ci.cancel();
    }

    /** No iron golems summoned by villagers. */
    @Inject(method = "spawnGolemIfNeeded", at = @At("HEAD"), cancellable = true)
    private void vr$noGolemSpawn(ServerLevel level, long gameTime, int minVillagerAmount, CallbackInfo ci) {
        if ((Object) this instanceof FamilyVillager) ci.cancel();
    }
}
