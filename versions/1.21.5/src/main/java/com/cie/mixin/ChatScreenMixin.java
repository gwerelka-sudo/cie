package com.cie.mixin;

import com.cie.util.ColorPickerDataUtil;
import com.cie.util.ColorPickerHolder;
import com.cie.util.ColorPickerWidget;
import com.cie.util.UiColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [1.21.5] mouseClicked ПЕРЕОПРЕДЕЛЁН в ChatScreen (переопределение
 * компилируется и диспетчеризуется нормально), render()/keyPressed()
 * объявлены прямо в Screen (тоже диспетчеризуются нормально) — все три
 * инжекта через @Mixin(ChatScreen.class) работают. НО mouseDragged/
 * mouseReleased/charTyped — чисто default-методы интерфейса
 * ParentElement/Element, ни разу не переопределённые ни в Screen, ни в
 * ChatScreen. Отдельный @Mixin(ParentElement.class) (обычный класс ИЛИ
 * interface-миксин) в этой связке Loom+Mixin для 1.21.5 либо не
 * применяется вовсе, либо применяется, но не диспетчеризуется движком
 * тем же путём, что обычные method calls (тот же класс проблем, что уже
 * был подтверждён на 1.21.8/1.21.6 через декомпиляцию реальных .class
 * файлов — там это тоже не работало, несмотря на успешную компиляцию).
 *
 * Поэтому весь функционал (включая ранее раздельные ScreenMixin/
 * ParentElementMixin) объединён здесь и таргетит НЕПОСРЕДСТВЕННО
 * ChatScreen.class. mouseDragged/mouseReleased/charTyped реализованы
 * через опрос состояния ЛКМ каждый кадр внутри render() — тот же
 * проверенный паттерн, что уже работает на 1.21.6/1.21.7/1.21.8.
 *
 * При каждом открытии чата: создаёт (один раз) или переиспользует
 * ColorPickerWidget через ColorPickerHolder, и ставит кнопку-переключатель
 * над строкой ввода — той же высоты/стиля, что и сама строка (не
 * ButtonWidget, рисуется вручную в cie$renderColorPicker ниже). Кнопка и
 * виджет НЕ регистрируются как дети Screen, поэтому не участвуют в
 * Tab/стрелка-навигации по фокусу.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow
    protected TextFieldWidget chatField;

    // Отступ кнопки от правого края поля ввода (сдвиг влево).
    // Увеличьте, если кнопка всё ещё выходит за пределы экрана.
    private static final int cie$BUTTON_RIGHT_OFFSET = 24;

    // Минимальный отступ от правого края экрана — подстраховка на случай
    // если chatField сам подходит слишком близко к краю монитора.
    private static final int cie$SCREEN_EDGE_MARGIN = 4;

    @Inject(method = "init", at = @At("TAIL"))
    private void cie$setupColorPicker(CallbackInfo ci) {
        ChatScreen self = (ChatScreen) (Object) this;

        ColorPickerHolder.buttonW = 12;
        ColorPickerHolder.buttonH = 12;

        int screenWidth = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();

        int savedX = ColorPickerDataUtil.getButtonX();
        int savedY = ColorPickerDataUtil.getButtonY();

        if (savedX >= 0 && savedY >= 0) {
            // Пользователь уже перетаскивал кнопку раньше — используем
            // сохранённую позицию, но подстраховываемся, чтобы она не
            // оказалась за пределами текущего окна (например, после
            // изменения разрешения).
            int maxX = screenWidth - ColorPickerHolder.buttonW - cie$SCREEN_EDGE_MARGIN;
            int maxY = screenHeight - ColorPickerHolder.buttonH - cie$SCREEN_EDGE_MARGIN;
            ColorPickerHolder.buttonX = Math.max(cie$SCREEN_EDGE_MARGIN, Math.min(savedX, maxX));
            ColorPickerHolder.buttonY = Math.max(cie$SCREEN_EDGE_MARGIN, Math.min(savedY, maxY));
        } else {
            // Дефолтная позиция: у правого края поля ввода, с отступом влево.
            int desiredX = chatField.getX() + chatField.getWidth()
                    - ColorPickerHolder.buttonW - cie$BUTTON_RIGHT_OFFSET;

            // Не даём кнопке выйти за правый край экрана.
            int maxX = screenWidth - ColorPickerHolder.buttonW - cie$SCREEN_EDGE_MARGIN;
            // Не даём кнопке уйти за левый край поля ввода.
            int minX = chatField.getX();

            ColorPickerHolder.buttonX = Math.max(minX, Math.min(desiredX, maxX));
            ColorPickerHolder.buttonY = chatField.getY() - ColorPickerHolder.buttonH - 2;
        }

        if (ColorPickerHolder.widget == null) {
            ColorPickerHolder.widget = new ColorPickerWidget(
                    MinecraftClient.getInstance().textRenderer,
                    text -> chatField.write(text));
            // изначальная позиция — рядом со строкой чата, если ещё не сохранялась
            if (ColorPickerHolder.widget.x < 0 || ColorPickerHolder.widget.y < 0) {
                ColorPickerHolder.widget.x = Math.max(4, chatField.getX());
                ColorPickerHolder.widget.y = Math.max(4, chatField.getY() - 20 - ColorPickerHolder.widget.height());
                ColorPickerHolder.widget.layout();
            }
        } else {
            ColorPickerHolder.widget.setInsertCallback(text -> chatField.write(text));
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cie$renderColorPicker(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ColorPickerHolder.widget == null) return;

        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean mouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        // [1.21.5] mouseDragged/mouseReleased не диспетчеризуются движком
        // для этого класса (см. комментарий класса), поэтому драг кнопки
        // и виджета реализован через опрос состояния ЛКМ прямо здесь,
        // каждый кадр.
        if (ColorPickerHolder.draggingButton) {
            if (mouseDown) {
                int newX = mouseX - ColorPickerHolder.buttonDragOffsetX;
                int newY = mouseY - ColorPickerHolder.buttonDragOffsetY;
                if (newX != ColorPickerHolder.buttonX || newY != ColorPickerHolder.buttonY) {
                    ColorPickerHolder.buttonDragMoved = true;
                }
                ColorPickerHolder.buttonX = newX;
                ColorPickerHolder.buttonY = newY;
            } else {
                // Кнопка мыши отпущена — эквивалент старого mouseReleased.
                ColorPickerHolder.draggingButton = false;
                if (ColorPickerHolder.buttonDragMoved) {
                    ColorPickerDataUtil.setButtonPosition(ColorPickerHolder.buttonX, ColorPickerHolder.buttonY);
                } else {
                    ColorPickerHolder.widget.open = !ColorPickerHolder.widget.open;
                    ColorPickerDataUtil.setWidgetOpen(ColorPickerHolder.widget.open);
                }
            }
        } else if (mouseDown) {
            // Кнопка мыши зажата, но не над нашей toggle-кнопкой — значит
            // это может быть драг ВНУТРИ самого пикера (окно/слайдеры/
            // градиент) — эквивалент старого mouseDragged, дренится в
            // виджет, который сам решает, относится ли это к нему по
            // своим внутренним draggingWindow/draggingSV/... флагам.
            if (ColorPickerHolder.widget.mouseDragged(mouseX, mouseY)) {
                ColorPickerHolder.widget.mouseReleasedPending = true;
            }
        } else if (ColorPickerHolder.widget.mouseReleasedPending) {
            // Кнопка мыши только что отпущена после драга внутри пикера.
            ColorPickerHolder.widget.mouseReleased(mouseX, mouseY);
            ColorPickerHolder.widget.mouseReleasedPending = false;
        }

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
        // [1.21.5] старый 3D-based MatrixStack API: push()/translate(x,y,z)/
        // scale(x,y,z)/pop() — не pushMatrix()/2D-translate() из более
        // новых версий.
        float scale = 1.0f;
        var tr = MinecraftClient.getInstance().textRenderer;
        String symbol = "C";
        int textW = tr.getWidth(symbol);
        int textH = tr.fontHeight;

        context.getMatrices().push();
        context.getMatrices().translate(
                (float) (bx + bw / 2f - (textW * scale) / 2f),
                (float) (by + bh / 2f - (textH * scale) / 2f),
                0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawText(tr, symbol, 0, 0, textColor, false);
        context.getMatrices().pop();

        ColorPickerHolder.widget.render(context, MinecraftClient.getInstance().textRenderer, mouseX, mouseY);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cie$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (ColorPickerHolder.widget == null) return;
        if (ColorPickerHolder.widget.keyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
            return;
        }
        // [1.21.5] charTyped тоже недоступен для @Inject (см. комментарий
        // класса) — печать символов в hex-поле реализована здесь же,
        // вручную, через GLFW-коды клавиш для цифр 0-9 и букв A-F
        // (единственные символы, допустимые в hex-коде цвета).
        if (ColorPickerHolder.widget.isHexFieldFocused()) {
            char typed = cie$keyCodeToHexChar(keyCode);
            if (typed != 0 && ColorPickerHolder.widget.charTyped(typed, modifiers)) {
                cir.setReturnValue(true);
            }
        }
    }

    /** GLFW-коды: 48-57 = цифры 0-9 (основная раскладка), 65-70 = буквы A-F. */
    private static char cie$keyCodeToHexChar(int keyCode) {
        if (keyCode >= 48 && keyCode <= 57) {
            return (char) ('0' + (keyCode - 48));
        }
        if (keyCode >= 65 && keyCode <= 70) {
            return (char) ('a' + (keyCode - 65));
        }
        return 0;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cie$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (ColorPickerHolder.widget == null) return;

        if (mouseX >= ColorPickerHolder.buttonX && mouseX < ColorPickerHolder.buttonX + ColorPickerHolder.buttonW
                && mouseY >= ColorPickerHolder.buttonY && mouseY < ColorPickerHolder.buttonY + ColorPickerHolder.buttonH) {
            // Не переключаем пикер сразу — сначала ждём отпускания кнопки
            // мыши (см. cie$renderColorPicker): если мышь не сдвинулась,
            // это был обычный клик (тогда переключаем), если сдвинулась —
            // это было перетаскивание кнопки.
            ColorPickerHolder.draggingButton = true;
            ColorPickerHolder.buttonDragMoved = false;
            ColorPickerHolder.buttonDragOffsetX = (int) mouseX - ColorPickerHolder.buttonX;
            ColorPickerHolder.buttonDragOffsetY = (int) mouseY - ColorPickerHolder.buttonY;
            cir.setReturnValue(true);
            return;
        }

        if (ColorPickerHolder.widget.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}