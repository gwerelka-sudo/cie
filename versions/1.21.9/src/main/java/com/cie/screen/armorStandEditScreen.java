package com.cie.screen;

import com.cie.text.CIELang;
import com.cie.util.ArmorStandDataUtil;
import com.cie.util.EntitySettingsUtil;
import com.cie.util.StoragePageUtil;
import com.cie.util.StoragePicker;
import com.cie.util.UndoUtil;
import com.cie.util.UiColorUtil;
import com.cie.util.UseRemainderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.EulerAngle;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /cie edit armorStand menu — некастомный (не Handled-, не привязанный к
 * инвентарю сервера) плавающий экран: двигается по экрану перетаскиванием
 * за шапку, живёт целиком на клиенте и пишет прямо в NBT предмета в руке
 * (тот же ENTITY_DATA-механизм, что и остальной редактор, см.
 * {@link ArmorStandDataUtil}).
 *
 * Правки применяются к {@link #workingStack} немедленно (это то, что видно
 * в превью), но на сервер уходят ТОЛЬКО по явному нажатию кнопки Apply через
 * {@link #syncToServer()} — закрытие окна, переключение чекбоксов, смена
 * экипировки, применение пресета и перетаскивание слайдеров позы больше не
 * триггерят синхронизацию сами по себе. Если закрыть окно без Apply, все
 * несохранённые правки просто отбрасываются вместе с {@link #workingStack}.
 */
public class armorStandEditScreen extends Screen {

    private static final int PANEL_W = 430;
    // Было 288 — между рядом флагов и нижними кнопками (Presets/Apply/Close)
    // оставалась большая пустая полоса (см. скриншот). Флаги заканчиваются
    // на previewY + previewSize + 14 + ~14 (высота чекбокса) от TITLE_H,
    // низ панели теперь ближе к этому и оставляет только место под statusLine.
    private static final int PANEL_H = 210;
    private static final int TITLE_H = 20;

    private static final Map<String, EquipmentSlot> NBT_TO_SLOT = new LinkedHashMap<>();
    static {
        UiColorUtil.registerDefault("armorStandMenu.panelBackground", 0xE0202020);
        UiColorUtil.registerDefault("armorStandMenu.titleBar", 0xE0303030);
        UiColorUtil.registerDefault("armorStandMenu.panelBorder", 0xFF606060);
        UiColorUtil.registerDefault("armorStandMenu.previewBackground", 0xFF101010);
        UiColorUtil.registerDefault("armorStandMenu.titleText", 0xFFFFFF);
        UiColorUtil.registerDefault("armorStandMenu.previewFallbackText", 0x808080);
        UiColorUtil.registerDefault("armorStandMenu.poseLabelText", 0xA0F5D0);
        UiColorUtil.registerDefault("armorStandMenu.statusLineText", 0xF2C866);

        NBT_TO_SLOT.put("head", EquipmentSlot.HEAD);
        NBT_TO_SLOT.put("chest", EquipmentSlot.CHEST);
        NBT_TO_SLOT.put("legs", EquipmentSlot.LEGS);
        NBT_TO_SLOT.put("feet", EquipmentSlot.FEET);
        NBT_TO_SLOT.put("mainhand", EquipmentSlot.MAINHAND);
        NBT_TO_SLOT.put("offhand", EquipmentSlot.OFFHAND);
    }

    private enum Overlay { NONE, PRESETS, EQUIP }

    private final ItemStack workingStack;
    private final int handSlot;
    private final RegistryWrapper.WrapperLookup registries;
    private final ArmorStandEntity previewEntity;

    private int panelX;
    private int panelY;
    private boolean dragging;
    private int dragOffX;
    private int dragOffY;

    // -- вращение камеры превью (не позы стойки!) перетаскиванием ЛКМ по превью --
    private static final int PREVIEW_SIZE = 90;
    private float previewYaw = 35.0f;   // стартовый угол, похожий на прежний "по умолчанию" вид
    private float previewPitch = 10.0f;
    private float previewZoom = 30.0f;  // масштаб модели в drawArmorStandPreview (было хардкодом 30), крутится колесом мыши над превью
    private static final float PREVIEW_ZOOM_MIN = 10.0f;
    private static final float PREVIEW_ZOOM_MAX = 60.0f;
    private boolean draggingPreview;
    private double lastPreviewDragX;
    private double lastPreviewDragY;

    private ArmorStandDataUtil.Part selectedPart = ArmorStandDataUtil.Part.HEAD;
    private Overlay overlay = Overlay.NONE;
    private String equipSlotKey = null;
    private TextFieldWidget newItemField;
    private Text statusLine = null;

    public armorStandEditScreen(ItemStack stack, int handSlot, RegistryWrapper.WrapperLookup registries) {
        super(Text.literal(CIELang.get("armorstand_menu_title")));
        this.workingStack = stack;
        this.handSlot = handSlot;
        this.registries = registries;

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        ArmorStandEntity entity = null;
        if (player != null && player.getEntityWorld() != null) {
            entity = new ArmorStandEntity(EntityType.ARMOR_STAND, player.getEntityWorld());
        }
        this.previewEntity = entity;
    }

    @Override
    protected void init() {
        // Открываем ближе к центру при первом появлении, дальше — там, куда
        // перетащили (panelX/panelY уже не 0 после первого init()).
        if (panelX == 0 && panelY == 0) {
            panelX = (this.width - PANEL_W) / 2;
            panelY = (this.height - PANEL_H) / 2;
        }
        refreshPreviewEntity();
        equipButtonRects.clear();

        if (overlay == Overlay.PRESETS) {
            initPresetsOverlay();
        } else if (overlay == Overlay.EQUIP) {
            initEquipOverlay();
        } else {
            initMainLayout();
        }
    }

    /** Пересобирает виджеты на текущих panelX/panelY — используется и после init(), и после перетаскивания. */
    private void rebuild() {
        clearChildren();
        init();
    }

    // ================================================================
    //  основной layout
    // ================================================================

    /** Прямоугольник превью-стойки на экране — общий для render() и обработки мыши. */
    // previewX сдвинут правее (было px+14, левые equip-кнопки на previewX-20
    // вылезали за левый край панели — см. скриншот). Слева отводим 24px под
    // колонку из 4 кнопок (по 18px + отступ), затем сам превью.
    private static final int EQUIP_COL_W = 24;
    private int previewX() { return panelX + 14 + EQUIP_COL_W; }
    private int previewY() { return panelY + TITLE_H + 6; }

    private void initMainLayout() {
        int px = panelX, py = panelY;

        // Маленькая "x" в шапке — единственная кнопка закрытия теперь (была
        // ещё большая "Close" внизу, дублирующая её — убрали). Отступ справа
        // был 20px при 16px кнопке (несимметрично), выровняли на 4px с обеих
        // сторон от угла панели, как отступ сверху.
        addDrawableChild(ButtonWidget.builder(Text.literal("x"), b -> close())
                .dimensions(px + PANEL_W - 20, py + 4, 16, 16).build());

        // -- paperdoll: превью + 6 кнопок-слотов экипировки вокруг него --
        int previewX = previewX(), previewY = previewY(), previewSize = PREVIEW_SIZE;
        addEquipButton(px + 14, previewY, "head");
        addEquipButton(px + 14, previewY + 22, "chest");
        addEquipButton(px + 14, previewY + 44, "legs");
        addEquipButton(px + 14, previewY + 66, "feet");
        addEquipButton(previewX + previewSize + 4, previewY, "mainhand");
        addEquipButton(previewX + previewSize + 4, previewY + 22, "offhand");

        // -- часть тела: 2 ряда по 3 кнопки --
        int partsX = px + 14 + EQUIP_COL_W + PREVIEW_SIZE + 4 + 18 + 8, partsY = py + TITLE_H + 6;
        int pw = 82, ph = 18, gap = 4;
        addPartButton(partsX, partsY, pw, ph, "armorstand_menu_part_head", ArmorStandDataUtil.Part.HEAD);
        addPartButton(partsX + pw + gap, partsY, pw, ph, "armorstand_menu_part_body", ArmorStandDataUtil.Part.BODY);
        addPartButton(partsX + 2 * (pw + gap), partsY, pw, ph, "armorstand_menu_part_left_arm", ArmorStandDataUtil.Part.LEFT_ARM);
        addPartButton(partsX, partsY + ph + gap, pw, ph, "armorstand_menu_part_right_arm", ArmorStandDataUtil.Part.RIGHT_ARM);
        addPartButton(partsX + pw + gap, partsY + ph + gap, pw, ph, "armorstand_menu_part_left_leg", ArmorStandDataUtil.Part.LEFT_LEG);
        addPartButton(partsX + 2 * (pw + gap), partsY + ph + gap, pw, ph, "armorstand_menu_part_right_leg", ArmorStandDataUtil.Part.RIGHT_LEG);

        // -- 3 ползунка X/Y/Z для выбранной части + кнопка сброса каждой оси --
        int sliderY = partsY + 2 * (ph + gap) + 8;
        int resetW = 18;
        int sliderW = (px + PANEL_W - 14) - partsX - resetW - gap;
        float[] cur = ArmorStandDataUtil.getPoseAll(workingStack, selectedPart);
        addDrawableChild(new AxisSlider(partsX, sliderY, sliderW, 18, "X", cur[0], ArmorStandDataUtil.Axis.X));
        addDrawableChild(new AxisSlider(partsX, sliderY + 20, sliderW, 18, "Y", cur[1], ArmorStandDataUtil.Axis.Y));
        addDrawableChild(new AxisSlider(partsX, sliderY + 40, sliderW, 18, "Z", cur[2], ArmorStandDataUtil.Axis.Z));
        addResetButton(partsX + sliderW + gap, sliderY, resetW, ArmorStandDataUtil.Axis.X);
        addResetButton(partsX + sliderW + gap, sliderY + 20, resetW, ArmorStandDataUtil.Axis.Y);
        addResetButton(partsX + sliderW + gap, sliderY + 40, resetW, ArmorStandDataUtil.Axis.Z);

        // -- флаги --
        // Раньше flagsY считался только от превью (previewY + previewSize + 14)
        // и на некоторых GUI-масштабах наезжал на третий слайдер (Z), т.к. тот
        // тоже заканчивается примерно на этой высоте. Берём максимум с учётом
        // фактического низа слайдеров, чтобы ряды никогда не пересекались.
        int slidersBottom = sliderY + 40 + 18;
        int flagsY = Math.max(previewY + previewSize + 14, slidersBottom + 10);
        int flagX = px + 14;
        addFlagCheckbox(flagX, flagsY, "armorstand_menu_flag_no_plate", "NoBasePlate");
        addFlagCheckbox(flagX + 90, flagsY, "armorstand_menu_flag_small", "Small");
        addFlagCheckbox(flagX + 160, flagsY, "armorstand_menu_flag_arms", "ShowArms");
        addFlagCheckbox(flagX + 225, flagsY, "armorstand_menu_flag_invis", "Invisible");
        addFlagCheckbox(flagX + 290, flagsY, "armorstand_menu_flag_marker", "Marker");

        // -- низ панели --
        // Была ещё третья кнопка "Close" здесь, дублирующая "x" в шапке —
        // убрали, теперь только Presets и Apply, поровну делят ширину.
        int bottomY = py + PANEL_H - 26;
        int bottomGap = 8;
        int bottomBtnW = (PANEL_W - 28 - bottomGap) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal(CIELang.get("armorstand_menu_presets")), b -> {
            overlay = Overlay.PRESETS;
            rebuild();
        }).dimensions(px + 14, bottomY, bottomBtnW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(CIELang.get("armorstand_menu_apply")), b -> {
            syncToServer();
            statusLine = Text.literal(CIELang.get("armorstand_menu_applied"));
        }).dimensions(px + 14 + bottomBtnW + bottomGap, bottomY, bottomBtnW, 20).build());
    }

    private void addPartButton(int x, int y, int w, int h, String labelKey, ArmorStandDataUtil.Part part) {
        String prefix = part == selectedPart ? "> " : "";
        // Индикатор "изменена" — точка после названия, если поза этой части
        // отличается от дефолтной (0,0,0). Сравниваем именно с нулём, а не с
        // состоянием на момент открытия окна: reset-кнопка тоже приводит к 0,
        // так что "изменена" и "не равна дефолту" — одно и то же понятие
        // везде в этом экране, без отдельного снапшота для сравнения.
        boolean modified = isPartModified(part);
        String suffix = modified ? " §c•" : "";
        addDrawableChild(ButtonWidget.builder(Text.literal(prefix + CIELang.get(labelKey) + suffix), b -> {
            selectedPart = part;
            rebuild();
        }).dimensions(x, y, w, h).build());
    }

    private boolean isPartModified(ArmorStandDataUtil.Part part) {
        float[] pose = ArmorStandDataUtil.getPoseAll(workingStack, part);
        return pose[0] != 0f || pose[1] != 0f || pose[2] != 0f;
    }

    /** Кнопка сброса одной оси текущей выбранной части в 0 — рядом со слайдером. */
    private void addResetButton(int x, int y, int w, ArmorStandDataUtil.Axis axis) {
        addDrawableChild(ButtonWidget.builder(Text.literal(CIELang.get("armorstand_menu_reset_axis")), b -> {
            ArmorStandDataUtil.resetPoseAxis(workingStack, selectedPart, axis);
            refreshPreviewEntity();
            rebuild();
        }).dimensions(x, y, w, 18).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                Text.literal(CIELang.getFormatted("armorstand_menu_reset_tooltip", axis.name())))).build());
    }

    private void addFlagCheckbox(int x, int y, String labelKey, String nbtKey) {
        boolean value = ArmorStandDataUtil.getFlag(workingStack, nbtKey);
        addDrawableChild(CheckboxWidget.builder(Text.literal(CIELang.get(labelKey)), this.textRenderer)
                .pos(x, y)
                .checked(value)
                .callback((checkbox, checked) -> {
                    ArmorStandDataUtil.setFlag(workingStack, nbtKey, checked);
                    refreshPreviewEntity();
                })
                .build());
    }

    /** x,y,slotKey каждой из 6 кнопок-слотов — иконка предмета рисуется поверх в render(), см. drawEquipIcons(). */
    private final List<Object[]> equipButtonRects = new java.util.ArrayList<>();

    private void addEquipButton(int x, int y, String slotKey) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                    equipSlotKey = slotKey;
                    overlay = Overlay.EQUIP;
                    rebuild();
                }).dimensions(x, y, 18, 18).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(shortSlotLabel(slotKey))))
                .build());
        equipButtonRects.add(new Object[]{x, y, slotKey});
    }

    /** Рисуется поверх кнопок из initMainLayout() — сами кнопки уже отрисованы через super.render(). */
    private void drawEquipIcons(DrawContext context) {
        for (Object[] rect : equipButtonRects) {
            int x = (int) rect[0], y = (int) rect[1];
            String slotKey = (String) rect[2];
            ItemStack equipped = EntitySettingsUtil.getEquipment(workingStack, slotKey, registries);
            if (!equipped.isEmpty()) {
                // drawItem рисует фиксированную иконку 16x16 от левого верхнего
                // угла — на кнопке 18x18 это давало видимый сдвиг иконки от
                // центра (на 1-2px, но заметно). Центрируем вручную.
                int iconX = x + (18 - 16) / 2;
                int iconY = y + (18 - 16) / 2;
                context.drawItem(equipped, iconX, iconY);
            }
        }
    }

    private static String shortSlotLabel(String key) {
        return switch (key) {
            case "head" -> CIELang.get("armorstand_menu_slot_head");
            case "chest" -> CIELang.get("armorstand_menu_slot_chest");
            case "legs" -> CIELang.get("armorstand_menu_slot_legs");
            case "feet" -> CIELang.get("armorstand_menu_slot_feet");
            case "mainhand" -> CIELang.get("armorstand_menu_slot_mainhand");
            case "offhand" -> CIELang.get("armorstand_menu_slot_offhand");
            default -> key;
        };
    }

    // ================================================================
    //  оверлей: пресеты поз
    // ================================================================

    private void initPresetsOverlay() {
        int px = panelX + 30, py = panelY + 30;
        addDrawableChild(ButtonWidget.builder(Text.literal(CIELang.get("armorstand_menu_back")), b -> {
            overlay = Overlay.NONE;
            rebuild();
        }).dimensions(px, py, 70, 18).build());

        List<String> names = ArmorStandDataUtil.presetNames();
        int rowY = py + 24;
        for (String name : names) {
            addDrawableChild(ButtonWidget.builder(Text.literal(name), b -> {
                ArmorStandDataUtil.applyPreset(workingStack, name);
                refreshPreviewEntity();
                overlay = Overlay.NONE;
                rebuild();
            }).dimensions(px, rowY, 220, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(CIELang.get("armorstand_menu_preset_delete")), b -> {
                ArmorStandDataUtil.removePreset(name);
                rebuild();
            }).dimensions(px + 224, rowY, 40, 18).build());
            rowY += 20;
            if (rowY > panelY + PANEL_H - 60) break; // не вылезаем за панель
        }

        int newRowY = py + 24 + Math.min(names.size(), 8) * 20 + 6;
        newItemField = new TextFieldWidget(this.textRenderer, px, newRowY, 180, 18, Text.literal(CIELang.get("armorstand_menu_preset_name_placeholder")));
        addDrawableChild(newItemField);
        addDrawableChild(ButtonWidget.builder(Text.literal(CIELang.get("armorstand_menu_preset_save")), b -> {
            String name = newItemField.getText().trim();
            if (!name.isEmpty()) {
                ArmorStandDataUtil.addPreset(name, workingStack);
                newItemField.setText("");
                rebuild();
            }
        }).dimensions(px + 184, newRowY, 100, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(CIELang.get("armorstand_menu_preset_clear_all")), b -> {
            ArmorStandDataUtil.clearPresets();
            rebuild();
        }).dimensions(px, newRowY + 24, 100, 18).build());
    }

    // ================================================================
    //  оверлей: выбор источника экипировки для одного слота
    // ================================================================

    private void initEquipOverlay() {
        int px = panelX + 30, py = panelY + 30;
        addDrawableChild(ButtonWidget.builder(Text.literal(CIELang.get("armorstand_menu_back")), b -> {
            overlay = Overlay.NONE;
            rebuild();
        }).dimensions(px, py, 70, 18).build());

        int rowY = py + 26;

        addDrawableChild(ButtonWidget.builder(Text.literal(CIELang.get("armorstand_menu_equip_from_offhand")), b -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null) return;
            ItemStack offHand = player.getOffHandStack();
            if (!offHand.isEmpty()) {
                EntitySettingsUtil.setEquipment(workingStack, equipSlotKey, offHand.copy(), registries);
                refreshPreviewEntity();
            }
            overlay = Overlay.NONE;
            rebuild();
        }).dimensions(px, rowY, 200, 18).build());
        rowY += 22;

        addDrawableChild(ButtonWidget.builder(Text.literal(CIELang.get("armorstand_menu_equip_from_storage")), b -> {
            String slotKeyCapture = equipSlotKey;
            StoragePicker.requestPick(picked -> {
                EntitySettingsUtil.setEquipment(workingStack, slotKeyCapture, picked, registries);
                refreshPreviewEntity();
                overlay = Overlay.NONE;
                MinecraftClient.getInstance().execute(() ->
                        MinecraftClient.getInstance().setScreen(armorStandEditScreen.this));
            });
            openStoragePicker();
        }).dimensions(px, rowY, 200, 18).build());
        rowY += 22;

        addDrawableChild(ButtonWidget.builder(Text.literal(CIELang.get("armorstand_menu_equip_clear_slot")), b -> {
            EntitySettingsUtil.setEquipment(workingStack, equipSlotKey, ItemStack.EMPTY, registries);
            refreshPreviewEntity();
            overlay = Overlay.NONE;
            rebuild();
        }).dimensions(px, rowY, 200, 18).build());
        rowY += 26;

        newItemField = new TextFieldWidget(this.textRenderer, px, rowY, 200, 18, Text.literal(CIELang.get("armorstand_menu_equip_item_id_placeholder")));
        newItemField.setText("minecraft:");
        addDrawableChild(newItemField);
        rowY += 22;
        addDrawableChild(ButtonWidget.builder(Text.literal(CIELang.get("armorstand_menu_equip_set_new_item")), b -> {
            String idStr = newItemField.getText().trim();
            Identifier id = Identifier.tryParse(idStr);
            if (id == null || !Registries.ITEM.containsId(id)) {
                statusLine = Text.literal(CIELang.getFormatted("armorstand_menu_equip_unknown_item", idStr));
                return;
            }
            Item item = Registries.ITEM.get(id);
            try {
                ItemStack toEquip = UseRemainderUtil.buildRemainderStack(item, 1, "", registries);
                EntitySettingsUtil.setEquipment(workingStack, equipSlotKey, toEquip, registries);
                refreshPreviewEntity();
                overlay = Overlay.NONE;
                rebuild();
            } catch (Exception e) {
                statusLine = Text.literal(CIELang.getFormatted("armorstand_menu_equip_failed", e.getMessage()));
            }
        }).dimensions(px, rowY, 200, 18).build());
    }

    private void openStoragePicker() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            try {
                int pageIndex = StoragePageUtil.getCurrentPageIndex();
                StoragePageUtil.setCurrentPageIndex(pageIndex);
                StoragePageUtil.Page page = StoragePageUtil.loadPage(pageIndex, registries);
                com.cie.screen.StorageScreenHandler handler =
                        new com.cie.screen.StorageScreenHandler(player.getInventory(), pageIndex, page);
                client.setScreen(new com.cie.screen.StorageScreen(handler, player.getInventory(), registries));
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    // ================================================================
    //  превью-сущность и синхронизация с сервером
    // ================================================================

    /**
     * Копия {@link InventoryScreen#drawEntity} с одной сутевой разницей: ванильный
     * метод жёстко высчитывает bodyYaw/pitch превью из курсора мыши по формуле
     * atan((center - mouse) / 40) — это (а) полностью игнорирует
     * entity.setYaw()/setPitch(), поэтому наши прошлые попытки крутить саму entity
     * ни на что не влияли, и (б) ограничивает угол небольшим диапазоном (реально
     * ~±20° из-за множителя *20 после atan), отсюда "слабая чувствительность по Y"
     * и вращение только базовой плиты — тело просто не успевало довернуться на
     * видимый угол.
     * Здесь вместо формулы курсора подставляем накопленные {@link #previewYaw}/
     * {@link #previewPitch} напрямую, без ограничения диапазона — полный оборот
     * по обеим осям, управляемый только драгом по превью.
     */
    private void drawArmorStandPreview(DrawContext context, int x1, int y1, int x2, int y2, int size, LivingEntity entity) {
        Quaternionf rotation = new Quaternionf().rotateZ(3.1415927F);
        Quaternionf pitchRotation = new Quaternionf().rotateX(previewPitch * 0.017453292F);
        rotation.mul(pitchRotation);

        EntityRenderManager dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer((Entity) entity);
        EntityRenderState renderState = renderer.getAndUpdateRenderState(entity, 1.0F);
        renderState.light = 15728880;
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;

        if (renderState instanceof LivingEntityRenderState livingState) {
            livingState.bodyYaw = 180.0F + previewYaw;
            livingState.relativeHeadYaw = 0.0F;
            livingState.pitch = previewPitch;
            livingState.width /= livingState.baseScale;
            livingState.height /= livingState.baseScale;
            livingState.baseScale = 1.0F;
        }

        Vector3f offset = new Vector3f(0.0F, renderState.height / 2.0F, 0.0F);
        context.addEntity(renderState, (float) size, offset, rotation, pitchRotation, x1, y1, x2, y2);
    }

    private void refreshPreviewEntity() {
        if (previewEntity == null) return;
        for (ArmorStandDataUtil.Part part : ArmorStandDataUtil.Part.values()) {
            float[] v = ArmorStandDataUtil.getPoseAll(workingStack, part);
            EulerAngle angle = new EulerAngle(v[0], v[1], v[2]);
            switch (part) {
                case HEAD -> previewEntity.setHeadRotation(angle);
                case BODY -> previewEntity.setBodyRotation(angle);
                case LEFT_ARM -> previewEntity.setLeftArmRotation(angle);
                case RIGHT_ARM -> previewEntity.setRightArmRotation(angle);
                case LEFT_LEG -> previewEntity.setLeftLegRotation(angle);
                case RIGHT_LEG -> previewEntity.setRightLegRotation(angle);
            }
        }
        previewEntity.setHideBasePlate(ArmorStandDataUtil.getFlag(workingStack, "NoBasePlate"));
        previewEntity.setShowArms(ArmorStandDataUtil.getFlag(workingStack, "ShowArms"));
        previewEntity.setInvisible(ArmorStandDataUtil.getFlag(workingStack, "Invisible"));

        byte currentFlags = previewEntity.getDataTracker().get(ArmorStandEntity.ARMOR_STAND_FLAGS);
        boolean small = ArmorStandDataUtil.getFlag(workingStack, "Small");
        boolean marker = ArmorStandDataUtil.getFlag(workingStack, "Marker");
        if (small) currentFlags |= 0x01; else currentFlags &= ~0x01;
        if (marker) currentFlags |= 0x10; else currentFlags &= ~0x10;
        previewEntity.getDataTracker().set(ArmorStandEntity.ARMOR_STAND_FLAGS, currentFlags);

        for (Map.Entry<String, EquipmentSlot> e : NBT_TO_SLOT.entrySet()) {
            ItemStack eq = EntitySettingsUtil.getEquipment(workingStack, e.getKey(), registries);
            previewEntity.equipStack(e.getValue(), eq);
        }
    }

    /** UndoUtil снимается один раз, лениво, при первом реальном изменении за сессию окна. */
    private boolean snapshotTaken = false;

    private void syncToServer() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if (!snapshotTaken) {
            UndoUtil.pushSnapshot(player, player.getInventory().getStack(handSlot));
            snapshotTaken = true;
        }
        player.getInventory().setStack(handSlot, workingStack);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            int packetSlot = 36 + handSlot;
            client.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(packetSlot, workingStack));
        }
    }

    // Троттлированная синхронизация слайдера (maybeSyncThrottled) убрана —
    // изменения позы теперь применяются только по явному нажатию Apply,
    // сервер больше не видит промежуточные значения при перетаскивании.

    // ================================================================
    //  перетаскивание панели за шапку
    // ================================================================

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button == 0 && overlay == Overlay.NONE
                && mouseX >= panelX && mouseX <= panelX + PANEL_W - 22
                && mouseY >= panelY && mouseY <= panelY + TITLE_H) {
            dragging = true;
            dragOffX = (int) mouseX - panelX;
            dragOffY = (int) mouseY - panelY;
            return true;
        }

        if (button == 0 && overlay == Overlay.NONE
                && mouseX >= previewX() && mouseX <= previewX() + PREVIEW_SIZE
                && mouseY >= previewY() && mouseY <= previewY() + PREVIEW_SIZE) {
            draggingPreview = true;
            lastPreviewDragX = mouseX;
            lastPreviewDragY = mouseY;
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging) {
            panelX = Math.max(0, Math.min(this.width - PANEL_W, (int) click.x() - dragOffX));
            panelY = Math.max(0, Math.min(this.height - PANEL_H, (int) click.y() - dragOffY));
            rebuild();
            return true;
        }
        if (draggingPreview) {
            double dx = click.x() - lastPreviewDragX;
            double dy = click.y() - lastPreviewDragY;
            lastPreviewDragX = click.x();
            lastPreviewDragY = click.y();

            // yaw подтверждён верным — не трогаем. pitch был инвертирован
            // (тянешь вниз — камера должна опускаться под стойку, было
            // наоборот), меняем знак только для него.
            float sensitivity = 2.5f;
            previewYaw -= (float) dx * sensitivity;
            previewYaw = ((previewYaw + 180f) % 360f + 360f) % 360f - 180f; // держим в (-180, 180]

            previewPitch -= (float) dy * sensitivity;
            previewPitch = Math.max(-89f, Math.min(89f, previewPitch)); // не переворачиваем вверх ногами

            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        draggingPreview = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (overlay == Overlay.NONE
                && mouseX >= previewX() && mouseX <= previewX() + PREVIEW_SIZE
                && mouseY >= previewY() && mouseY <= previewY() + PREVIEW_SIZE) {
            // verticalAmount > 0 — колесо от себя (вверх) — приближаем (модель крупнее).
            previewZoom += (float) verticalAmount * 4.0f;
            previewZoom = Math.max(PREVIEW_ZOOM_MIN, Math.min(PREVIEW_ZOOM_MAX, previewZoom));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ================================================================
    //  рендер
    // ================================================================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Никакого затемнения фона на весь экран — это плавающая, не
        // модальная панель, мир за ней должен оставаться видимым.
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, UiColorUtil.get("armorStandMenu.panelBackground"));
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + TITLE_H, UiColorUtil.get("armorStandMenu.titleBar"));
        context.drawStrokedRectangle(panelX, panelY, PANEL_W, PANEL_H, UiColorUtil.get("armorStandMenu.panelBorder"));
        context.drawTextWithShadow(this.textRenderer, this.title, panelX + 6, panelY + 6, UiColorUtil.get("armorStandMenu.titleText"));

        if (overlay == Overlay.NONE) {
            int previewX = previewX(), previewY = previewY(), previewSize = PREVIEW_SIZE;
            context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, UiColorUtil.get("armorStandMenu.previewBackground"));
            if (previewEntity != null) {
                try {
                    drawArmorStandPreview(context, previewX, previewY, previewX + previewSize, previewY + previewSize,
                            Math.round(previewZoom), previewEntity);
                } catch (Throwable ignored) {
                    context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(CIELang.get("armorstand_menu_preview_fallback")),
                            previewX + previewSize / 2, previewY + previewSize / 2, UiColorUtil.get("armorStandMenu.previewFallbackText"));
                }
            }

            float[] cur = ArmorStandDataUtil.getPoseAll(workingStack, selectedPart);
            String label = String.format(Locale.US, "%s: x=%.1f y=%.1f z=%.1f",
                    selectedPart.nbtKey, cur[0], cur[1], cur[2]);
            context.drawTextWithShadow(this.textRenderer, Text.literal(label), panelX + 150, panelY + 4, UiColorUtil.get("armorStandMenu.poseLabelText"));
        }

        if (statusLine != null) {
            context.drawTextWithShadow(this.textRenderer, statusLine, panelX + 14, panelY + PANEL_H - 40, UiColorUtil.get("armorStandMenu.statusLineText"));
        }

        super.render(context, mouseX, mouseY, delta);

        if (overlay == Overlay.NONE) {
            drawEquipIcons(context);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return overlay == Overlay.NONE;
    }

    @Override
    public void close() {
        if (overlay != Overlay.NONE) {
            overlay = Overlay.NONE;
            rebuild();
            return;
        }
        // Раньше здесь был неявный syncToServer() — закрытие окна применяло
        // все несохранённые правки на сервер, даже без нажатия Apply. Теперь
        // изменения применяются только по явному Apply — закрытие их
        // отбрасывает (сервер видит только то, что было заапплено).
        MinecraftClient.getInstance().setScreen(null);
    }

    // ================================================================
    //  ползунок оси X/Y/Z (-180..180°), пишет живьём в workingStack
    // ================================================================

    private class AxisSlider extends SliderWidget {
        private final String label;
        private final ArmorStandDataUtil.Axis axis;

        AxisSlider(int x, int y, int w, int h, String label, float degrees, ArmorStandDataUtil.Axis axis) {
            super(x, y, w, h, Text.literal(label + ": " + fmt(degrees) + "°"), (degrees + 180.0) / 360.0);
            this.label = label;
            this.axis = axis;
        }

        private float currentDegrees() {
            return (float) (this.value * 360.0 - 180.0);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + fmt(currentDegrees()) + "°"));
        }

        @Override
        protected void applyValue() {
            ArmorStandDataUtil.setPoseAxis(workingStack, selectedPart, axis, currentDegrees());
            refreshPreviewEntity();
        }

        @Override
        public boolean mouseReleased(Click click) {
            boolean result = super.mouseReleased(click);
            // Перестраиваем меню на отпускание, чтобы точка-индикатор "изменена"
            // на кнопке части появилась сразу, а не только после следующего
            // не связанного со слайдером rebuild() (например смены части).
            // Раньше здесь была угаданная сигнатура onRelease(double, double) —
            // не компилировалась под эти маппинги (SliderWidget такой метод не
            // переопределяет). mouseReleased(Click) — уже подтверждённая рабочая
            // сигнатура, используется в этом же файле для драга панели.
            rebuild();
            return result;
        }

        private static String fmt(float v) {
            return String.format(Locale.US, "%.1f", v);
        }
    }
}