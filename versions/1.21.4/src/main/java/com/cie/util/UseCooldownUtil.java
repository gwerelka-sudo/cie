package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UseCooldownComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Утилита для работы с компонентом {@code minecraft:use_cooldown} —
 * задержка перед повторным использованием предмета и (опционально)
 * группа предметов, которые делят один и тот же таймер отката.
 */
public final class UseCooldownUtil {

    private static final float DEFAULT_SECONDS = 1.0f;

    private UseCooldownUtil() {
    }

    public static UseCooldownComponent getUseCooldown(ItemStack stack) {
        return stack.get(DataComponentTypes.USE_COOLDOWN);
    }

    public static Identifier getGroup(ItemStack stack) {
        UseCooldownComponent comp = getUseCooldown(stack);
        if (comp == null) return null;
        return comp.cooldownGroup().orElse(null);
    }

    public static void setGroup(ItemStack stack, Identifier group) {
        UseCooldownComponent current = getUseCooldown(stack);
        float seconds = current != null ? current.seconds() : DEFAULT_SECONDS;
        stack.set(DataComponentTypes.USE_COOLDOWN, new UseCooldownComponent(seconds, Optional.ofNullable(group)));
    }

    public static float getSeconds(ItemStack stack) {
        UseCooldownComponent comp = getUseCooldown(stack);
        return comp != null ? comp.seconds() : DEFAULT_SECONDS;
    }

    public static void setSeconds(ItemStack stack, float seconds) {
        UseCooldownComponent current = getUseCooldown(stack);
        Optional<Identifier> group = current != null ? current.cooldownGroup() : Optional.empty();
        stack.set(DataComponentTypes.USE_COOLDOWN, new UseCooldownComponent(seconds, group));
    }

    public static void removeUseCooldown(ItemStack stack) {
        stack.remove(DataComponentTypes.USE_COOLDOWN);
    }
}