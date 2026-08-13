package com.cie.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * /cie pickColor — "замораживает" текущий кадр (скриншот фреймбуфера в
 * NativeImage) и рисует его на весь экран вместо обычного рендера мира/GUI.
 * Наведение мышью показывает лупу с цветом пикселя под курсором, ЛКМ —
 * копирует HEX этого пикселя в системный клипборд и закрывает экран.
 * ПКМ / Escape — отмена без копирования (обрабатывается через Screen#close,
 * который вызывается автоматически при Escape).
 *
 * ВАЖНО (мультиверсия / Stonecutter): этот файл написан под текущую
 * "новую" input-модель (Click/CharInput/KeyInput — та же, что уже
 * используется в ParentElementMixin/ScreenMixin). Для более старых нод
 * (например 1.21.1, где mouseClicked ещё принимает double mouseX, double
 * mouseY, int button вместо Click) метод mouseClicked() и связанные с ним
 * сигнатуры нужно будет обернуть в //? if / //? else Stonecutter-блоки —
 * я не проверял это на всех нодах, только на текущей активной версии.
 *
 * Также ScreenshotRecorder.takeScreenshot(Framebuffer) и
 * TextureManager.registerDynamicTexture(...) — точки, которые чаще всего
 * двигаются между снапшотами; если на какой-то ноде сборка не найдёт эти
 * методы — сначала проверяйте маппинги именно тут.
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
        this.frozenImage = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
        this.frozenTexture = new NativeImageBackedTexture(() -> "cie_colorpick_frozen", frozenImage);

        client.getTextureManager().registerTexture(FROZEN_TEXTURE_ID, frozenTexture);
        this.textureRegistered = true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Замороженный кадр на весь экран вместо обычного рендера мира.
        if (frozenImage != null) {
            context.drawTexture(RenderLayer::getGuiTextured, FROZEN_TEXTURE_ID,
                    0, 0, 0.0f, 0.0f,
                    this.width, this.height,
                    this.width, this.height);
        }

        this.lastArgb = sampleColorAt(mouseX, mouseY);
        renderMagnifier(context, mouseX, mouseY, this.lastArgb);

        // Подсказка внизу экрана.
        String hint = "ЛКМ — скопировать цвет, Esc — отмена";
        context.drawCenteredTextWithShadow(this.textRenderer, hint,
                this.width / 2, this.height - 16, 0xFFFFFFFF);
    }

    /** Всегда полностью непрозрачный фон — не хотим, чтобы Minecraft рисовал что-то за кадром. */
    @Override
    protected void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // no-op: фон — это уже наш замороженный кадр, дефолтный фон Screen не нужен
    }

    private int sampleColorAt(int mouseX, int mouseY) {
        if (frozenImage == null) return 0xFFFFFFFF;

        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        int imgX = (int) Math.floor(mouseX * scale);
        int imgY = (int) Math.floor(mouseY * scale);
        imgX = Math.max(0, Math.min(frozenImage.getWidth() - 1, imgX));
        imgY = Math.max(0, Math.min(frozenImage.getHeight() - 1, imgY));

        // NativeImage#getColor отдаёт ABGR (little-endian) — переупаковываем в обычный ARGB,
        // с которым и так работает весь остальной код мода (UiColorUtil и т.д.).
        int abgr = frozenImage.getColor(imgX, imgY);
        int a = (abgr >> 24) & 0xFF;
        int b = (abgr >> 16) & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int r = abgr & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
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

        context.fill(sx, sy, sx + boxW, sy + boxH, 0xD0000000);
        context.drawBorder(sx, sy, boxW, boxH, 0xFFFFFFFF);

        int swatchX = sx + pad;
        int swatchY = sy + pad;
        context.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, 0xFF000000 | (argb & 0xFFFFFF));
        context.drawBorder(swatchX, swatchY, swatchSize, swatchSize, 0xFF000000);

        context.drawText(this.textRenderer, hex,
                sx + pad, swatchY + swatchSize + pad, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) { // ЛКМ
            int argb = sampleColorAt((int) click.x(), (int) click.y());
            String hex = String.format("#%06X", argb & 0xFFFFFF);
            MinecraftClient.getInstance().keyboard.setClipboard(hex);
            close();
            return true;
        }
        if (click.button() == 1) { // ПКМ — отмена без копирования
            close();
            return true;
        }
        return super.mouseClicked(click, doubled);
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
