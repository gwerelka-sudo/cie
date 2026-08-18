package com.cie.util;
import java.util.List;
import java.util.ArrayList;

import net.minecraft.item.consume.ApplyEffectsConsumeEffect;
import net.minecraft.item.consume.ConsumeEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;

public final class ConsumableComponentUtil {

    private ConsumableComponentUtil() {}

    public static ConsumableComponent getConsumable(ItemStack stack) {
        return stack.get(DataComponentTypes.CONSUMABLE);
    }

    // Комплексный метод (исправляет ошибку)
    public static void setConsumable(ItemStack stack, float seconds, UseAction animation, RegistryEntry<SoundEvent> sound, boolean hasConsumeParticles) {
        ConsumableComponent comp = ConsumableComponent.builder()
                .consumeSeconds(seconds)
                .useAction(animation)
                .sound(sound)
                .consumeParticles(hasConsumeParticles)
                .build();

        stack.set(DataComponentTypes.CONSUMABLE, comp);
    }

    // Раздельные методы
    public static void setSeconds(ItemStack stack, float seconds) {
        ConsumableComponent current = getConsumable(stack);
        ConsumableComponent.Builder builder = current != null
                ? ConsumableComponent.builder().consumeSeconds(seconds).useAction(current.useAction()).sound(current.sound()).consumeParticles(current.hasConsumeParticles())
                : ConsumableComponent.builder().consumeSeconds(seconds);

        stack.set(DataComponentTypes.CONSUMABLE, builder.build());
    }

    public static void setAnimation(ItemStack stack, UseAction action) {
        ConsumableComponent current = getConsumable(stack);
        float seconds = current != null ? current.consumeSeconds() : 1.6f;
        ConsumableComponent.Builder builder = current != null
                ? ConsumableComponent.builder().consumeSeconds(seconds).useAction(action).sound(current.sound()).consumeParticles(current.hasConsumeParticles())
                : ConsumableComponent.builder().consumeSeconds(seconds).useAction(action);

        stack.set(DataComponentTypes.CONSUMABLE, builder.build());
    }

    public static void setSound(ItemStack stack, RegistryEntry<SoundEvent> sound) {
        ConsumableComponent current = getConsumable(stack);
        float seconds = current != null ? current.consumeSeconds() : 1.6f;
        ConsumableComponent.Builder builder = current != null
                ? ConsumableComponent.builder().consumeSeconds(seconds).useAction(current.useAction()).sound(sound).consumeParticles(current.hasConsumeParticles())
                : ConsumableComponent.builder().consumeSeconds(seconds).sound(sound);

        stack.set(DataComponentTypes.CONSUMABLE, builder.build());
    }

    public static void setParticles(ItemStack stack, boolean particles) {
        ConsumableComponent current = getConsumable(stack);
        float seconds = current != null ? current.consumeSeconds() : 1.6f;
        ConsumableComponent.Builder builder = current != null
                ? ConsumableComponent.builder().consumeSeconds(seconds).useAction(current.useAction()).sound(current.sound()).consumeParticles(particles)
                : ConsumableComponent.builder().consumeSeconds(seconds).consumeParticles(particles);

        stack.set(DataComponentTypes.CONSUMABLE, builder.build());
    }

    public static void removeConsumable(ItemStack stack) {
        stack.remove(DataComponentTypes.CONSUMABLE);
    }

    public static void addEffect(ItemStack stack, StatusEffectInstance effect, float chance) {
        ConsumableComponent comp = getConsumable(stack);
        if (comp == null) return;

        List<ConsumeEffect> effects = new ArrayList<>(comp.onConsumeEffects());

        // В Yarn 1.21.2+ передаётся List.of(effect) и chance
        effects.add(new ApplyEffectsConsumeEffect(List.of(effect), chance));

        ConsumableComponent updated = new ConsumableComponent(
                comp.consumeSeconds(),
                comp.useAction(),
                comp.sound(),
                comp.hasConsumeParticles(),
                effects
        );
        stack.set(DataComponentTypes.CONSUMABLE, updated);
    }
}