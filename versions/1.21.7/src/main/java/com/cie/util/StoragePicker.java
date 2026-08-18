package com.cie.util;

import net.minecraft.item.ItemStack;

import java.util.function.Consumer;

/**
 * Одноразовый callback для режима "клик-пикер" storage: команда вроде
 * EntitySettings equipment fromStorage вызывает requestPick(callback),
 * затем открывает StorageScreen — при клике по предмету в верхних 54
 * слотах StorageScreen вызывает completePick(item), которая передаёт
 * копию предмета в callback и закрывает экран. Не деструктивно —
 * предмет остаётся в storage.
 */
public final class StoragePicker {

    private StoragePicker() {
    }

    private static Consumer<ItemStack> pendingCallback;

    public static void requestPick(Consumer<ItemStack> callback) {
        pendingCallback = callback;
    }

    public static boolean isPicking() {
        return pendingCallback != null;
    }

    /** Вызывает и СБРАСЫВАЕТ callback (одноразовый — второй клик уже ничего не сделает, пока не запросят заново). */
    public static void completePick(ItemStack picked) {
        Consumer<ItemStack> callback = pendingCallback;
        pendingCallback = null;
        if (callback != null) {
            callback.accept(picked);
        }
    }

    public static void cancelPick() {
        pendingCallback = null;
    }
}
