package com.cie.util;

import com.cie.util.LivePreviewUtil;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * /cie livePreview — рендер HUD-виджета (маленький "слот" с предметом из
 * основной руки) и перетаскивание этого виджета мышью.
 *
 * ВАЖНО про тултип: НЕ используем DrawContext.drawItemTooltip(...) — этот
 * метод только КЛАДЁТ отрисовку тултипа в отложенную очередь DrawContext'а
 * (приватное поле tooltipDrawer), а сама очередь "промывается" (реально
 * рисуется на экран) только когда следом в этом же кадре рендерится живой
 * Screen — у него в конце своего render() есть шаг, который эту очередь
 * сбрасывает. Наш виджет рисуется через HudRenderCallback, который
 * срабатывает и без открытого экрана — в этом случае очередь тултипа
 * никем не промывается и просто пропадает, поэтому тултип был виден
 * только при открытом чате. Чтобы тултип был виден ВСЕГДА, строим и
 * рисуем его сами: сами берём строки через stack.getTooltip(...) и рисуем
 * фон + рамку + текст обычными immediate-вызовами (fill + drawText), точно
 * так же, как рисуем слот и иконку предмета — они уже и так работали без
 * чата.
 *
 * ВАЖНО про advanced tooltip (F3+H): TooltipType не хардкодим — читаем
 * настройку клиента client.options.advancedItemTooltips, иначе id
 * предмета/NBT в тултипе никогда не появится, даже если у игрока включён
 * F3+H.
 *
 * ВАЖНО про мышь: перетаскивание НЕ использует ScreenMouseEvents.afterMouseClick
 * / afterMouseRelease — сигнатура этих функциональных интерфейсов плавает
 * между версиями Fabric API. Вместо этого состояние левой кнопки мыши
 * читается напрямую через GLFW каждый кадр внутри ScreenEvents.afterRender —
 * низкоуровневый LWJGL API, не меняющийся от версии к версии.
 *
 * Перетаскивание работает ТОЛЬКО пока открыт {@link ChatScreen}: это
 * единственный "лёгкий" экран, который не ставит игру на паузу и не
 * отдаёт мышь под управление камерой. Сам виджет при этом рисуется
 * всегда (через {@link HudRenderCallback}) — открытие чата лишь включает
 * возможность его подвинуть.
 */
public final class LivePreviewRenderer {

    private LivePreviewRenderer() {
    }

    private static final int TOOLTIP_BACKGROUND = 0xF0100010;
    // Цвета рамки — как в ванильном TooltipComponent: верх/низ градиент фиолетово-синего в тёмно-фиолетовый.
    private static final int TOOLTIP_BORDER_TOP = 0x505000FF;
    private static final int TOOLTIP_BORDER_BOTTOM = 0x5028007F;
    private static final int TOOLTIP_TEXT_COLOR = 0xFFFFFFFF;
    private static final int TOOLTIP_PADDING = 4;
    private static final int TOOLTIP_LINE_HEIGHT = 10;

    /** Вызывается один раз при инициализации мода (см. CIEClientMod). */
    public static void register() {
        HudRenderCallback.EVENT.register(LivePreviewRenderer::onHudRender);

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof ChatScreen)) {
                return;
            }

            ScreenEvents.afterRender(screen).register((s, drawContext, mouseX, mouseY, tickDelta) ->
                    handleDrag(client, s.width, s.height, mouseX, mouseY));

            // Чат закрыли посреди перетаскивания (например, Escape) — не оставляем "залипший" drag.
            ScreenEvents.remove(screen).register(s -> LivePreviewUtil.endDrag());
        });
    }

    private static void handleDrag(MinecraftClient client, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        long windowHandle = client.getWindow().getHandle();
        boolean leftDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (leftDown && !LivePreviewUtil.isDragging() && LivePreviewUtil.isEnabled()
                && LivePreviewUtil.isInside(mouseX, mouseY)) {
            LivePreviewUtil.beginDrag(mouseX, mouseY);
        }

        if (LivePreviewUtil.isDragging()) {
            if (leftDown) {
                LivePreviewUtil.updateDrag(mouseX, mouseY, screenWidth, screenHeight);
            } else {
                LivePreviewUtil.endDrag();
            }
        }
    }

    private static void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        if (!LivePreviewUtil.isEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            LivePreviewUtil.clearTooltipBounds();
            return;
        }
        // Полноценные "тяжёлые" экраны (инвентарь, сундуки и т.д.) и так покрывают собой HUD своим
        // фоном — виджет там просто не нужен. Чат — лёгкий полупрозрачный оверлей, сквозь него
        // виджет должен оставаться видимым и двигаемым, поэтому чат намеренно не исключаем.
        if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
            LivePreviewUtil.clearTooltipBounds();
            return;
        }

        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();
        LivePreviewUtil.clampToScreen(screenWidth, screenHeight);

        int x = LivePreviewUtil.getX();
        int y = LivePreviewUtil.getY();
        int size = LivePreviewUtil.getSlotSize();

        drawSlotBackground(drawContext, x, y, size);

        ItemStack stack = client.player.getMainHandStack();
        if (stack.isEmpty()) {
            LivePreviewUtil.clearTooltipBounds();
            return;
        }

        int itemMargin = (size - 16) / 2;
        int itemX = x + itemMargin;
        int itemY = y + itemMargin;

        TextRenderer textRenderer = client.textRenderer;
        drawContext.drawItem(stack, itemX, itemY);
        drawContext.drawStackOverlay(textRenderer, stack, itemX, itemY);

        drawTooltip(drawContext, textRenderer, stack, client, x, y, size, screenWidth);
    }

    /**
     * Строим и рисуем тултип полностью сами (см. класс-комментарий про причину) —
     * "курсор" как будто навечно завис над предметом, тултип виден постоянно.
     */
    private static void drawTooltip(DrawContext drawContext, TextRenderer textRenderer, ItemStack stack,
                                    MinecraftClient client, int x, int y, int size, int screenWidth) {
        // F3+H — advanced tooltips (id предмета, NBT и т.д.). Без этой проверки тултип
        // всегда оставался "базовым", даже при включённом F3+H у игрока.
        TooltipType tooltipType = client.options.advancedItemTooltips
                ? TooltipType.ADVANCED
                : TooltipType.BASIC;
        List<Text> lines = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, tooltipType);
        if (lines.isEmpty()) {
            LivePreviewUtil.clearTooltipBounds();
            return;
        }

        int maxWidth = 0;
        for (Text line : lines) {
            maxWidth = Math.max(maxWidth, textRenderer.getWidth(line));
        }

        int boxWidth = maxWidth + TOOLTIP_PADDING * 2;
        int boxHeight = lines.size() * TOOLTIP_LINE_HEIGHT + TOOLTIP_PADDING * 2 - (TOOLTIP_LINE_HEIGHT - 8);

        int boxX = x + size + 6;
        if (boxX + boxWidth > screenWidth) {
            boxX = Math.max(0, x - boxWidth - 6);
        }
        int boxY = y;

        drawContext.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, TOOLTIP_BACKGROUND);
        drawTooltipBorder(drawContext, boxX, boxY, boxWidth, boxHeight);

        for (int i = 0; i < lines.size(); i++) {
            int lineY = boxY + TOOLTIP_PADDING + i * TOOLTIP_LINE_HEIGHT;
            drawContext.drawText(textRenderer, lines.get(i), boxX + TOOLTIP_PADDING, lineY, TOOLTIP_TEXT_COLOR, true);
        }

        // Регистрируем границы, чтобы захват мышью для перетаскивания работал и по тултипу, не только по слоту.
        LivePreviewUtil.setTooltipBounds(boxX, boxY, boxWidth, boxHeight);
    }

    /**
     * Имитация ванильной рамки тултипа: верх/низ — плоские линии, а левая/правая грань —
     * вертикальный градиент между двумя цветами (как в настоящем TooltipComponent),
     * из-за чего края выглядят мягче, а не одним резким плоским цветом на всю высоту.
     */
    private static void drawTooltipBorder(DrawContext drawContext, int x, int y, int width, int height) {
        drawContext.fill(x, y, x + width, y + 1, TOOLTIP_BORDER_TOP);
        drawContext.fill(x, y + height - 1, x + width, y + height, TOOLTIP_BORDER_BOTTOM);
        drawContext.fillGradient(x, y + 1, x + 1, y + height - 1, TOOLTIP_BORDER_TOP, TOOLTIP_BORDER_BOTTOM);
        drawContext.fillGradient(x + width - 1, y + 1, x + width, y + height - 1, TOOLTIP_BORDER_TOP, TOOLTIP_BORDER_BOTTOM);
    }

    private static void drawSlotBackground(DrawContext drawContext, int x, int y, int size) {
        // Ручная имитация ванильного "утопленного" слота 18x18: сплошная (НЕ прозрачная) заливка +
        // тёмная тень сверху/слева + светлый блик снизу/справа. Без завязки на конкретные
        // Identifier'ы спрайтов виджетов (они не всегда стабильны между версиями).
        drawContext.fill(x, y, x + size, y + size, 0xFF8B8B8B);
        drawContext.fill(x, y, x + size, y + 1, 0xFF373737);
        drawContext.fill(x, y, x + 1, y + size, 0xFF373737);
        drawContext.fill(x + size - 1, y, x + size, y + size, 0xFFFFFFFF);
        drawContext.fill(x, y + size - 1, x + size, y + size, 0xFFFFFFFF);
    }
}