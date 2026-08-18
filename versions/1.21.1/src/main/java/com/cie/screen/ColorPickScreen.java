package com.cie.screen;

import com.cie.util.UiColorUtil;
import net.minecraft.client.MinecraftClient;
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
 * [1.21.4] Старая система ввода: mouseClicked(double, double, int) вместо
 * Click. Текстуры рисуются через RenderLayer::getGuiTextured (функция,
 * не готовый RenderPipeline — тот появился в 1.21.6+). drawBorder(...)
 * существует в этой версии (в отличие от более новых, где он убран в
 * пользу drawStrokedRectangle) — используем его напрямую.
 *
 * ScreenshotRecorder.takeScreenshot в этой версии — синхронный, возвращает
 * NativeImage напрямую (колбэк-вариант (Framebuffer, Consumer<NativeImage>)
 * появился позже). NativeImageBackedTexture тоже без конструктора с
 * Supplier<String> — просто NativeImageBackedTexture(NativeImage).
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
        // В этой версии takeScreenshot синхронный: сразу отдаёт готовый
        // NativeImage, без колбэка.
        this.frozenImage = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
        this.frozenTexture = new NativeImageBackedTexture(frozenImage);
        client.getTextureManager().registerTexture(FROZEN_TEXTURE_ID, frozenTexture);
        this.textureRegistered = true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Замороженный кадр на весь экран вместо обычного рендера мира.
        // getGuiTextured — стандартный RenderLayer для текстурного GUI-квада
        // в этой версии (готовые RenderPipeline-константы вроде
        // RenderPipelines.GUI_TEXTURED появились позже, в 1.21.6+).
        if (frozenImage != null) {
            context.drawTexture(FROZEN_TEXTURE_ID,
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

        context.fill(sx, sy, sx + boxW, sy + boxH, UiColorUtil.get("colorPick.magnifierBackground"));
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