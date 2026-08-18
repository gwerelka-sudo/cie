package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlocksAttacksComponent;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Real layout of BlocksAttacksComponent (per compiler feedback):
 *
 *   BlocksAttacksComponent(
 *       float blockDelaySeconds,
 *       float disableCooldownScale,
 *       List<DamageReduction> damageReductions,
 *       BlocksAttacksComponent.ItemDamage itemDamage,
 *       Optional<TagKey<DamageType>> bypassedBy,
 *       Optional<RegistryEntry<SoundEvent>> blockSound,
 *       Optional<RegistryEntry<SoundEvent>> disableSound
 *   )
 *
 *   DamageReduction(float horizontalBlockingAngle, Optional<RegistryEntryList<DamageType>> type, float base, float factor)
 *   ItemDamage(float threshold, float base, float factor)
 */
public class BlockAttacksUtil {

    private static final float DEFAULT_BLOCK_DELAY = 0f;
    private static final float DEFAULT_DISABLE_COOLDOWN_SCALE = 1f;
    private static final float DEFAULT_ITEM_DAMAGE_THRESHOLD = 1f;
    private static final float DEFAULT_ITEM_DAMAGE_BASE = 1f;
    private static final float DEFAULT_ITEM_DAMAGE_FACTOR = 0f;
    private static final float DEFAULT_REDUCTION_ANGLE = 90f;
    private static final float DEFAULT_REDUCTION_BASE = 0f;
    private static final float DEFAULT_REDUCTION_FACTOR = 1f;

    public static void clear(ItemStack stack) {
        stack.remove(DataComponentTypes.BLOCKS_ATTACKS);
    }

    private static TagKey<DamageType> resolveDamageTypeTag(String tagId) {
        return TagKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(tagId));
    }

    private static RegistryEntryList<DamageType> resolveDamageTypeEntryList(String tagId) {
        TagKey<DamageType> tag = resolveDamageTypeTag(tagId);
        net.minecraft.registry.Registry<DamageType> registry =
                MinecraftClient.getInstance().world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE);
        List<RegistryEntry<DamageType>> matching = new ArrayList<>();
        for (DamageType t : registry) {
            RegistryEntry<DamageType> entry = registry.getEntry(t);
            if (entry.isIn(tag)) matching.add(entry);
        }
        return RegistryEntryList.of(matching);
    }

    private static BlocksAttacksComponent current(ItemStack stack) {
        BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
        if (comp != null) return comp;
        BlocksAttacksComponent.ItemDamage defaultItemDamage =
                new BlocksAttacksComponent.ItemDamage(
                        DEFAULT_ITEM_DAMAGE_THRESHOLD, DEFAULT_ITEM_DAMAGE_BASE, DEFAULT_ITEM_DAMAGE_FACTOR);
        return new BlocksAttacksComponent(
                DEFAULT_BLOCK_DELAY,
                DEFAULT_DISABLE_COOLDOWN_SCALE,
                List.of(defaultDamageReduction()),
                defaultItemDamage,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    private static BlocksAttacksComponent.DamageReduction defaultDamageReduction() {
        return new BlocksAttacksComponent.DamageReduction(
                DEFAULT_REDUCTION_ANGLE, Optional.empty(), DEFAULT_REDUCTION_BASE, DEFAULT_REDUCTION_FACTOR);
    }

    private static void write(ItemStack stack, BlocksAttacksComponent comp) {
        stack.set(DataComponentTypes.BLOCKS_ATTACKS, comp);
    }

    private static BlocksAttacksComponent.DamageReduction firstReduction(BlocksAttacksComponent comp) {
        List<BlocksAttacksComponent.DamageReduction> reductions = comp.damageReductions();
        if (reductions == null || reductions.isEmpty()) return defaultDamageReduction();
        return reductions.get(0);
    }

    private static BlocksAttacksComponent withReduction(ItemStack stack, BlocksAttacksComponent.DamageReduction newReduction) {
        BlocksAttacksComponent comp = current(stack);
        List<BlocksAttacksComponent.DamageReduction> list = new ArrayList<>(comp.damageReductions());
        if (list.isEmpty()) {
            list.add(newReduction);
        } else {
            list.set(0, newReduction);
        }
        return new BlocksAttacksComponent(
                comp.blockDelaySeconds(),
                comp.disableCooldownScale(),
                list,
                comp.itemDamage(),
                comp.bypassedBy(),
                comp.blockSound(),
                comp.disableSound()
        );
    }

    // --- blockDelaySeconds ---
    public static class BlockDelaySeconds {
        public static void set(ItemStack stack, int seconds) {
            BlocksAttacksComponent comp = current(stack);
            write(stack, new BlocksAttacksComponent(
                    (float) seconds,
                    comp.disableCooldownScale(),
                    comp.damageReductions(),
                    comp.itemDamage(),
                    comp.bypassedBy(),
                    comp.blockSound(),
                    comp.disableSound()
            ));
        }

        public static int get(ItemStack stack) {
            BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
            if (comp == null) return (int) DEFAULT_BLOCK_DELAY;
            return (int) comp.blockDelaySeconds();
        }

        public static void reset(ItemStack stack) {
            set(stack, (int) DEFAULT_BLOCK_DELAY);
        }
    }

    // --- blockSound ---
    public static class BlockSound {
        public static void set(ItemStack stack, String sound) {
            BlocksAttacksComponent comp = current(stack);
            RegistryEntry<SoundEvent> entry = RegistryEntry.of(SoundEvent.of(Identifier.of(sound)));
            write(stack, new BlocksAttacksComponent(
                    comp.blockDelaySeconds(),
                    comp.disableCooldownScale(),
                    comp.damageReductions(),
                    comp.itemDamage(),
                    comp.bypassedBy(),
                    Optional.of(entry),
                    comp.disableSound()
            ));
        }

        public static String get(ItemStack stack) {
            BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
            if (comp == null || comp.blockSound().isEmpty()) return "";
            RegistryEntry<SoundEvent> entry = comp.blockSound().get();
            return entry.getKey().isPresent() ? entry.getKey().get().getValue().toString() : "";
        }

        public static void reset(ItemStack stack) {
            BlocksAttacksComponent comp = current(stack);
            write(stack, new BlocksAttacksComponent(
                    comp.blockDelaySeconds(),
                    comp.disableCooldownScale(),
                    comp.damageReductions(),
                    comp.itemDamage(),
                    comp.bypassedBy(),
                    Optional.empty(),
                    comp.disableSound()
            ));
        }
    }

    // --- bypassedBy ---
    public static class BypassedBy {
        public static void set(ItemStack stack, String by) {
            BlocksAttacksComponent comp = current(stack);
            TagKey<DamageType> tagList = resolveDamageTypeTag(by);
            write(stack, new BlocksAttacksComponent(
                    comp.blockDelaySeconds(),
                    comp.disableCooldownScale(),
                    comp.damageReductions(),
                    comp.itemDamage(),
                    Optional.of(tagList),
                    comp.blockSound(),
                    comp.disableSound()
            ));
        }

        public static String get(ItemStack stack) {
            BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
            if (comp == null || comp.bypassedBy().isEmpty()) return "";
            return describeTagList(comp.bypassedBy().get());
        }

        public static void reset(ItemStack stack) {
            BlocksAttacksComponent comp = current(stack);
            write(stack, new BlocksAttacksComponent(
                    comp.blockDelaySeconds(),
                    comp.disableCooldownScale(),
                    comp.damageReductions(),
                    comp.itemDamage(),
                    Optional.empty(),
                    comp.blockSound(),
                    comp.disableSound()
            ));
        }
    }

    private static String describeTagList(TagKey<DamageType> tag) {
        return tag.id().toString();
    }

    private static String describeTagList(RegistryEntryList<DamageType> list) {
        if (list instanceof RegistryEntryList.Named<DamageType> named) {
            return named.getTag().id().toString();
        }
        return list.toString();
    }

    // --- damageReducations ---
    public static class DamageReducations {
        public static void clear(ItemStack stack) {
            BlocksAttacksComponent comp = current(stack);
            write(stack, new BlocksAttacksComponent(
                    comp.blockDelaySeconds(),
                    comp.disableCooldownScale(),
                    List.of(defaultDamageReduction()),
                    comp.itemDamage(),
                    comp.bypassedBy(),
                    comp.blockSound(),
                    comp.disableSound()
            ));
        }

        public static class Base {
            public static void set(ItemStack stack, float val) {
                BlocksAttacksComponent.DamageReduction r = firstReduction(current(stack));
                write(stack, withReduction(stack, new BlocksAttacksComponent.DamageReduction(
                        r.horizontalBlockingAngle(), r.type(), val, r.factor())));
            }

            public static float get(ItemStack stack) {
                BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
                if (comp == null) return DEFAULT_REDUCTION_BASE;
                return firstReduction(comp).base();
            }

            public static void reset(ItemStack stack) {
                BlocksAttacksComponent.DamageReduction r = firstReduction(current(stack));
                write(stack, withReduction(stack, new BlocksAttacksComponent.DamageReduction(
                        r.horizontalBlockingAngle(), r.type(), DEFAULT_REDUCTION_BASE, r.factor())));
            }
        }

        public static class Factor {
            public static void set(ItemStack stack, float val) {
                BlocksAttacksComponent.DamageReduction r = firstReduction(current(stack));
                write(stack, withReduction(stack, new BlocksAttacksComponent.DamageReduction(
                        r.horizontalBlockingAngle(), r.type(), r.base(), val)));
            }

            public static float get(ItemStack stack) {
                BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
                if (comp == null) return DEFAULT_REDUCTION_FACTOR;
                return firstReduction(comp).factor();
            }

            public static void reset(ItemStack stack) {
                BlocksAttacksComponent.DamageReduction r = firstReduction(current(stack));
                write(stack, withReduction(stack, new BlocksAttacksComponent.DamageReduction(
                        r.horizontalBlockingAngle(), r.type(), r.base(), DEFAULT_REDUCTION_FACTOR)));
            }
        }

        public static class HorizontalBlockingAngle {
            public static void set(ItemStack stack, float val) {
                BlocksAttacksComponent.DamageReduction r = firstReduction(current(stack));
                write(stack, withReduction(stack, new BlocksAttacksComponent.DamageReduction(
                        val, r.type(), r.base(), r.factor())));
            }

            public static float get(ItemStack stack) {
                BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
                if (comp == null) return DEFAULT_REDUCTION_ANGLE;
                return firstReduction(comp).horizontalBlockingAngle();
            }

            public static void reset(ItemStack stack) {
                BlocksAttacksComponent.DamageReduction r = firstReduction(current(stack));
                write(stack, withReduction(stack, new BlocksAttacksComponent.DamageReduction(
                        DEFAULT_REDUCTION_ANGLE, r.type(), r.base(), r.factor())));
            }
        }

        public static class Type {
            public static void set(ItemStack stack, String type) {
                BlocksAttacksComponent.DamageReduction r = firstReduction(current(stack));
                RegistryEntryList<DamageType> tagList = resolveDamageTypeEntryList(type);
                write(stack, withReduction(stack, new BlocksAttacksComponent.DamageReduction(
                        r.horizontalBlockingAngle(), Optional.of(tagList), r.base(), r.factor())));
            }

            public static String get(ItemStack stack) {
                BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
                if (comp == null) return "";
                Optional<RegistryEntryList<DamageType>> type = firstReduction(comp).type();
                return type.isPresent() ? describeTagList(type.get()) : "";
            }

            public static void reset(ItemStack stack) {
                BlocksAttacksComponent.DamageReduction r = firstReduction(current(stack));
                write(stack, withReduction(stack, new BlocksAttacksComponent.DamageReduction(
                        r.horizontalBlockingAngle(), Optional.empty(), r.base(), r.factor())));
            }
        }
    }

    // --- disableCooldownScale ---
    public static class DisableCooldownScale {
        public static int get(ItemStack stack) {
            BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
            if (comp == null) return (int) DEFAULT_DISABLE_COOLDOWN_SCALE;
            return (int) comp.disableCooldownScale();
        }

        public static void set(ItemStack stack, int scale) {
            BlocksAttacksComponent comp = current(stack);
            write(stack, new BlocksAttacksComponent(
                    comp.blockDelaySeconds(),
                    (float) scale,
                    comp.damageReductions(),
                    comp.itemDamage(),
                    comp.bypassedBy(),
                    comp.blockSound(),
                    comp.disableSound()
            ));
        }

        public static void reset(ItemStack stack) {
            BlocksAttacksComponent comp = current(stack);
            write(stack, new BlocksAttacksComponent(
                    comp.blockDelaySeconds(),
                    DEFAULT_DISABLE_COOLDOWN_SCALE,
                    comp.damageReductions(),
                    comp.itemDamage(),
                    comp.bypassedBy(),
                    comp.blockSound(),
                    comp.disableSound()
            ));
        }
    }

    // --- disabledSound ---
    public static class DisabledSound {
        public static String get(ItemStack stack) {
            BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
            if (comp == null || comp.disableSound().isEmpty()) return "";
            RegistryEntry<SoundEvent> entry = comp.disableSound().get();
            return entry.getKey().isPresent() ? entry.getKey().get().getValue().toString() : "";
        }

        public static void set(ItemStack stack, String sound) {
            BlocksAttacksComponent comp = current(stack);
            RegistryEntry<SoundEvent> entry = RegistryEntry.of(SoundEvent.of(Identifier.of(sound)));
            write(stack, new BlocksAttacksComponent(
                    comp.blockDelaySeconds(),
                    comp.disableCooldownScale(),
                    comp.damageReductions(),
                    comp.itemDamage(),
                    comp.bypassedBy(),
                    comp.blockSound(),
                    Optional.of(entry)
            ));
        }

        public static void reset(ItemStack stack) {
            BlocksAttacksComponent comp = current(stack);
            write(stack, new BlocksAttacksComponent(
                    comp.blockDelaySeconds(),
                    comp.disableCooldownScale(),
                    comp.damageReductions(),
                    comp.itemDamage(),
                    comp.bypassedBy(),
                    comp.blockSound(),
                    Optional.empty()
            ));
        }
    }

    // --- itemDamage ---
    public static class ItemDamage {

        private static BlocksAttacksComponent withItemDamage(ItemStack stack, BlocksAttacksComponent.ItemDamage fn) {
            BlocksAttacksComponent comp = current(stack);
            return new BlocksAttacksComponent(
                    comp.blockDelaySeconds(),
                    comp.disableCooldownScale(),
                    comp.damageReductions(),
                    fn,
                    comp.bypassedBy(),
                    comp.blockSound(),
                    comp.disableSound()
            );
        }

        public static class Base {
            public static void set(ItemStack stack, float val) {
                BlocksAttacksComponent.ItemDamage fn = current(stack).itemDamage();
                write(stack, withItemDamage(stack, new BlocksAttacksComponent.ItemDamage(
                        fn.threshold(), val, fn.factor())));
            }

            public static float get(ItemStack stack) {
                BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
                if (comp == null) return DEFAULT_ITEM_DAMAGE_BASE;
                return comp.itemDamage().base();
            }

            public static void reset(ItemStack stack) {
                BlocksAttacksComponent.ItemDamage fn = current(stack).itemDamage();
                write(stack, withItemDamage(stack, new BlocksAttacksComponent.ItemDamage(
                        fn.threshold(), DEFAULT_ITEM_DAMAGE_BASE, fn.factor())));
            }
        }

        public static class Factor {
            public static void set(ItemStack stack, float val) {
                BlocksAttacksComponent.ItemDamage fn = current(stack).itemDamage();
                write(stack, withItemDamage(stack, new BlocksAttacksComponent.ItemDamage(
                        fn.threshold(), fn.base(), val)));
            }

            public static float get(ItemStack stack) {
                BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
                if (comp == null) return DEFAULT_ITEM_DAMAGE_FACTOR;
                return comp.itemDamage().factor();
            }

            public static void reset(ItemStack stack) {
                BlocksAttacksComponent.ItemDamage fn = current(stack).itemDamage();
                write(stack, withItemDamage(stack, new BlocksAttacksComponent.ItemDamage(
                        fn.threshold(), fn.base(), DEFAULT_ITEM_DAMAGE_FACTOR)));
            }
        }

        public static class Threshold {
            public static void set(ItemStack stack, float val) {
                BlocksAttacksComponent.ItemDamage fn = current(stack).itemDamage();
                write(stack, withItemDamage(stack, new BlocksAttacksComponent.ItemDamage(
                        val, fn.base(), fn.factor())));
            }

            public static float get(ItemStack stack) {
                BlocksAttacksComponent comp = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
                if (comp == null) return DEFAULT_ITEM_DAMAGE_THRESHOLD;
                return comp.itemDamage().threshold();
            }

            public static void reset(ItemStack stack) {
                BlocksAttacksComponent.ItemDamage fn = current(stack).itemDamage();
                write(stack, withItemDamage(stack, new BlocksAttacksComponent.ItemDamage(
                        DEFAULT_ITEM_DAMAGE_THRESHOLD, fn.base(), fn.factor())));
            }
        }
    }
}