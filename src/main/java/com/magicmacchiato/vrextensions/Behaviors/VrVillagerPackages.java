package com.magicmacchiato.vrextensions.Behaviors;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.GoToWantedItem;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.behavior.LookAndFollowTradingPlayerSink;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.ReactToBell;
import net.minecraft.world.entity.ai.behavior.SetRaidStatus;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.behavior.VillagerPanicTrigger;
import net.minecraft.world.entity.ai.behavior.WakeUp;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;

import java.util.Optional;

/** Our own behavior packages for the base mod's villagers. */
public final class VrVillagerPackages {
    private VrVillagerPackages() {
    }

    /**
     * Vanilla 1.21.1 {@code VillagerGoalPackages.getCorePackage} without anything about jobs: no job-site validation,
     * competitor scan, job-site acquisition, walking to a potential job site, yielding a job site, nor assigning or
     * resetting the profession. Home and meeting point acquisition stay. Plus our {@link RandomConversation}.
     */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> corePackage(float speed) {
        return ImmutableList.of(
                Pair.of(0, new Swim(0.8F)),
                Pair.of(0, InteractWithDoor.create()),
                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(0, new VillagerPanicTrigger()),
                Pair.of(0, WakeUp.create()),
                Pair.of(0, ReactToBell.create()),
                Pair.of(0, SetRaidStatus.create()),
                Pair.of(1, new MoveToTargetSink()),
                Pair.of(3, new LookAndFollowTradingPlayerSink(speed)),
                Pair.of(4, new RandomConversation()),
                Pair.of(5, GoToWantedItem.create(speed, false, 4)),
                Pair.of(10, AcquirePoi.create(p -> p.is(PoiTypes.HOME), MemoryModuleType.HOME, false, Optional.of((byte) 14))),
                Pair.of(10, AcquirePoi.create(p -> p.is(PoiTypes.MEETING), MemoryModuleType.MEETING_POINT, true, Optional.of((byte) 14)))
        );
    }
}
