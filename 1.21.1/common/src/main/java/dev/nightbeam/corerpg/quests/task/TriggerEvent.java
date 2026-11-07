package dev.nightbeam.corerpg.quests.task;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** A game event delivered to the tasks that listen for it. */
public sealed interface TriggerEvent {
    TriggerKind kind();

    record BlockBreak(BlockState state, BlockPos pos, ItemStack tool) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.BLOCK_BREAK;
        }
    }

    record BlockPlace(BlockState state, BlockPos pos) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.BLOCK_PLACE;
        }
    }

    record Kill(Entity entity) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.KILL;
        }
    }

    record Craft(ItemStack stack, int amount) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.CRAFT;
        }
    }

    record Consume(ItemStack stack) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.CONSUME;
        }
    }

    record Fish(List<ItemStack> items) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.FISH;
        }
    }

    record Trade(ItemStack result, Entity merchant) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.TRADE;
        }
    }

    record Breed(Entity child, Entity parent) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.BREED;
        }
    }

    record Tame(Entity animal) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.TAME;
        }
    }

    record Enchant(ItemStack stack, int levels) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.ENCHANT;
        }
    }

    record Advancement(ResourceLocation id, String criterion) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.ADVANCEMENT;
        }
    }

    record Dimension(ResourceKey<Level> to) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.DIMENSION;
        }
    }

    record InteractBlock(BlockState state, BlockPos pos, ItemStack held) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.INTERACT_BLOCK;
        }
    }

    record InteractEntity(Entity entity, ItemStack held) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.INTERACT_ENTITY;
        }
    }

    record Inventory() implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.INVENTORY;
        }
    }

    record Stage(String stage, boolean added) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.STAGE;
        }
    }

    record Custom(String key, long amount) implements TriggerEvent {
        public TriggerKind kind() {
            return TriggerKind.CUSTOM;
        }
    }
}
