package com.cie.screen;

import com.cie.util.StoragePageUtil;
import com.cie.util.StoragePicker;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class StorageScreen extends GenericContainerScreen {

    private final RegistryWrapper.WrapperLookup registries;
    private boolean locked;

    private TextFieldWidget nameField;
    private TextFieldWidget pageField;
    private ButtonWidget lockButton;

    private boolean suppressNameListener;
    private boolean suppressPageListener;

    public StorageScreen(StorageScreenHandler handler, PlayerInventory inventory, RegistryWrapper.WrapperLookup registries) {
        super(handler, inventory, Text.literal("Storage"));
        this.registries = registries;
        this.locked = handler.isLocked();
    }

    private StorageScreenHandler handler() {
        return (StorageScreenHandler) this.handler;
    }

    @Override
    protected void init() {
        super.init();
        int bx = this.x + this.backgroundWidth + 8;
        int by = this.y;
        int totalW = 110;
        int h = 18;
        int gap = 2;

        nameField = new TextFieldWidget(this.textRenderer, bx, by, totalW, h, Text.literal("page name"));
        nameField.setMaxLength(64);
        nameField.setText(StoragePageUtil.loadPage(handler().getPageIndex(), registries).name);
        nameField.setChangedListener(newName -> {
            if (suppressNameListener || newName == null || newName.isBlank()) {
                return;
            }
            StoragePageUtil.renamePage(handler().getPageIndex(), newName, registries);
        });
        this.addDrawableChild(nameField);

        int rowY = by + h + gap;
        int firstW = 18, prevW = 18, pageW = 30, nextW = 18, lastW = 18;
        int cx = bx;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("<<"), b -> switchPage(1))
                .dimensions(cx, rowY, firstW, h).build());
        cx += firstW + gap;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> switchPage(prevIndex()))
                .dimensions(cx, rowY, prevW, h).build());
        cx += prevW + gap;

        pageField = new TextFieldWidget(this.textRenderer, cx, rowY, pageW, h, Text.literal("page"));
        pageField.setMaxLength(3);
        pageField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d*"));
        pageField.setText(String.valueOf(handler().getPageIndex()));
        pageField.setChangedListener(text -> {
            if (suppressPageListener) {
                return;
            }
            try {
                int idx = Integer.parseInt(text.trim());
                if (idx >= 1 && idx <= StoragePageUtil.PAGE_COUNT && idx != handler().getPageIndex()) {
                    switchPage(idx);
                }
            } catch (NumberFormatException ignored) {
            }
        });
        this.addDrawableChild(pageField);
        cx += pageW + gap;

        this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> switchPage(nextIndex()))
                .dimensions(cx, rowY, nextW, h).build());
        cx += nextW + gap;

        this.addDrawableChild(ButtonWidget.builder(Text.literal(">>"), b -> switchPage(StoragePageUtil.PAGE_COUNT))
                .dimensions(cx, rowY, lastW, h).build());

        int row3Y = rowY + h + gap;
        lockButton = ButtonWidget.builder(lockLabel(), b -> onToggleLock())
                .dimensions(bx, row3Y, totalW, h).build();
        this.addDrawableChild(lockButton);

        int row4Y = row3Y + h + gap;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear page"), b -> onClearPage())
                .dimensions(bx, row4Y, totalW, h).build());
    }

    private Text lockLabel() {
        return Text.literal(locked ? "Locked: ON" : "Locked: OFF");
    }

    private int prevIndex() {
        int idx = handler().getPageIndex() - 1;
        return idx < 1 ? StoragePageUtil.PAGE_COUNT : idx;
    }

    private int nextIndex() {
        int idx = handler().getPageIndex() + 1;
        return idx > StoragePageUtil.PAGE_COUNT ? 1 : idx;
    }

    private void persistCurrentPage() {
        StoragePageUtil.Page page = StoragePageUtil.loadPage(handler().getPageIndex(), registries);
        ItemStack[] snapshot = handler().snapshotTopSlots();
        System.arraycopy(snapshot, 0, page.items, 0, StoragePageUtil.SLOTS_PER_PAGE);
        page.locked = this.locked;
        StoragePageUtil.savePage(handler().getPageIndex(), page, registries);
    }

    private void onClearPage() {
        for (int i = 0; i < StoragePageUtil.SLOTS_PER_PAGE; i++) {
            this.handler.slots.get(i).setStack(ItemStack.EMPTY);
        }
        StoragePageUtil.clearPageItems(handler().getPageIndex(), registries);
    }

    private void onToggleLock() {
        locked = !locked;
        StoragePageUtil.setLocked(handler().getPageIndex(), locked, registries);
        lockButton.setMessage(lockLabel());
    }

    private void switchPage(int newIndex) {
        if (newIndex == handler().getPageIndex()) {
            return;
        }
        persistCurrentPage();
        StoragePageUtil.setCurrentPageIndex(newIndex);

        StoragePageUtil.Page page = StoragePageUtil.loadPage(newIndex, registries);
        handler().reloadFromPage(newIndex, page);
        this.locked = page.locked;

        suppressNameListener = true;
        nameField.setText(page.name);
        suppressNameListener = false;

        if (!pageField.isFocused()) {
            suppressPageListener = true;
            pageField.setText(String.valueOf(newIndex));
            suppressPageListener = false;
        }

        lockButton.setMessage(lockLabel());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        persistCurrentPage();
        super.removed();
    }

    @Override
    protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
        boolean isTopSlot = slot != null && slot.id < StoragePageUtil.SLOTS_PER_PAGE;

        if (StoragePicker.isPicking()) {
            if (isTopSlot && slot.hasStack()) {
                ItemStack picked = slot.getStack().copy();
                StoragePicker.completePick(picked);
                if (this.client != null) {
                    this.client.setScreen(null);
                }
            }
            return;
        }

        if (isTopSlot && locked && slot.hasStack() && this.handler.getCursorStack().isEmpty()) {
            ItemStack copy = slot.getStack().copy();
            this.handler.setCursorStack(copy);
            return;
        }

        this.handler.onSlotClick(slot == null ? slotId : slot.id, button, actionType, this.client.player);
    }
}