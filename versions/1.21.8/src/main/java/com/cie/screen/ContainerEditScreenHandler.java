package com.cie.screen;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

import java.util.ArrayList;
import java.util.List;

/**
 * Локальный (несинхронизируемый с сервером) ScreenHandler для
 * /cie edit container и /cie edit bundle — тот же проверенный подход,
 * что и у Storage/MouseHistory (реальный GenericContainerScreenHandler
 * поверх независимого SimpleInventory).
 *
 * capacity может быть МЕНЬШЕ, чем rows*9 (например, воронка: capacity=5,
 * но сетка всё равно минимум 1 ряд = 9 слотов) — "лишние" слоты
 * (index >= capacity) считаются недоступными, см. isDisabledSlot()
 * и их обработку в ContainerEditScreen (визуально затемняются, клики
 * по ним блокируются).
 */
public class ContainerEditScreenHandler extends GenericContainerScreenHandler {

    public static final int SYNC_ID = -2720;

    private final int capacity;
    private final int rows;

    public ContainerEditScreenHandler(PlayerInventory playerInventory, int capacity, List<ItemStack> contents) {
        super(typeFor(rowsFor(capacity)), SYNC_ID, playerInventory, buildInventory(rowsFor(capacity), contents), rowsFor(capacity));
        this.capacity = capacity;
        this.rows = rowsFor(capacity);
    }

    private static int rowsFor(int capacity) {
        return Math.min(6, Math.max(1, (int) Math.ceil(capacity / 9.0)));
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> typeFor(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    private static SimpleInventory buildInventory(int rows, List<ItemStack> contents) {
        int size = rows * 9;
        SimpleInventory inventory = new SimpleInventory(size);
        for (int i = 0; i < size && i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            inventory.setStack(i, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return inventory;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getRows() {
        return rows;
    }

    public boolean isDisabledSlot(int index) {
        return index >= capacity;
    }

    /** Содержимое РЕАЛЬНЫХ (в пределах capacity) слотов, для сохранения обратно в компонент. */
    public List<ItemStack> snapshotContents() {
        List<ItemStack> result = new ArrayList<>(capacity);
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = this.slots.get(i).getStack();
            result.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return result;
    }
}
