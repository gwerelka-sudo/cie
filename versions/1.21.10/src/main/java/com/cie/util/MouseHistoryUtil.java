package com.cie.util;

import net.minecraft.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Хранит копии предметов, по которым игрок кликнул ЛКМ/ПКМ в любом
 * инвентарь-экране (сундук, крафт, собственный инвентарь и т.д.) —
 * заполняется миксином HandledScreenMixin. Хранилище чисто клиентское,
 * живёт в памяти на протяжении сессии (не переживает перезапуск игры).
 *
 * Порядок — от последнего клика к первому (getAll()[0] — самый свежий).
 * Максимум CAPACITY записей: старые вытесняются новыми.
 */
public final class MouseHistoryUtil {

    private MouseHistoryUtil() {
    }

    /** 54 = размер экрана истории (6x9, как двойной сундук). */
    public static final int CAPACITY = 54;

    private static final Deque<ItemStack> HISTORY = new ArrayDeque<>();

    public static synchronized void record(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        HISTORY.addFirst(stack.copy());
        while (HISTORY.size() > CAPACITY) {
            HISTORY.removeLast();
        }
    }

    public static synchronized List<ItemStack> getAll() {
        return List.copyOf(HISTORY);
    }

    /** Самый последний кликнутый предмет, или ItemStack.EMPTY, если истории ещё нет. */
    public static synchronized ItemStack getLast() {
        return HISTORY.isEmpty() ? ItemStack.EMPTY : HISTORY.peekFirst().copy();
    }

    public static synchronized void clear() {
        HISTORY.clear();
    }

    public static synchronized boolean isEmpty() {
        return HISTORY.isEmpty();
    }
}