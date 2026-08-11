package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Обёртка над DataComponentTypes.POTION_CONTENTS (зелья, стрелы с эффектом,
 * колдунские бутылки и т.д.) — базовый тип зелья + кастомный цвет +
 * список кастомных эффектов поверх тех, что даёт базовое зелье.
 *
 * РИСК ДЛЯ КОМПИЛЯЦИИ: это record с несколькими Optional-полями, и в разных
 * ревизиях 1.21.2+ Mojang туда добавлял/убирал поля (например customName
 * для кастомного отображаемого имени зелья). Если конструктор ругается на
 * количество аргументов — открой genSources → PotionContentsComponent,
 * посмотри реальный список полей и поправь только этот файл.
 */
public final class PotionContentsUtil {

    private PotionContentsUtil() {
    }

    public static PotionContentsComponent getOrCreate(ItemStack stack) {
        PotionContentsComponent existing = stack.get(DataComponentTypes.POTION_CONTENTS);
        return existing != null ? existing : PotionContentsComponent.DEFAULT;
    }

    public static void save(ItemStack stack, PotionContentsComponent content) {
        stack.set(DataComponentTypes.POTION_CONTENTS, content);
    }

    public static PotionContentsComponent withPotion(PotionContentsComponent content, RegistryEntry<Potion> potion) {
        return new PotionContentsComponent(
                Optional.of(potion),
                content.customColor(),
                content.customEffects(),
                content.customName()
        );
    }

    public static PotionContentsComponent withCustomColor(PotionContentsComponent content, Integer colorOrNull) {
        return new PotionContentsComponent(
                content.potion(),
                Optional.ofNullable(colorOrNull),
                content.customEffects(),
                content.customName()
        );
    }

    public static PotionContentsComponent withExtraEffect(PotionContentsComponent content, StatusEffectInstance effect) {
        List<StatusEffectInstance> effects = new ArrayList<>(content.customEffects());
        effects.add(effect);
        return new PotionContentsComponent(
                content.potion(),
                content.customColor(),
                effects,
                content.customName()
        );
    }

    public static PotionContentsComponent withClearedEffects(PotionContentsComponent content) {
        return new PotionContentsComponent(
                content.potion(),
                content.customColor(),
                List.of(),
                content.customName()
        );
    }
}
