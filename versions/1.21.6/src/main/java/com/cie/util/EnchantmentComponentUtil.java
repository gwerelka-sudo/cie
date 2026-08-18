package com.cie.util;

import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Общая утилита для DataComponentTypes.ENCHANTMENTS (обычные чары предмета)
 * и DataComponentTypes.STORED_ENCHANTMENTS (чары "внутри" зачарованной книги,
 * которые применятся на наковальне) — оба используют один и тот же тип
 * компонента ItemEnchantmentsComponent.
 *
 * РИСК ДЛЯ КОМПИЛЯЦИИ: с версии 1.21.5 Mojang убрал поле show_in_tooltip у
 * этого компонента (видимость теперь целиком через tooltip_display), так что
 * билдер здесь должен принимать только RegistryEntry+уровень. Если у тебя
 * другая ревизия маппингов и ItemEnchantmentsComponent.Builder требует
 * дополнительный boolean-параметр в конструкторе — открой genSources и
 * поправь только этот файл.
 */
public final class EnchantmentComponentUtil {

    private EnchantmentComponentUtil() {
    }

    public static Map<RegistryEntry<Enchantment>, Integer> toMap(ItemEnchantmentsComponent component) {
        Map<RegistryEntry<Enchantment>, Integer> map = new LinkedHashMap<>();
        if (component == null) {
            return map;
        }
        for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
            map.put(entry, component.getLevel(entry));
        }
        return map;
    }

    public static ItemEnchantmentsComponent fromMap(Map<RegistryEntry<Enchantment>, Integer> map) {
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        for (Map.Entry<RegistryEntry<Enchantment>, Integer> e : map.entrySet()) {
            builder.add(e.getKey(), e.getValue());
        }
        return builder.build();
    }
}
