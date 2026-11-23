package com.magicmacchiato.vrextensions.Features;

/** Implemented (via mixin) by villager entities that carry a {@link VillagerRelationship}. */
public interface RelationshipHolder {
    VillagerRelationship getVrRelationship();
}
