package com.cie.mixin;

import com.cie.util.ColorPickerHolder;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.input.CharInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * mouseClicked/mouseDragged/mouseReleased/charTyped больше НЕ переопределены
 * в Screen — начиная с этой версии их единственная реализация — дефолтный
 * метод интерфейса ParentElement, который Screen просто наследует. Поэтому
 * @Mixin(Screen.class) не может найти эти методы (их там физически нет в
 * байткоде класса), и инъекция цепляется прямо на интерфейс.
 *
 * render() и keyPressed() всё ещё объявлены в самом Screen — они остаются
 * в ScreenMixin как было.
 */
@Mixin(ParentElement.class)
public interface ParentElementMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    default void cie$mouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!(this instanceof ChatScreen) || ColorPickerHolder.widget == null) return;

        double mx = click.x();
        double my = click.y();

        if (mx >= ColorPickerHolder.buttonX && mx < ColorPickerHolder.buttonX + ColorPickerHolder.buttonW
                && my >= ColorPickerHolder.buttonY && my < ColorPickerHolder.buttonY + ColorPickerHolder.buttonH) {
            // Не переключаем пикер сразу — сначала ждём mouseReleased: если
            // мышь не сдвинулась, это был обычный клик (тогда переключаем),
            // если сдвинулась — это было перетаскивание кнопки.
            ColorPickerHolder.draggingButton = true;
            ColorPickerHolder.buttonDragMoved = false;
            ColorPickerHolder.buttonDragOffsetX = (int) mx - ColorPickerHolder.buttonX;
            ColorPickerHolder.buttonDragOffsetY = (int) my - ColorPickerHolder.buttonY;
            cir.setReturnValue(true);
            return;
        }

        if (ColorPickerHolder.widget.mouseClicked(mx, my, click.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    default void cie$mouseDragged(Click click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
        if (!(this instanceof ChatScreen) || ColorPickerHolder.widget == null) return;

        if (ColorPickerHolder.draggingButton) {
            int newX = (int) click.x() - ColorPickerHolder.buttonDragOffsetX;
            int newY = (int) click.y() - ColorPickerHolder.buttonDragOffsetY;
            if (newX != ColorPickerHolder.buttonX || newY != ColorPickerHolder.buttonY) {
                ColorPickerHolder.buttonDragMoved = true;
            }
            ColorPickerHolder.buttonX = newX;
            ColorPickerHolder.buttonY = newY;
            cir.setReturnValue(true);
            return;
        }

        if (ColorPickerHolder.widget.mouseDragged(click.x(), click.y())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    default void cie$mouseReleased(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (!(this instanceof ChatScreen) || ColorPickerHolder.widget == null) return;

        if (ColorPickerHolder.draggingButton) {
            ColorPickerHolder.draggingButton = false;
            if (ColorPickerHolder.buttonDragMoved) {
                // Было перетаскивание — сохраняем новую позицию кнопки.
                com.cie.util.ColorPickerDataUtil.setButtonPosition(
                        ColorPickerHolder.buttonX, ColorPickerHolder.buttonY);
            } else {
                // Мышь не сдвинулась — это был обычный клик, переключаем пикер.
                ColorPickerHolder.widget.open = !ColorPickerHolder.widget.open;
                com.cie.util.ColorPickerDataUtil.setWidgetOpen(ColorPickerHolder.widget.open);
            }
            cir.setReturnValue(true);
            return;
        }

        if (ColorPickerHolder.widget.mouseReleased(click.x(), click.y())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    default void cie$charTyped(CharInput input, CallbackInfoReturnable<Boolean> cir) {
        if (!(this instanceof ChatScreen) || ColorPickerHolder.widget == null) return;
        if (ColorPickerHolder.widget.charTyped(input)) {
            cir.setReturnValue(true);
        }
    }
}