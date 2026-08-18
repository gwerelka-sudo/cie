package com.cie.screen;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

import java.util.ArrayList;
import java.util.List;

/**
 * Общий (несинхронизируемый с сервером) ScreenHandler для /cie edit
 * container open и /cie edit bundle open — та же схема, что и у
 * StorageScreenHandler/MouseHistoryScreenHandler: реальный
 * GenericContainerScreenHandler поверх независимого SimpleInventory,
 * заполненного копиями переданного списка при открытии. Размер (27 или
 * 54 слота) определяется параметром rows — 3 для container, 6 для
 * bundle (см. ContainerUtil.SLOT_COUNT / BundleUtil.SLOT_COUNT).
 */
public class ItemContentsScreenHandler extends GenericContainerScreenHandler {

    public static final int SYNC_ID = -2720;

    private final int slotCount;

    public ItemContentsScreenHandler(PlayerInventory playerInventory, List<ItemStack> initial, int rows) {
        super(rows >= 6 ? ScreenHandlerType.GENERIC_9X6 : ScreenHandlerType.GENERIC_9X3,
                SYNC_ID, playerInventory, buildInventory(initial, rows * 9), rows);
        this.slotCount = rows * 9;
    }

    private static SimpleInventory buildInventory(List<ItemStack> initial, int size) {
        SimpleInventory inventory = new SimpleInventory(size);
        for (int i = 0; i < initial.size() && i < size; i++) {
            ItemStack s = initial.get(i);
            inventory.setStack(i, s.isEmpty() ? ItemStack.EMPTY : s.copy());
        }
        return inventory;
    }

    /**
     * Текущее содержимое верхних слотов, ПОЗИЦИОННО — список ровно длины
     * slotCount, пустой слот = ItemStack.EMPTY на своём индексе (а не
     * пропущен). Раньше пустые слоты выкидывались из списка, из-за чего
     * при сохранении в ContainerComponent/BundleContentsComponent
     * терялись позиции предметов — см. ContainerUtil/BundleUtil javadoc.
     */
    public List<ItemStack> snapshotTopSlots() {
        List<ItemStack> result = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            ItemStack s = this.slots.get(i).getStack();
            result.add(s.isEmpty() ? ItemStack.EMPTY : s.copy());
        }
        return result;
    }
}