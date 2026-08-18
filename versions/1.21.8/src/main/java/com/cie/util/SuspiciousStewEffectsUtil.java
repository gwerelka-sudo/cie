package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.SuspiciousStewEffectsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Утилита для компонента `minecraft:suspicious_stew_effects` (подозрительный
 * стью). В отличие от DEATH_PROTECTION/зелий, тут каждая запись — это
 * просто пара (эффект, длительность в тиках), без amplifier/probability.
 */
public final class SuspiciousStewEffectsUtil {

    private SuspiciousStewEffectsUtil() {
    }

    public static List<SuspiciousStewEffectsComponent.StewEffect> getEffects(ItemStack stack) {
        SuspiciousStewEffectsComponent comp = stack.get(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS);
        return comp != null ? new ArrayList<>(comp.effects()) : new ArrayList<>();
    }

    private static void setEffects(ItemStack stack, List<SuspiciousStewEffectsComponent.StewEffect> effects) {
        stack.set(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffectsComponent(effects));
    }

    public static void clear(ItemStack stack) {
        stack.remove(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS);
    }

    public static void add(ItemStack stack, RegistryEntry<StatusEffect> effect, int durationTicks) {
        List<SuspiciousStewEffectsComponent.StewEffect> effects = getEffects(stack);
        effects.add(new SuspiciousStewEffectsComponent.StewEffect(effect, durationTicks));
        setEffects(stack, effects);
    }

    /** Удаляет ВСЕ записи с данным эффектом (может быть добавлен несколько раз). Возвращает, сколько записей удалено. */
    public static int remove(ItemStack stack, RegistryEntry<StatusEffect> effect) {
        List<SuspiciousStewEffectsComponent.StewEffect> effects = getEffects(stack);
        int before = effects.size();
        effects.removeIf(e -> e.effect().equals(effect));
        int removed = before - effects.size();
        if (removed > 0) {
            setEffects(stack, effects);
        }
        return removed;
    }
}