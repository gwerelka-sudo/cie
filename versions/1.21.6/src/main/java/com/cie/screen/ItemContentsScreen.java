package com.cie.screen;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

/**
 * Общий экран для /cie edit container open и /cie edit bundle open — тот
 * же паттерн, что и StorageScreen: настоящий {@link GenericContainerScreen}
 * поверх {@link ItemContentsScreenHandler} с полным ванильным
 * drag-n-drop "бесплатно", сохранение содержимого назад в компонент
 * предмета в руке происходит в {@link #removed()} (закрытие экрана),
 * а не автоматически на каждый клик — поэтому колбэк принимает
 * итоговый список только один раз, при закрытии.
 */
public class ItemContentsScreen extends GenericContainerScreen {

    private final Consumer<List<ItemStack>> onClose;

    public ItemContentsScreen(ItemContentsScreenHandler handler, PlayerInventory inventory, Text title, Consumer<List<ItemStack>> onClose) {
        super(handler, inventory, title);
        this.onClose = onClose;
    }

    private ItemContentsScreenHandler handler() {
        return (ItemContentsScreenHandler) this.handler;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        onClose.accept(handler().snapshotTopSlots());
        super.removed();
    }

    @Override
    protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
        this.handler.onSlotClick(slot == null ? slotId : slot.id, button, actionType, this.client.player);
    }
}
