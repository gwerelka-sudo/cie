package com.cie.mixin;

import com.cie.util.ColorPickerHolder;
import net.minecraft.client.gui.Click;
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
 * Общая точка рендера/ввода для color picker'а. Висит на Screen (а не на
 * ChatScreen), потому что render/mouseClicked/mouseDragged/mouseReleased/
 * keyPressed/charTyped гарантированно объявлены именно в Screen — так
 * инъекция не зависит от того, переопределяет их ChatScreen или нет.
 * Всё активно только когда this instanceof ChatScreen.
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

        // Кнопка того же стиля, что и строка чата: тёмная заливка + серая
        // рамка, внутри — квадратик текущего выбранного цвета.
        context.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFFA0A0A0);
        context.fill(bx, by, bx + bw, by + bh, 0xFF000000);
        int rgb = ColorPickerHolder.widget.currentRgb();
        context.fill(bx + 2, by + 2, bx + bw - 2, by + bh - 2, 0xFF000000 | rgb);

        ColorPickerHolder.widget.render(context, MinecraftClient.getInstance().textRenderer, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cie$mouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ChatScreen) || ColorPickerHolder.widget == null) return;

        double mx = click.x();
        double my = click.y();

        if (mx >= ColorPickerHolder.buttonX && mx < ColorPickerHolder.buttonX + ColorPickerHolder.buttonW
                && my >= ColorPickerHolder.buttonY && my < ColorPickerHolder.buttonY + ColorPickerHolder.buttonH) {
            ColorPickerHolder.widget.open = !ColorPickerHolder.widget.open;
            com.cie.util.ColorPickerDataUtil.setWidgetOpen(ColorPickerHolder.widget.open);
            cir.setReturnValue(true);
            return;
        }

        if (ColorPickerHolder.widget.mouseClicked(mx, my, click.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void cie$mouseDragged(Click click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ChatScreen) || ColorPickerHolder.widget == null) return;
        if (ColorPickerHolder.widget.mouseDragged(click.x(), click.y())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void cie$mouseReleased(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ChatScreen) || ColorPickerHolder.widget == null) return;
        if (ColorPickerHolder.widget.mouseReleased(click.x(), click.y())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cie$keyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ChatScreen) || ColorPickerHolder.widget == null) return;
        if (ColorPickerHolder.widget.keyPressed(input)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void cie$charTyped(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ChatScreen) || ColorPickerHolder.widget == null) return;
        if (ColorPickerHolder.widget.charTyped(chr, modifiers)) {
            cir.setReturnValue(true);
        }
    }
}
