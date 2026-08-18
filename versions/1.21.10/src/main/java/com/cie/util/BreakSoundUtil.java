package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;

/**
 * Утилита для работы с компонентом {@code minecraft:break_sound} —
 * звук, который проигрывается, когда предмет ломается (например, у
 * тающих предметов или снежков).
 */
public final class BreakSoundUtil {

    private BreakSoundUtil() {
    }

    public static RegistryEntry<SoundEvent> getBreakSound(ItemStack stack) {
        return stack.get(DataComponentTypes.BREAK_SOUND);
    }

    public static void setBreakSound(ItemStack stack, RegistryEntry<SoundEvent> sound) {
        stack.set(DataComponentTypes.BREAK_SOUND, sound);
    }

    public static void removeBreakSound(ItemStack stack) {
        stack.remove(DataComponentTypes.BREAK_SOUND);
    }
}