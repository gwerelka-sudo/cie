package com.cie.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * На 1.21.4 ещё нет компонентов {@code minecraft:blocks_attacks},
 * {@code minecraft:weapon}, {@code minecraft:break_sound} и
 * {@code minecraft:tooltip_display} — они появились только в 1.21.5
 * ("Spring to Life"). Реальный игровой эффект этих компонентов
 * (снятие прочности при блокировании щитом, отключение блокировки на
 * N секунд, кастомный тултип-дисплей и т.п.) на 1.21.4 воспроизвести
 * нечем — сам движок про них не знает.
 *
 * Чтобы не резать команды в CIECommand по живому, эти 4 Util-класса
 * держат значения в {@code minecraft:custom_data} (NBT), под своим
 * ключом-неймспейсом (см. {@link #NAMESPACE}) — так команды остаются
 * рабочими "на бумаге" (значение проставляется/читается/сбрасывается),
 * просто ванильный клиент это никак не рендерит и не применяет в бою,
 * т.к. самого компонента-обработчика в этой версии игры не существует.
 *
 * ВАЖНО: если после апдейта Yarn-маппингов {@code NbtComponent} не
 * резолвится/переименован — открой genSources и поправь только этот
 * файл, остальные 4 Util-класса от него не зависят напрямую по API.
 */
final class LegacyComponentStorage {

    private static final String NAMESPACE = "cie_legacy";

    private LegacyComponentStorage() {
    }

    // NB: 1.21.4 Yarn — NbtCompound.getCompound(String) возвращает пустой
    // NbtCompound, если ключа нет (не Optional, не null). Если после
    // апдейта маппингов сигнатура другая — правь только тут.

    static NbtCompound section(ItemStack stack, String key) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = component != null ? component.copyNbt() : new NbtCompound();
        NbtCompound self = root.getCompound(NAMESPACE);
        return self.getCompound(key);
    }

    static void saveSection(ItemStack stack, String key, NbtCompound section) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = component != null ? component.copyNbt() : new NbtCompound();
        NbtCompound self = root.getCompound(NAMESPACE);
        self.put(key, section);
        root.put(NAMESPACE, self);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }

    static void removeSection(ItemStack stack, String key) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return;
        NbtCompound root = component.copyNbt();
        NbtCompound self = root.getCompound(NAMESPACE);
        self.remove(key);
        root.put(NAMESPACE, self);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }
}
