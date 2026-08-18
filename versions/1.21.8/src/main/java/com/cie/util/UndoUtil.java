package com.cie.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Хранит историю состояний предмета в руке перед каждым изменяющим действием,
 * чтобы можно было откатить последнюю правку командой /cie undo, и историю
 * "отменённых" правок для /cie redo.
 *
 * Живёт только в памяти клиента (сбрасывается при перезапуске игры/выходе
 * из мира) — постоянного хранилища на диске сейчас нет.
 */
public final class UndoUtil {

    private UndoUtil() {
    }

    private static final int MAX_HISTORY = 20;
    private static final Map<UUID, Deque<ItemStack>> HISTORY = new HashMap<>();
    private static final Map<UUID, Deque<ItemStack>> REDO_HISTORY = new HashMap<>();

    /**
     * Вызывается в начале КАЖДОЙ команды, которая меняет предмет — сохраняет
     * состояние ДО правки. Также очищает redo-историю игрока: как и в любом
     * стандартном undo/redo (текстовые редакторы и т.д.), новое изменение
     * "перезаписывает будущее" — старые отменённые правки больше не redo-ятся.
     */
    public static void pushSnapshot(ClientPlayerEntity player, ItemStack stack) {
        Deque<ItemStack> stackHistory = HISTORY.computeIfAbsent(player.getUuid(), id -> new ArrayDeque<>());
        stackHistory.push(stack.copy());
        while (stackHistory.size() > MAX_HISTORY) {
            stackHistory.removeLast();
        }
        REDO_HISTORY.remove(player.getUuid());
    }

    /** Достаёт и удаляет последнее сохранённое состояние (null, если истории нет). */
    public static ItemStack pop(ClientPlayerEntity player) {
        Deque<ItemStack> stackHistory = HISTORY.get(player.getUuid());
        if (stackHistory == null || stackHistory.isEmpty()) {
            return null;
        }
        return stackHistory.pop();
    }

    /**
     * Сохраняет состояние предмета ДО отката (то, что undo сейчас заменит) в
     * redo-историю — вызывается командой /cie undo непосредственно перед
     * применением UndoUtil.pop().
     */
    public static void pushRedoSnapshot(ClientPlayerEntity player, ItemStack currentStack) {
        Deque<ItemStack> redoHistory = REDO_HISTORY.computeIfAbsent(player.getUuid(), id -> new ArrayDeque<>());
        redoHistory.push(currentStack.copy());
        while (redoHistory.size() > MAX_HISTORY) {
            redoHistory.removeLast();
        }
    }

    /** Достаёт и удаляет последнее отменённое состояние (null, если redo-истории нет). */
    public static ItemStack popRedo(ClientPlayerEntity player) {
        Deque<ItemStack> redoHistory = REDO_HISTORY.get(player.getUuid());
        if (redoHistory == null || redoHistory.isEmpty()) {
            return null;
        }
        return redoHistory.pop();
    }

    /**
     * Возвращает состояние в undo-историю (используется командой /cie redo,
     * чтобы можно было снова откатить только что повторённую правку) — не
     * трогает MAX_HISTORY-обрезку, т.к. redo не должен "терять" undo-стек.
     */
    public static void pushUndoSnapshot(ClientPlayerEntity player, ItemStack stack) {
        Deque<ItemStack> stackHistory = HISTORY.computeIfAbsent(player.getUuid(), id -> new ArrayDeque<>());
        stackHistory.push(stack.copy());
        while (stackHistory.size() > MAX_HISTORY) {
            stackHistory.removeLast();
        }
    }

    public static void clear(ClientPlayerEntity player) {
        HISTORY.remove(player.getUuid());
        REDO_HISTORY.remove(player.getUuid());
    }
}