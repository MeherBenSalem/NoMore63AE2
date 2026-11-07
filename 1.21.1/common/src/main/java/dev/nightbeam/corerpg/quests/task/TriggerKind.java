package dev.nightbeam.corerpg.quests.task;

/** Game events that tasks listen to. Each maps to one {@link TriggerEvent} record. */
public enum TriggerKind {
    BLOCK_BREAK,
    BLOCK_PLACE,
    KILL,
    CRAFT,
    CONSUME,
    FISH,
    TRADE,
    BREED,
    TAME,
    ENCHANT,
    ADVANCEMENT,
    DIMENSION,
    INTERACT_BLOCK,
    INTERACT_ENTITY,
    INVENTORY,
    STAGE,
    CUSTOM
}
