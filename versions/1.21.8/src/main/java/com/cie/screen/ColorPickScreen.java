package com.cie.screen;

import com.cie.util.UiColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/**
 * /cie pickColor — "замораживает" текущий кадр (скриншот фреймбуфера в
 * NativeImage) и рисует его на весь экран вместо обычного рендера мира/GUI.
 * Наведение мышью показывает лупу с цветом пикселя под курсором, ЛКМ —
 * копирует HEX этого пикселя в системный клипборд и закрывает экран.
 * ПКМ / Escape — отмена без копирования (обрабатывается через Screen#close,
 * который вызывается автоматически при Escape).
 *
 * ВЕРСИЯ 1.21.8 (до рефакторинга ввода 1.21.9): mouseClicked использует
 * старую сигнатуру (double mouseX, double mouseY, int button) вместо
 * Click/doubled — рефакторинг ввода (Click/CharInput/KeyInput) появился
 * только в 1.21.9.
 *
 * Также ScreenshotRecorder.takeScreenshot(...) и
 * TextureManager.registerTexture(...) — точки, которые чаще всего
 * двигаются между снапшотами (в частности, на этой версии takeScreenshot
 * отдаёт NativeImage через Consumer-колбэк, а не через return); если на
 * какой-то ноде сборка не найдёт эти методы — сначала проверяйте
 * маппинги именно тут. То же касается DrawContext.drawTexture (принимает
 * RenderPipeline, не RenderLayer-функцию, с 1.21.6+) и NativeImage.getColor
 * (стал private — используем публичный getColorArgb). drawBorder на этой
 * версии (1.21.8) ещё СУЩЕСТВУЕТ (drawStrokedRectangle — более поздняя
 * замена, появившаяся в какой-то из версий 1.21.9+, и на 1.21.8 её нет).
 *
 * Все статичные цвета этого экрана (фон/рамка лупы, рамка свотча, цвет
 * HEX-текста, цвет подсказки) взяты не хардкодом, а через UiColorUtil —
 * ключи colorPick.* зарегистрированы в UiColorDefaults и красятся командой
 * "/cie paint colorPick.<key> set <argb>". Сам квадратик-свотч с
 * подобранным цветом красить не нужно — это и есть результат работы
 * инструмента, а не элемент оформления.
 */
public class ColorPickScreen extends Screen {

    private static final Identifier FROZEN_TEXTURE_ID =
            Identifier.of("cie", "colorpick_frozen_frame");

    private NativeImage frozenImage;
    private NativeImageBackedTexture frozenTexture;
    private boolean textureRegistered = false;

    private int lastArgb = 0xFFFFFFFF;

    public ColorPickScreen() {
        super(Text.literal("CIE Color Pick"));
    }

    @Override
    protected void init() {
        MinecraftClient client = MinecraftClient.getInstance();

        // Захватываем текущий кадр ДО того как этот экран начнёт рисовать
        // поверх — иначе заморозим уже пустой/чёрный фреймбуфер.
        // takeScreenshot в этой версии не возвращает NativeImage напрямую,
        // а отдаёт его через колбэк — регистрацию текстуры делаем внутри.
        // Колбэк дёргается синхронно (захват пикселей идёт прямо тут же,
        // на рендер-треде), так что к концу init() всё уже готово.
        ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), (Consumer<NativeImage>) image -> {
            this.frozenImage = image;
            this.frozenTexture = new NativeImageBackedTexture(() -> "cie_colorpick_frozen", frozenImage);
            client.getTextureManager().registerTexture(FROZEN_TEXTURE_ID, frozenTexture);
            this.textureRegistered = true;
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Замороженный кадр на весь экран вместо обычного рендера мира.
        // GUI_TEXTURED — стандартный render pipeline для текстурного GUI-квада
        // начиная с рендер-рефакторинга 1.21.6+ (раньше сюда передавалась
        // ссылка RenderLayer::getGuiTextured — на 1.21.11 такого метода
        // в RenderLayer уже нет, вместо этого пайплайн передаётся напрямую).
        if (frozenImage != null) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, FROZEN_TEXTURE_ID,
                    0, 0, 0.0f, 0.0f,
                    this.width, this.height,
                    this.width, this.height);
        }

        this.lastArgb = sampleColorAt(mouseX, mouseY);
        renderMagnifier(context, mouseX, mouseY, this.lastArgb);

        // Подсказка внизу экрана.
        String hint = "ЛКМ — скопировать цвет, Esc — отмена";
        context.drawCenteredTextWithShadow(this.textRenderer, hint,
                this.width / 2, this.height - 16, UiColorUtil.get("colorPick.hintText"));
    }

    private int sampleColorAt(int mouseX, int mouseY) {
        if (frozenImage == null) return 0xFFFFFFFF;

        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        int imgX = (int) Math.floor(mouseX * scale);
        int imgY = (int) Math.floor(mouseY * scale);
        imgX = Math.max(0, Math.min(frozenImage.getWidth() - 1, imgX));
        imgY = Math.max(0, Math.min(frozenImage.getHeight() - 1, imgY));

        // getColorArgb — публичный метод, отдаёт цвет уже в обычном ARGB
        // (getColor() тоже существует, но отдаёт ABGR и на 1.21.11 стал
        // private — поэтому используем именно getColorArgb).
        return frozenImage.getColorArgb(imgX, imgY);
    }

    private void renderMagnifier(DrawContext context, int mouseX, int mouseY, int argb) {
        String hex = String.format("#%06X", argb & 0xFFFFFF);

        int swatchSize = 16;
        int pad = 4;
        int sx = mouseX + 14;
        int sy = mouseY + 14;

        int textWidth = this.textRenderer.getWidth(hex);
        int boxW = Math.max(swatchSize, textWidth) + pad * 2;
        int boxH = swatchSize + this.textRenderer.fontHeight + pad * 3;

        // не даём подсказке уехать за правый/нижний край экрана
        if (sx + boxW > this.width) sx = mouseX - boxW - 6;
        if (sy + boxH > this.height) sy = mouseY - boxH - 6;

        context.fill(sx, sy, sx + boxW, sy + boxH, UiColorUtil.get("colorPick.magnifierBackground"));
        // ВЕРСИЯ 1.21.8: drawStrokedRectangle в этой версии ещё не
        // существует (появился позже) — используем drawBorder, который
        // стабилен с давних версий и имеет ту же сигнатуру (x, y, width,
        // height, color).
        context.drawBorder(sx, sy, boxW, boxH, UiColorUtil.get("colorPick.magnifierBorder"));

        int swatchX = sx + pad;
        int swatchY = sy + pad;
        context.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, 0xFF000000 | (argb & 0xFFFFFF));
        context.drawBorder(swatchX, swatchY, swatchSize, swatchSize, UiColorUtil.get("colorPick.swatchBorder"));

        context.drawText(this.textRenderer, hex,
                sx + pad, swatchY + swatchSize + pad, UiColorUtil.get("colorPick.hexText"), true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // ЛКМ
            int argb = sampleColorAt((int) mouseX, (int) mouseY);
            String hex = String.format("#%06X", argb & 0xFFFFFF);
            MinecraftClient.getInstance().keyboard.setClipboard(hex);
            close();
            return true;
        }
        if (button == 1) { // ПКМ — отмена без копирования
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (textureRegistered) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(FROZEN_TEXTURE_ID);
            textureRegistered = false;
        }
        if (frozenImage != null) {
            frozenImage.close();
            frozenImage = null;
        }
        super.close();
    }
}