package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DeathProtectionComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.ApplyEffectsConsumeEffect;
import net.minecraft.item.consume.ConsumeEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Обёртка над DataComponentTypes.DEATH_PROTECTION — компонент тотема
 * бессмертия: список ConsumeEffect (тех же типов, что и у CONSUMABLE),
 * которые применяются при спасении игрока от смерти.
 *
 * РИСК ДЛЯ КОМПИЛЯЦИИ: сигнатура ApplyEffectsConsumeEffect(List, float)
 * уже подтверждена рабочей в твоём ConsumableComponentUtil, так что тут
 * она должна собраться без правок. Единственное непроверенное место —
 * имя аксессора DeathProtectionComponent.deathEffects() — если
 * компилятор ругается, открой genSources → DeathProtectionComponent
 * и поправь только этот файл.
 */
public final class DeathProtectionUtil {

    private DeathProtectionUtil() {
    }

    public static DeathProtectionComponent getOrCreate(ItemStack stack) {
        DeathProtectionComponent existing = stack.get(DataComponentTypes.DEATH_PROTECTION);
        return existing != null ? existing : new DeathProtectionComponent(List.of());
    }

    public static boolean isPresent(ItemStack stack) {
        return stack.contains(DataComponentTypes.DEATH_PROTECTION);
    }

    public static void save(ItemStack stack, DeathProtectionComponent component) {
        stack.set(DataComponentTypes.DEATH_PROTECTION, component);
    }

    public static void remove(ItemStack stack) {
        stack.remove(DataComponentTypes.DEATH_PROTECTION);
    }

    public static List<ConsumeEffect> effects(DeathProtectionComponent component) {
        return component.deathEffects();
    }

    public static DeathProtectionComponent withExtraEffect(DeathProtectionComponent component, StatusEffectInstance effect, float probability) {
        List<ConsumeEffect> effects = new ArrayList<>(component.deathEffects());
        effects.add(new ApplyEffectsConsumeEffect(List.of(effect), probability));
        return new DeathProtectionComponent(effects);
    }

    public static DeathProtectionComponent withClearedEffects(DeathProtectionComponent component) {
        return new DeathProtectionComponent(List.of());
    }
}
