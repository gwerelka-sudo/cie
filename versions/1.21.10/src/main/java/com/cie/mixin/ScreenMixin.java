package com.cie.mixin;

import com.cie.util.ColorPickerHolder;
import com.cie.util.UiColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * render() и keyPressed() всё ещё объявлены непосредственно в Screen (не
 * унаследованы как дефолтные методы интерфейса), поэтому остаются здесь.
 * mouseClicked/mouseDragged/mouseReleased/charTyped переехали в
 * ParentElementMixin — см. комментарий там.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void cie$renderColorPicker(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!((Object) this instanceof ChatScreen) || ColorPickerHolder.widget == null) return;

        int bx = ColorPickerHolder.buttonX;
        int by = ColorPickerHolder.buttonY;
        int bw = ColorPickerHolder.buttonW;
        int bh = ColorPickerHolder.buttonH;

        boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        int bg = hovered
                ? UiColorUtil.get("colorPicker.toggleButtonBackgroundHover")
                : UiColorUtil.get("colorPicker.toggleButtonBackground");
        int textColor = UiColorUtil.get("colorPicker.toggleButtonText");

        context.fill(bx, by, bx + bw, by + bh, bg);

        // Символ рисуем крупнее через масштаб матрицы, центрируя в кнопке.
        float scale = 1.0f;
        var tr = MinecraftClient.getInstance().textRenderer;
        String symbol = "C";
        int textW = tr.getWidth(symbol);
        int textH = tr.fontHeight;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(
                bx + bw / 2f - (textW * scale) / 2f,
                by + bh / 2f - (textH * scale) / 2f);
        context.getMatrices().scale(scale, scale);
        context.drawText(tr, symbol, 0, 0, textColor, false);
        context.getMatrices().popMatrix();

        ColorPickerHolder.widget.render(context, MinecraftClient.getInstance().textRenderer, mouseX, mouseY);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cie$keyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ChatScreen) || ColorPickerHolder.widget == null) return;
        if (ColorPickerHolder.widget.keyPressed(input)) {
            cir.setReturnValue(true);
        }
    }
}