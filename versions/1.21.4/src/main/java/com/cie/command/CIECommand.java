package com.cie.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import com.cie.text.MiniMessageBridge;
import com.cie.text.CIELang;
import com.cie.util.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandRegistryAccess;
import com.cie.util.UiColorUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.RegistryEntryReferenceArgumentType;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.ApplyEffectsConsumeEffect;
import net.minecraft.item.consume.ConsumeEffect;
import net.minecraft.item.consume.UseAction;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.visitor.StringNbtWriter;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.potion.Potion;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.DyeColor;
import net.minecraft.registry.Registries;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class CIECommand {

    private static final List<String> FORMATS = List.of("json", "mm", "plain");
    private static final SuggestionProvider<FabricClientCommandSource> FORMAT_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(FORMATS, builder);
    private static final List<String> ANIMATION_SUGGESTIONS_LIST = List.of("none", "eat", "drink", "block", "bow", "spear", "crossbow", "spyglass", "toot_horn", "brush");
    private static final List<String> BOOK_TYPES = List.of("original", "copy", "copy_of_copy", "tattered");
    private static final SuggestionProvider<FabricClientCommandSource> BOOK_TYPE_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(BOOK_TYPES, builder);

    private static final List<String> DYE_COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private static final SuggestionProvider<FabricClientCommandSource> DYE_COLOR_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(DYE_COLORS, builder);

    private static final SuggestionProvider<FabricClientCommandSource> TRIM_PATTERN_SUGGESTIONS =
            registrySuggestions(RegistryKeys.TRIM_PATTERN);
    private static final SuggestionProvider<FabricClientCommandSource> TRIM_MATERIAL_SUGGESTIONS =
            registrySuggestions(RegistryKeys.TRIM_MATERIAL);
    private static final SuggestionProvider<FabricClientCommandSource> BANNER_PATTERN_SUGGESTIONS =
            registrySuggestions(RegistryKeys.BANNER_PATTERN);
    private static final SuggestionProvider<FabricClientCommandSource> ITEM_SUGGESTIONS =
            registrySuggestions(RegistryKeys.ITEM);

    /** Динамически, по реестру предметов: все "..._sign", кроме "..._hanging_sign" (это отдельная фича, не табличка на палке/стене). */
    private static final SuggestionProvider<FabricClientCommandSource> SIGN_TYPE_SUGGESTIONS =
            (ctx, builder) -> {
                for (var entry : Registries.ITEM.getEntrySet()) {
                    String path = entry.getKey().getValue().getPath();
                    if (path.endsWith("_sign") && !path.contains("hanging")) {
                        String type = path.substring(0, path.length() - "_sign".length());
                        if (type.startsWith(builder.getRemaining().toLowerCase(Locale.ROOT))) {
                            builder.suggest(type);
                        }
                    }
                }
                return builder.buildFuture();
            };

    private static final List<String> RARITY_SUGGESTIONS_LIST = List.of("common", "uncommon", "rare", "epic");
    private static final SuggestionProvider<FabricClientCommandSource> RARITY_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(RARITY_SUGGESTIONS_LIST, builder);

    private static final SuggestionProvider<FabricClientCommandSource> JUKEBOX_SONG_SUGGESTIONS =
            registrySuggestions(RegistryKeys.JUKEBOX_SONG);
    private static final SuggestionProvider<FabricClientCommandSource> DAMAGE_TYPE_TAG_SUGGESTIONS =
            tagSuggestions(RegistryKeys.DAMAGE_TYPE);

    private static final SuggestionProvider<FabricClientCommandSource> MACRO_RECORD_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(MacroUtil.names(), builder);

    private CIECommand() {
    }

    /**
     * Ссылка на диспетчер, сохранённая при регистрации — нужна /cie repeat
     * и /cie macro records play, чтобы повторно выполнить уже собранную
     * командную строку через тот же дерево команд (а не дублировать логику
     * каждой команды вручную).
     */
    private static CommandDispatcher<FabricClientCommandSource> DISPATCHER;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess buildContext) {
        DISPATCHER = dispatcher;
        LiteralCommandNode<FabricClientCommandSource> root = dispatcher.register(buildTree("commanditemeditor", buildContext));
        // ВАЖНО: .redirect(root) сам по себе не подхватывает root.executes()
        // при вызове БЕЗ аргументов (голое "/cie") — Brigadier следует
        // редиректу, только когда после литерала есть ещё ввод для разбора
        // ("/cie repeat" и т.п. это не касается). Поэтому каждому алиасу
        // дополнительно вешаем свой .executes(...) с той же логикой, что и
        // у root, иначе "/cie" / "/ie" / "/ei" без аргументов молча не
        // находят команду, хотя "/commanditemeditor" (сам root) работает.
        dispatcher.register(ClientCommandManager.literal("cie").executes(CIECommand::showInfoScreen).redirect(root));
        dispatcher.register(ClientCommandManager.literal("ie").executes(CIECommand::showInfoScreen).redirect(root));
        dispatcher.register(ClientCommandManager.literal("ei").executes(CIECommand::showInfoScreen).redirect(root));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildTree(String name, CommandRegistryAccess buildContext) {
        return ClientCommandManager.literal(name)
                .executes(CIECommand::showInfoScreen)
                .then(giveNode())
                .then(ClientCommandManager.literal("export")
                        .then(ClientCommandManager.literal("giveCommand").executes(CIECommand::exportGiveCommand))
                        .then(ClientCommandManager.literal("JSON").executes(CIECommand::exportJson)))
                .then(ClientCommandManager.literal("undo").executes(CIECommand::undo))
                .then(ClientCommandManager.literal("redo").executes(CIECommand::redo))
                .then(ClientCommandManager.literal("repeat").executes(CIECommand::repeatLast))
                .then(ClientCommandManager.literal("stats").executes(CIECommand::showStats))
                .then(ClientCommandManager.literal("import")
                        .then(ClientCommandManager.argument("snbt", StringArgumentType.greedyString())
                                .executes(CIECommand::importItem)))
                .then(macroNode())
                .then(templateNode())
                .then(ClientCommandManager.literal("diff").executes(CIECommand::diffHands))
                .then(ClientCommandManager.literal("chaos")
                        .executes(CIECommand::chaosDefault)
                        .then(ClientCommandManager.argument("overwrite", BoolArgumentType.bool())
                                .executes(CIECommand::chaosWithOverwrite)))
                .then(ClientCommandManager.literal("reloadConfig").executes(CIECommand::reloadConfig))
                .then(ClientCommandManager.literal("clearinv").executes(CIECommand::clearInventory)
                        .then(ClientCommandManager.literal("hotbar").executes(CIECommand::clearInventoryHotbar))
                        .then(ClientCommandManager.literal("armor").executes(CIECommand::clearInventoryArmor))
                        .then(ClientCommandManager.literal("offhand").executes(CIECommand::clearInventoryOffhand))
                        .then(ClientCommandManager.literal("hand").executes(CIECommand::clearInventoryHand))
                        .then(ClientCommandManager.literal("inventory").executes(CIECommand::clearInventoryMain)))
                .then(mathNode())
                .then(stackNode())
                .then(mouseHistoryNode())
                .then(languageNode())
                .then(gradientNode())
                .then(soundNode())
                .then(storagePagesNode())
                .then(coloringNode())
                .then(paintNode())
                .then(pickColorNode())
                .then(livePreviewNode())
                .then(editNode(buildContext));
    }

    // ================================================================
    //  /cie paint <ключ> get/set/reset — покраска GUI-элементов через
    //  UiColorUtil (см. util/UiColorUtil.java). Работает для любых
    //  зарегистрированных ключей: colorPicker.*, armorStandMenu.* и т.д.
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> paintNode() {
        return ClientCommandManager.literal("paint")
                .then(ClientCommandManager.argument("key", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandSource.suggestMatching(UiColorUtil.knownKeys(), builder))
                        .then(ClientCommandManager.literal("get")
                                .executes(CIECommand::uiColorPaintGet))
                        .then(ClientCommandManager.literal("reset")
                                .executes(CIECommand::uiColorPaintReset))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("argb", StringArgumentType.word())
                                        .executes(CIECommand::uiColorPaintSet))));
    }

    private static int uiColorPaintGet(CommandContext<FabricClientCommandSource> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        int argb = UiColorUtil.get(key);
        boolean overridden = UiColorUtil.isOverridden(key);
        sendLangFeedback(ctx.getSource(), "colorpicker_paint_status",
                key, String.format("%08X", argb), overridden ? "override" : "default");
        return 1;
    }

    private static int uiColorPaintSet(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String key = StringArgumentType.getString(ctx, "key");
        String raw = StringArgumentType.getString(ctx, "argb");
        int argb = parseArgb(ctx, raw);
        UiColorUtil.set(key, argb);
        sendLangFeedback(ctx.getSource(), "colorpicker_paint_set", key, String.format("%08X", argb));
        return 1;
    }

    private static int uiColorPaintReset(CommandContext<FabricClientCommandSource> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        UiColorUtil.reset(key);
        sendLangFeedback(ctx.getSource(), "colorpicker_paint_reset", key, String.format("%08X", UiColorUtil.get(key)));
        return 1;
    }

    // ================================================================
    //  /cie pickColor — замораживает кадр и даёт взять цвет любого
    //  пикселя на экране мышью (эффект "eyedropper"), копирует HEX
    //  в системный клипборд. Реализация — com.cie.screen.ColorPickScreen.
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> pickColorNode() {
        return ClientCommandManager.literal("pickColor")
                .executes(CIECommand::openColorPickScreen);
    }

    private static int openColorPickScreen(CommandContext<FabricClientCommandSource> ctx) {
        System.out.println("CIE-DEBUG: openColorPickScreen command reached");
        // ChatScreen после выполнения команды сам закрывает себя через
        // setScreen(null) — если наш экран открывать синхронно в
        // client.execute(...), очередь дренится ещё внутри того же
        // keyPressed() и ChatScreen.close() затирает уже открытый нами
        // экран. openScreenNextTick откладывает открытие на END_CLIENT_TICK,
        // который гарантированно срабатывает после всего input этого тика.
        openScreenNextTick(com.cie.screen.ColorPickScreen::new);
        return 1;
    }

    /**
     * В отличие от parseHex(...) (используется у /cie color dye|map),
     * здесь нужно принимать 8-значный ARGB с альфой — а такое значение
     * (например FFFFFFFF) не влезает в Integer.parseInt со знаком и там
     * просто упадёт с переполнением. Парсим через Long и приводим к int.
     * 6-значный вход (без альфы) трактуем как непрозрачный (альфа = FF).
     */
    private static int parseArgb(CommandContext<FabricClientCommandSource> ctx, String hex) throws CommandSyntaxException {
        String clean = hex.replace("#", "");
        try {
            if (clean.length() <= 6) {
                long rgb = Long.parseLong(clean, 16);
                return (int) (0xFF000000L | rgb);
            }
            return (int) Long.parseLong(clean, 16);
        } catch (NumberFormatException e) {
            throw createException("potion_bad_color", hex);
        }
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> trimNode() {
        return ClientCommandManager.literal("trim")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getTrim))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearTrim))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("pattern", IdentifierArgumentType.identifier())
                                .suggests(TRIM_PATTERN_SUGGESTIONS)
                                .then(ClientCommandManager.argument("material", IdentifierArgumentType.identifier())
                                        .suggests(TRIM_MATERIAL_SUGGESTIONS)
                                        .executes(CIECommand::setTrim))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> colorNode() {
        return ClientCommandManager.literal("color")
                .then(ClientCommandManager.literal("dye")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getDyeColor))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("hex", StringArgumentType.word())
                                        .executes(ctx -> setDyeColor(ctx, StringArgumentType.getString(ctx, "hex")))))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearDyeColor)))
                .then(ClientCommandManager.literal("map")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getMapColor))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("hex", StringArgumentType.word())
                                        .executes(ctx -> setMapColor(ctx, StringArgumentType.getString(ctx, "hex")))))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearMapColor)));
    }

    private static int getDyeColor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        Integer color = ColorComponentUtil.getDyedColor(stack);
        sendLangFeedback(ctx.getSource(), "color_dye_status", color == null ? "нет" : String.format("#%06X", color));
        return 1;
    }

    private static int setDyeColor(CommandContext<FabricClientCommandSource> ctx, String hex) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int color = parseHex(ctx, hex);
        ColorComponentUtil.setDyedColor(stack, color);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "color_dye_set", String.format("%06X", color));
        return 1;
    }

    private static int clearDyeColor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        ColorComponentUtil.removeDyedColor(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "color_dye_cleared");
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> consumableNode(CommandRegistryAccess buildContext) {
        return ClientCommandManager.literal("consumable")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getConsumable))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearConsumable))

                // /cie consumable seconds get/set <float>
                .then(ClientCommandManager.literal("seconds")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getConsumableSeconds))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0.0f))
                                        .executes(CIECommand::setConsumableSeconds))))

                // /cie consumable animation get/set <animation>
                .then(ClientCommandManager.literal("animation")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getConsumableAnimation))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", StringArgumentType.word())
                                        .suggests((ctx, builder) -> CommandSource.suggestMatching(ANIMATION_SUGGESTIONS_LIST, builder))
                                        .executes(CIECommand::setConsumableAnimation))))

                // /cie consumable sound get/set <identifier>
                .then(ClientCommandManager.literal("sound")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getConsumableSound))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", IdentifierArgumentType.identifier())
                                        .suggests(registrySuggestions(RegistryKeys.SOUND_EVENT))
                                        .executes(CIECommand::setConsumableSound))))

                // /rnm consumable effect ...
                .then(ClientCommandManager.literal("effect")
                        .then(ClientCommandManager.literal("clear")
                                .executes(CIECommand::clearConsumableEffects))
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("effect", RegistryEntryReferenceArgumentType.registryEntry(buildContext, RegistryKeys.STATUS_EFFECT))
                                        .then(ClientCommandManager.argument("duration", IntegerArgumentType.integer(1))
                                                .then(ClientCommandManager.argument("amplifier", IntegerArgumentType.integer(0))
                                                        .then(ClientCommandManager.argument("chance", FloatArgumentType.floatArg(0.0f, 1.0f))
                                                                .executes(CIECommand::addConsumableEffect)))))))

                // /cie consumable particles get/set <true/false>
                .then(ClientCommandManager.literal("particles")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getConsumableParticles))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(CIECommand::setConsumableParticles))));
    }

    private static int getConsumableSeconds(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        ConsumableComponent comp = ConsumableComponentUtil.getConsumable(stack);
        if (comp == null) throw createException("consumable_none");
        sendLangFeedback(ctx.getSource(), "consumable_seconds_status", comp.consumeSeconds());
        return 1;
    }

    private static int getConsumableAnimation(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        ConsumableComponent comp = ConsumableComponentUtil.getConsumable(stack);
        if (comp == null) throw createException("consumable_none");
        sendLangFeedback(ctx.getSource(), "consumable_animation_status", comp.useAction().name());
        return 1;
    }

    private static int getConsumableSound(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        ConsumableComponent comp = ConsumableComponentUtil.getConsumable(stack);
        if (comp == null) throw createException("consumable_none");
        String sound = comp.sound() == null
                ? "нет"
                : comp.sound().getKey().map(k -> k.getValue().toString()).orElse("нет");
        sendLangFeedback(ctx.getSource(), "consumable_sound_status", sound);
        return 1;
    }

    private static int getConsumableParticles(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        ConsumableComponent comp = ConsumableComponentUtil.getConsumable(stack);
        if (comp == null) throw createException("consumable_none");
        sendLangFeedback(ctx.getSource(), "consumable_particles_status", comp.hasConsumeParticles());
        return 1;
    }

    private static int addConsumableEffect(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        if (ConsumableComponentUtil.getConsumable(stack) == null) {
            throw createException("consumable_none");
        }

        @SuppressWarnings("unchecked")
        RegistryEntry.Reference<StatusEffect> effect = RegistryEntryReferenceArgumentType.getRegistryEntry(
                (CommandContext) ctx, "effect", RegistryKeys.STATUS_EFFECT
        );
        int duration = IntegerArgumentType.getInteger(ctx, "duration");
        int amplifier = IntegerArgumentType.getInteger(ctx, "amplifier");
        float chance = FloatArgumentType.getFloat(ctx, "chance");

        StatusEffectInstance instance = new StatusEffectInstance(effect, duration, amplifier);
        ConsumableComponentUtil.addEffect(stack, instance, chance);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "consumable_effect_added");
        return 1;
    }

    private static int clearConsumableEffects(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        ConsumableComponent comp = ConsumableComponentUtil.getConsumable(stack);
        if (comp == null) {
            throw createException("consumable_none");
        }

        ConsumableComponent updated = new ConsumableComponent(
                comp.consumeSeconds(),
                comp.useAction(),
                comp.sound(),
                comp.hasConsumeParticles(),
                List.of()
        );

        stack.set(DataComponentTypes.CONSUMABLE, updated);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "consumable_effect_cleared");
        return 1;
    }


    private static int setConsumableSeconds(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        float val = FloatArgumentType.getFloat(ctx, "value");
        ConsumableComponentUtil.setSeconds(stack, val);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "consumable_set");
        return 1;
    }

    private static int setConsumableAnimation(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String animStr = StringArgumentType.getString(ctx, "value").toUpperCase(Locale.ROOT);
        UseAction action = UseAction.valueOf(animStr);
        ConsumableComponentUtil.setAnimation(stack, action);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "consumable_set");
        return 1;
    }

    private static int setConsumableSound(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier soundId = ctx.getArgument("value", Identifier.class);
        RegistryEntry<SoundEvent> sound = resolveEntry(RegistryKeys.SOUND_EVENT, soundId.toString());
        ConsumableComponentUtil.setSound(stack, sound);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "consumable_set");
        return 1;
    }

    private static int setConsumableParticles(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        boolean val = BoolArgumentType.getBool(ctx, "value");
        ConsumableComponentUtil.setParticles(stack, val);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "consumable_set");
        return 1;
    }

    private static int getConsumable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        ConsumableComponent comp = ConsumableComponentUtil.getConsumable(stack);
        if (comp == null) {
            sendLangFeedback(ctx.getSource(), "consumable_empty");
            return 0;
        }
        sendLangFeedback(
                ctx.getSource(),
                "consumable_status",
                comp.consumeSeconds(),
                comp.useAction().name(),
                comp.sound().getKey().map(k -> k.getValue().toString()).orElse("none"),
                comp.hasConsumeParticles()
        );

        List<ConsumeEffect> effects = comp.onConsumeEffects();
        if (effects.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "consumable_effects_empty");
        } else {
            for (int i = 0; i < effects.size(); i++) {
                sendLangFeedback(ctx.getSource(), "consumable_effect_entry", i + 1, describeConsumeEffect(effects.get(i)));
            }
        }
        return 1;
    }

    /** Человекочитаемое описание одного ConsumeEffect (эффекта поедания) для вывода в чат. */
    private static String describeConsumeEffect(ConsumeEffect effect) {
        if (effect instanceof ApplyEffectsConsumeEffect apply) {
            StringBuilder sb = new StringBuilder();
            for (StatusEffectInstance instance : apply.effects()) {
                String id = instance.getEffectType().getKey().map(k -> k.getValue().toString()).orElse("?");
                if (sb.length() > 0) sb.append(", ");
                sb.append(id).append(" (ур.").append(instance.getAmplifier() + 1)
                        .append(", ").append(instance.getDuration()).append("т)");
            }
            sb.append(" [шанс=").append(apply.probability()).append("]");
            return sb.toString();
        }
        // Другие типы ConsumeEffect (RemoveEffects/ClearAllEffects/TeleportRandomly/PlaySound и т.д.) —
        // показываем как есть, без детального разбора.
        return effect.getClass().getSimpleName();
    }

    private static int setConsumable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        float seconds = FloatArgumentType.getFloat(ctx, "seconds");
        String animStr = StringArgumentType.getString(ctx, "animation").toUpperCase(Locale.ROOT);
        UseAction anim = UseAction.valueOf(animStr);
        Identifier soundId = ctx.getArgument("sound", Identifier.class);
        RegistryEntry<SoundEvent> sound = resolveEntry(RegistryKeys.SOUND_EVENT, soundId.toString());
        boolean hasParticles = BoolArgumentType.getBool(ctx, "hasParticles");

        ConsumableComponentUtil.setConsumable(stack, seconds, anim, sound, hasParticles);
        syncHandItem(player, stack);

        sendLangFeedback(ctx.getSource(), "consumable_set", seconds, animStr, soundId.toString(), hasParticles);
        return 1;
    }

    private static int clearConsumable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        ConsumableComponentUtil.removeConsumable(stack);
        syncHandItem(player, stack);

        sendLangFeedback(ctx.getSource(), "consumable_cleared");
        return 1;
    }

    private static int getMapColor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        Integer color = ColorComponentUtil.getMapColor(stack);
        sendLangFeedback(ctx.getSource(), "color_map_status", color == null ? "нет" : String.format("#%06X", color));
        return 1;
    }

    private static int setMapColor(CommandContext<FabricClientCommandSource> ctx, String hex) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int color = parseHex(ctx, hex);
        ColorComponentUtil.setMapColor(stack, color);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "color_map_set", String.format("%06X", color));
        return 1;
    }

    private static int clearMapColor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        ColorComponentUtil.removeMapColor(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "color_map_cleared");
        return 1;
    }

    // ================================================================
    //  /rnm count ...
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> countNode() {
        return ClientCommandManager.literal("count")
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearCount))
                .then(ClientCommandManager.literal("get").executes(CIECommand::getCount))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.literal("@max").executes(CIECommand::setCountToMax))
                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> setCount(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> addCount(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(ClientCommandManager.literal("take")
                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> takeCount(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(ClientCommandManager.literal("max")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getMaxStackSize))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1, 99))
                                        .executes(ctx -> setMaxStackSize(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearMaxStackSize)));
    }

    /** /cie count clear — сброс количества к 1 (дефолт). */
    private static int clearCount(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return setCount(ctx, 1);
    }

    /** /cie count set @max — выставить максимум, который допускает max_stack_size предмета. */
    private static int setCountToMax(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int max = stack.getOrDefault(DataComponentTypes.MAX_STACK_SIZE, stack.getItem().getMaxCount());
        stack.setCount(max);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "count_set", max);
        return 1;
    }

    private static int getMaxStackSize(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        int max = stack.getOrDefault(DataComponentTypes.MAX_STACK_SIZE, stack.getItem().getMaxCount());
        sendLangFeedback(ctx.getSource(), "count_max_status", max);
        return 1;
    }

    private static int getCount(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        int max = stack.getOrDefault(DataComponentTypes.MAX_STACK_SIZE, stack.getItem().getMaxCount());
        sendLangFeedback(ctx.getSource(), "count_status", stack.getCount(), max);
        return 1;
    }

    private static int setCount(CommandContext<FabricClientCommandSource> ctx, int amount) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.setCount(amount);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "count_set", amount);
        return 1;
    }

    private static int addCount(CommandContext<FabricClientCommandSource> ctx, int amount) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.increment(amount);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "count_set", stack.getCount());
        return 1;
    }

    private static int takeCount(CommandContext<FabricClientCommandSource> ctx, int amount) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.decrement(Math.min(amount, stack.getCount()));
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "count_set", stack.getCount());
        return 1;
    }

    private static int setMaxStackSize(CommandContext<FabricClientCommandSource> ctx, int amount) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.set(DataComponentTypes.MAX_STACK_SIZE, amount);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "count_max_set", amount);
        return 1;
    }

    private static int clearMaxStackSize(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.remove(DataComponentTypes.MAX_STACK_SIZE);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "count_max_cleared");
        return 1;
    }

    // ================================================================
    //  /rnm durability ...
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> durabilityNode() {
        return ClientCommandManager.literal("durability")
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearDurability))
                .then(ClientCommandManager.literal("get").executes(CIECommand::getDurability))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("damage", IntegerArgumentType.integer(0))
                                .executes(ctx -> setDamage(ctx, IntegerArgumentType.getInteger(ctx, "damage")))))
                .then(ClientCommandManager.literal("max")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getMaxDamage))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> setMaxDamage(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearMaxDamage)))
                .then(ClientCommandManager.literal("unbreakable")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getUnbreakable))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setUnbreakable(ctx, BoolArgumentType.getBool(ctx, "value")))))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearUnbreakable)));
    }

    /** /cie durability clear — сброс урона до 0 (предмет как новый). */
    private static int clearDurability(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return setDamage(ctx, 0);
    }

    private static int getMaxDamage(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "durability_max_status", stack.getMaxDamage());
        return 1;
    }

    private static int getDurability(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        int damage = stack.getDamage();
        int maxDamage = stack.getMaxDamage();
        sendLangFeedback(ctx.getSource(), "durability_status", maxDamage - damage, maxDamage, damage);
        return 1;
    }

    private static int setDamage(CommandContext<FabricClientCommandSource> ctx, int damage) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.setDamage(damage);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "durability_set", damage);
        return 1;
    }

    private static int setMaxDamage(CommandContext<FabricClientCommandSource> ctx, int amount) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.set(DataComponentTypes.MAX_DAMAGE, amount);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "durability_max_set", amount);
        return 1;
    }

    private static int clearMaxDamage(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.remove(DataComponentTypes.MAX_DAMAGE);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "durability_max_cleared");
        return 1;
    }

    private static int getUnbreakable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        boolean isUnbreakable = stack.contains(DataComponentTypes.UNBREAKABLE);
        sendLangFeedback(ctx.getSource(), "unbreakable_status", isUnbreakable);
        return 1;
    }

    private static int setUnbreakable(CommandContext<FabricClientCommandSource> ctx, boolean value) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        if (value) {
            stack.set(DataComponentTypes.UNBREAKABLE, new net.minecraft.component.type.UnbreakableComponent(true));
        } else {
            stack.remove(DataComponentTypes.UNBREAKABLE);
        }
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "unbreakable_set", value);
        return 1;
    }

    private static int clearUnbreakable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return setUnbreakable(ctx, false);
    }

    // ================================================================
    //  /rnm equipable ...
    // ================================================================

    private static final List<String> EQUIPMENT_SLOTS = List.of("mainhand", "offhand", "feet", "legs", "chest", "head", "body");
    private static final SuggestionProvider<FabricClientCommandSource> EQUIPMENT_SLOT_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(EQUIPMENT_SLOTS, builder);

    // ================================================================
    //  /cie edit playerHead ...  (PROFILE — голова игрока, minecraft:profile)
    // ================================================================

    private static final SuggestionProvider<FabricClientCommandSource> ONLINE_PLAYER_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(PlayerHeadUtil.onlinePlayerNames(), builder);

    private static LiteralArgumentBuilder<FabricClientCommandSource> playerHeadNode() {
        return ClientCommandManager.literal("playerHead")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getPlayerHead))
                .then(ClientCommandManager.literal("reset").executes(CIECommand::resetPlayerHead))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("texture", StringArgumentType.greedyString())
                                .executes(CIECommand::setPlayerHeadTexture)))
                .then(ClientCommandManager.literal("getFromPlayer")
                        .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                .suggests(ONLINE_PLAYER_SUGGESTIONS)
                                .executes(CIECommand::getPlayerHeadFromPlayer)));
    }

    private static int getPlayerHead(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        String info = PlayerHeadUtil.describe(stack);
        if (info == null) {
            sendLangFeedback(ctx.getSource(), "playerhead_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "playerhead_status", info);
        return 1;
    }

    private static int resetPlayerHead(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        PlayerHeadUtil.reset(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "playerhead_reset");
        return 1;
    }

    private static int setPlayerHeadTexture(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String base64 = StringArgumentType.getString(ctx, "texture");
        PlayerHeadUtil.setTexture(stack, base64);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "playerhead_set");
        return 1;
    }

    /**
     * Профиль берётся ИСКЛЮЧИТЕЛЬНО из клиентского таб-листа (см.
     * PlayerHeadUtil про SkinsRestorer) — если игрока сейчас не видно в
     * таб-листе (не в сети / пакет ещё не пришёл), команда падает с
     * понятной ошибкой, а не лезет за скином куда-то ещё.
     */
    private static int getPlayerHeadFromPlayer(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        String name = StringArgumentType.getString(ctx, "player");
        var profile = PlayerHeadUtil.findOnlineProfile(name);
        if (profile == null) {
            throw createException("playerhead_player_not_found", name);
        }
        UndoUtil.pushSnapshot(player, stack);

        PlayerHeadUtil.setFromGameProfile(stack, profile);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "playerhead_set_from_player", name);
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> equipableNode() {
        return ClientCommandManager.literal("equipable")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getEquipable))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearEquipable))
                .then(ClientCommandManager.literal("slot")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getEquipableSlot))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("slot", StringArgumentType.word())
                                        .suggests(EQUIPMENT_SLOT_SUGGESTIONS)
                                        .executes(CIECommand::setEquipableSlot))))
                .then(ClientCommandManager.literal("model")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getEquipableModel))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                        .executes(CIECommand::setEquipableModel)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetEquipableModel)))
                .then(ClientCommandManager.literal("sound")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getEquipableSound))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                        .suggests(registrySuggestions(RegistryKeys.SOUND_EVENT))
                                        .executes(CIECommand::setEquipableSound)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetEquipableSound)))
                .then(ClientCommandManager.literal("swappable")
                        .then(ClientCommandManager.literal("get").executes(ctx -> getEquipableFlag(ctx, "swappable")))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setEquipableFlag(ctx, "swappable")))))
                .then(ClientCommandManager.literal("dispensable")
                        .then(ClientCommandManager.literal("get").executes(ctx -> getEquipableFlag(ctx, "dispensable")))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setEquipableFlag(ctx, "dispensable")))))
                .then(ClientCommandManager.literal("damageOnHurt")
                        .then(ClientCommandManager.literal("get").executes(ctx -> getEquipableFlag(ctx, "damageonhurt")))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setEquipableFlag(ctx, "damageonhurt")))));
    }

    private static int getEquipableSlot(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        EquippableComponent comp = EquipableComponentUtil.getEquipable(stack);
        sendLangFeedback(ctx.getSource(), "equipable_status", comp == null ? "нет" : comp.slot().asString());
        return 1;
    }

    private static int getEquipableModel(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        EquippableComponent comp = EquipableComponentUtil.getEquipable(stack);
        String model = comp == null ? "нет" : comp.assetId().map(k -> k.getValue().toString()).orElse("default");
        sendLangFeedback(ctx.getSource(), "equipable_model_status", model);
        return 1;
    }

    private static int getEquipableSound(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        EquippableComponent comp = EquipableComponentUtil.getEquipable(stack);
        String sound = comp == null || comp.equipSound() == null
                ? "нет"
                : comp.equipSound().getKey().map(k -> k.getValue().toString()).orElse("default");
        sendLangFeedback(ctx.getSource(), "equipable_sound_status", sound);
        return 1;
    }

    private static int getEquipableFlag(CommandContext<FabricClientCommandSource> ctx, String flag) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        EquippableComponent comp = EquipableComponentUtil.getEquipable(stack);
        boolean val;
        if (comp == null) {
            val = true;
        } else {
            val = switch (flag) {
                case "swappable" -> comp.swappable();
                case "dispensable" -> comp.dispensable();
                default -> comp.damageOnHurt();
            };
        }
        sendLangFeedback(ctx.getSource(), "equipable_flag_status", flag, val);
        return 1;
    }

    private static int resetEquipableModel(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        EquipableComponentUtil.resetModel(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "equipable_model_reset");
        return 1;
    }

    private static int resetEquipableSound(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        EquipableComponentUtil.resetSound(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "equipable_sound_reset");
        return 1;
    }

    private static int setEquipableSlot(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String slotStr = StringArgumentType.getString(ctx, "slot");
        EquipmentSlot slot = EquipmentSlot.byName(slotStr.toLowerCase(Locale.ROOT));

        EquipableComponentUtil.setSlot(stack, slot);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "equipable_set", slot.asString());
        return 1;
    }

    private static int setEquipableModel(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier modelId = ctx.getArgument("id", Identifier.class);
        EquipableComponentUtil.setModel(stack, modelId);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "equipable_model_set", modelId.toString());
        return 1;
    }

    private static int setEquipableSound(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier soundId = ctx.getArgument("id", Identifier.class);
        RegistryEntry<SoundEvent> sound = resolveEntry(RegistryKeys.SOUND_EVENT, soundId.toString());

        EquipableComponentUtil.setSound(stack, sound);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "equipable_sound_set", soundId.toString());
        return 1;
    }

    private static int setEquipableFlag(CommandContext<FabricClientCommandSource> ctx, String flag) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        boolean val = BoolArgumentType.getBool(ctx, "value");
        EquipableComponentUtil.setFlag(stack, flag, val);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "equipable_flag_set", flag, val);
        return 1;
    }

    private static int getEquipable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        EquippableComponent comp = stack.get(DataComponentTypes.EQUIPPABLE);
        sendLangFeedback(ctx.getSource(), "equipable_status", comp == null ? "нет" : comp.slot().asString());
        return 1;
    }

    private static int clearEquipable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.remove(DataComponentTypes.EQUIPPABLE);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "equipable_cleared");
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> componentNode() {
        return ClientCommandManager.literal("component")
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearAllComponents))
                .then(ClientCommandManager.literal("get")
                        .then(ClientCommandManager.argument("type", IdentifierArgumentType.identifier())
                                .suggests(COMPONENT_TYPE_SUGGESTIONS)
                                .executes(CIECommand::getComponent)))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("type", IdentifierArgumentType.identifier())
                                .suggests(COMPONENT_TYPE_SUGGESTIONS)
                                .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                        .executes(CIECommand::setComponent))))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("type", IdentifierArgumentType.identifier())
                                .suggests(COMPONENT_TYPE_SUGGESTIONS)
                                .executes(CIECommand::removeComponent)))
                .then(ClientCommandManager.literal("disable")
                        .then(ClientCommandManager.argument("type", IdentifierArgumentType.identifier())
                                .suggests(COMPONENT_TYPE_SUGGESTIONS)
                                .executes(CIECommand::disableComponent)))
                .then(ClientCommandManager.literal("copy")
                        .then(ClientCommandManager.argument("type", IdentifierArgumentType.identifier())
                                .suggests(COMPONENT_TYPE_SUGGESTIONS)
                                .executes(CIECommand::copyComponent)))
                .then(ClientCommandManager.literal("paste").executes(CIECommand::pasteComponent))
                .then(ClientCommandManager.literal("item")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getFullItemInfo)));
    }

    /**
     * /cie component clear — стирает ВСЕ компоненты предмета, КРОМЕ
     * CUSTOM_NAME и LORE (те не в зоне ответственности "component",
     * ими управляют /cie name и /cie lore соответственно).
     */
    private static int clearAllComponents(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int removed = 0;
        for (ComponentType<?> type : Registries.DATA_COMPONENT_TYPE) {
            if (type == DataComponentTypes.CUSTOM_NAME || type == DataComponentTypes.LORE) {
                continue;
            }
            if (stack.contains(type)) {
                stack.remove(type);
                removed++;
            }
        }

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "component_cleared_all", removed);
        return 1;
    }

    private static final SuggestionProvider<FabricClientCommandSource> COMPONENT_TYPE_SUGGESTIONS = (ctx, builder) -> {
        for (Identifier id : Registries.DATA_COMPONENT_TYPE.getIds()) {
            builder.suggest(id.toString());
        }
        return builder.buildFuture();
    };

    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static int getComponent(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);

        Identifier typeId = IdentifierArgumentType.getIdentifier((CommandContext) ctx, "type");
        ComponentType<?> type = Registries.DATA_COMPONENT_TYPE.get(typeId);

        if (type == null || !stack.contains(type)) {
            sendLangFeedback(ctx.getSource(), "component_not_found", typeId.toString());
            return 0;
        }

        Object value = stack.get(type);

        @SuppressWarnings({"unchecked", "rawtypes"})
        Codec rawCodec = type.getCodec();
        if (rawCodec == null) {
            // У компонента нет кодека (обычно это чисто клиентские/маркерные компоненты) —
            // в этом случае честного JSON не существует, показываем как есть.
            sendCopyableRaw(ctx.getSource(), String.valueOf(value));
            return 1;
        }

        try {
            RegistryOps<JsonElement> ops = getRegistries().getOps(JsonOps.INSTANCE);
            @SuppressWarnings("unchecked")
            DataResult<JsonElement> result = rawCodec.encodeStart(ops, value);
            JsonElement json = result.getOrThrow();
            String pretty = PRETTY_GSON.toJson(json);
            sendCopyableRaw(ctx.getSource(), pretty);
            return 1;
        } catch (Exception e) {
            sendLangFeedback(ctx.getSource(), "component_parse_error", e.getMessage());
            return 0;
        }
    }

    private static int setComponent(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier typeId = IdentifierArgumentType.getIdentifier((CommandContext) ctx, "type");
        String snbtValue = StringArgumentType.getString(ctx, "value");

        ComponentType<?> type = Registries.DATA_COMPONENT_TYPE.get(typeId);
        if (type == null) {
            sendLangFeedback(ctx.getSource(), "component_invalid_type", typeId.toString());
            return 0;
        }

        try {
            // ВАЖНО: readCompound() парсит ТОЛЬКО объекты вида {...}. Большинство
            // значений компонентов — скаляры/списки (unbreakable=1b, rarity="common",
            // max_stack_size=64 и т.д.). В этой ревизии маппингов StringNbtReader —
            // generic-класс с приватным конструктором; правильный вход —
            // StringNbtReader.fromOps(ops).read(string), который парсит любой NbtElement.
            DynamicOps<NbtElement> ops = ctx.getSource().getWorld().getRegistryManager().getOps(NbtOps.INSTANCE);

            NbtElement nbt = new StringNbtReader(new com.mojang.brigadier.StringReader(snbtValue)).parseElement();

            @SuppressWarnings({"unchecked", "rawtypes"})
            ComponentType rawType = type;

            Codec codec = rawType.getCodec();
            if (codec == null) {
                sendLangFeedback(ctx.getSource(), "component_no_codec", typeId.toString());
                return 0;
            }

            Object componentValue = ((DataResult) codec.parse(ops, nbt)).getOrThrow();
            stack.set(rawType, componentValue);

            syncHandItem(player, stack);
            sendLangFeedback(ctx.getSource(), "component_set", typeId.toString());
            return 1;
        } catch (Exception e) {
            sendLangFeedback(ctx.getSource(), "component_parse_error", e.getMessage());
            return 0;
        }
    }

    private static int removeComponent(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier typeId = IdentifierArgumentType.getIdentifier((CommandContext) ctx, "type");
        ComponentType<?> type = Registries.DATA_COMPONENT_TYPE.get(typeId);

        if (type == null) {
            sendLangFeedback(ctx.getSource(), "component_invalid_type", typeId.toString());
            return 0;
        }
        if (!stack.contains(type)) {
            sendLangFeedback(ctx.getSource(), "component_not_found", typeId.toString());
            return 0;
        }

        stack.remove(type);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "component_removed", typeId.toString());
        return 1;
    }

    /**
     * /cie component disable <type> — то же самое действие на движке, что и
     * remove (ItemStack.remove() и так пишет в патч "явно снят", а не просто
     * стирает override — см. ComponentDiffUtil), но отдельная команда и
     * отдельная фраза в чате, чтобы было явно видно: это не "удалить то, что
     * я сам поставил", а "принудительно снять компонент, включая дефолтный
     * ванильный", что при экспорте (/cie export) отражается как
     * "!minecraft:тип_компонента".
     */
    private static int disableComponent(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier typeId = IdentifierArgumentType.getIdentifier((CommandContext) ctx, "type");
        ComponentType<?> type = Registries.DATA_COMPONENT_TYPE.get(typeId);

        if (type == null) {
            sendLangFeedback(ctx.getSource(), "component_invalid_type", typeId.toString());
            return 0;
        }
        if (!stack.contains(type)) {
            sendLangFeedback(ctx.getSource(), "component_not_found", typeId.toString());
            return 0;
        }

        stack.remove(type);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "component_disabled", "!" + typeId);
        return 1;
    }

    /**
     * /cie component copy <type> — кладёт (type, value) со стека в руке в
     * ComponentClipboardUtil. Чтения не мутируют предмет, поэтому
     * UndoUtil.pushSnapshot тут не нужен (в отличие от set/remove/disable).
     */
    private static int copyComponent(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        ClientPlayerEntity player = ctx.getSource().getPlayer();

        Identifier typeId = IdentifierArgumentType.getIdentifier((CommandContext) ctx, "type");
        ComponentType<?> type = Registries.DATA_COMPONENT_TYPE.get(typeId);

        if (type == null || !stack.contains(type)) {
            sendLangFeedback(ctx.getSource(), "component_not_found", typeId.toString());
            return 0;
        }

        Object value = stack.get(type);
        ComponentClipboardUtil.copy(player.getUuid(), type, value);
        sendLangFeedback(ctx.getSource(), "component_copied", typeId.toString());
        return 1;
    }

    /**
     * /cie component paste — переносит компонент из ComponentClipboardUtil
     * на текущий предмет в руке (может быть другой item, чем тот, с
     * которого копировали — компонент просто выставляется как есть).
     */
    private static int pasteComponent(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        ComponentClipboardUtil.Entry entry = ComponentClipboardUtil.get(player.getUuid());
        if (entry == null) {
            sendLangFeedback(ctx.getSource(), "component_clipboard_empty");
            return 0;
        }

        UndoUtil.pushSnapshot(player, stack);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ComponentType rawType = entry.type();
        stack.set(rawType, entry.value());

        syncHandItem(player, stack);
        Identifier typeId = Registries.DATA_COMPONENT_TYPE.getId((ComponentType) entry.type());
        sendLangFeedback(ctx.getSource(), "component_pasted", typeId != null ? typeId.toString() : String.valueOf(entry.type()));
        return 1;
    }

    /**
     * /cie component item get — полный SNBT предмета через ItemStack.CODEC,
     * тот же формат, что и у ванильной /data get entity <player> SelectedItem
     * (id/count/components-патч целиком, а не только "изменённые" в удобном
     * для человека виде, как в /cie export).
     */
    private static int getFullItemInfo(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        try {
            RegistryOps<NbtElement> ops = getRegistries().getOps(NbtOps.INSTANCE);
            @SuppressWarnings("unchecked")
            DataResult<NbtElement> result = ItemStack.CODEC.encodeStart(ops, stack);
            NbtElement nbt = result.getOrThrow();
            StringNbtWriter writer = new StringNbtWriter();
            nbt.accept(writer);
            sendCopyableRaw(ctx.getSource(), writer.toString());
            return 1;
        } catch (Exception e) {
            sendLangFeedback(ctx.getSource(), "component_parse_error", e.getMessage());
            return 0;
        }
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> materialNode(CommandRegistryAccess buildContext) {
        return ClientCommandManager.literal("material")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getMaterial))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("item", RegistryEntryReferenceArgumentType.registryEntry(buildContext, RegistryKeys.ITEM))
                                .executes(CIECommand::setMaterial)));
    }

    private static int getMaterial(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);

        Identifier id = MaterialUtil.getMaterial(stack);
        sendLangFeedback(ctx.getSource(), "material_get", id.toString());
        return 1;
    }

    private static int setMaterial(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        @SuppressWarnings("unchecked")
        RegistryEntry.Reference<Item> itemEntry = RegistryEntryReferenceArgumentType.getRegistryEntry(
                (CommandContext) ctx, "item", RegistryKeys.ITEM
        );
        Item newMaterial = itemEntry.value();

        ItemStack newStack = MaterialUtil.setMaterial(stack, newMaterial);

        syncHandItem(player, newStack);
        sendLangFeedback(ctx.getSource(), "material_set", Registries.ITEM.getId(newMaterial).toString());
        return 1;
    }

    // ================================================================
    //  /rnm food ...
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> foodNode() {
        return ClientCommandManager.literal("food")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getFood))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearFood))
                .then(ClientCommandManager.literal("nutrition")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getFoodNutrition))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(0))
                                        .executes(CIECommand::setFoodNutrition))))
                .then(ClientCommandManager.literal("saturation")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getFoodSaturation))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0.0f))
                                        .executes(CIECommand::setFoodSaturation))))
                .then(ClientCommandManager.literal("canAlwaysEat")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getFoodCanAlwaysEat))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(CIECommand::setFoodCanAlwaysEat))));
    }

    private static int getFoodNutrition(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        FoodComponent comp = FoodComponentUtil.getFood(stack);
        sendLangFeedback(ctx.getSource(), "food_nutrition_status", comp == null ? 0 : comp.nutrition());
        return 1;
    }

    private static int getFoodSaturation(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        FoodComponent comp = FoodComponentUtil.getFood(stack);
        sendLangFeedback(ctx.getSource(), "food_saturation_status", comp == null ? 0f : comp.saturation());
        return 1;
    }

    private static int getFoodCanAlwaysEat(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        FoodComponent comp = FoodComponentUtil.getFood(stack);
        sendLangFeedback(ctx.getSource(), "food_can_always_eat_status", comp != null && comp.canAlwaysEat());
        return 1;
    }

    private static int setFoodNutrition(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int val = IntegerArgumentType.getInteger(ctx, "value");
        FoodComponentUtil.setNutrition(stack, val);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "food_set");
        return 1;
    }

    private static int setFoodSaturation(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        float val = FloatArgumentType.getFloat(ctx, "value");
        FoodComponentUtil.setSaturation(stack, val);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "food_set");
        return 1;
    }

    private static int setFoodCanAlwaysEat(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        boolean val = BoolArgumentType.getBool(ctx, "value");
        FoodComponentUtil.setCanAlwaysEat(stack, val);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "food_set");
        return 1;
    }

    private static int getFood(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        FoodComponent comp = stack.get(DataComponentTypes.FOOD);
        if (comp == null) {
            sendLangFeedback(ctx.getSource(), "food_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "food_status", comp.nutrition(), comp.saturation(), comp.canAlwaysEat());
        return 1;
    }

    private static int clearFood(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.remove(DataComponentTypes.FOOD);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "food_cleared");
        return 1;
    }

    private static int parseHex(CommandContext<FabricClientCommandSource> ctx, String hex) throws CommandSyntaxException {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            throw createException("potion_bad_color", hex);
        }
    }

    private static int getTrim(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        ArmorTrim trim = TrimComponentUtil.getTrim(stack);

        if (trim == null) {
            sendLangFeedback(ctx.getSource(), "trim_empty");
            return 0;
        }

        String patternId = trim.pattern().getKey().map(k -> k.getValue().toString()).orElse("?");
        String materialId = trim.material().getKey().map(k -> k.getValue().toString()).orElse("?");

        sendLangFeedback(ctx.getSource(), "trim_status", patternId, materialId);
        return 1;
    }

    private static int setTrim(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier patternId = ctx.getArgument("pattern", Identifier.class);
        Identifier materialId = ctx.getArgument("material", Identifier.class);

        RegistryEntry<ArmorTrimPattern> pattern = resolveEntry(RegistryKeys.TRIM_PATTERN, patternId.toString());
        RegistryEntry<ArmorTrimMaterial> material = resolveEntry(RegistryKeys.TRIM_MATERIAL, materialId.toString());

        TrimComponentUtil.setTrim(stack, pattern, material);
        syncHandItem(player, stack);

        sendLangFeedback(ctx.getSource(), "trim_set", patternId.toString(), materialId.toString());
        return 1;
    }

    private static int clearTrim(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        TrimComponentUtil.removeTrim(stack);
        syncHandItem(player, stack);

        sendLangFeedback(ctx.getSource(), "trim_cleared");
        return 1;
    }

    // ================================================================
    //  /rnm name ...
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> nameNode() {
        return ClientCommandManager.literal("name")
                .then(ClientCommandManager.literal("get")
                        .then(ClientCommandManager.argument("format", StringArgumentType.word())
                                .suggests(FORMAT_SUGGESTIONS)
                                .executes(ctx -> getName(ctx, StringArgumentType.getString(ctx, "format")))))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                .executes(ctx -> setName(ctx, StringArgumentType.getString(ctx, "value")))))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearName));
    }

    private static int clearName(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.remove(DataComponentTypes.CUSTOM_NAME);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "name_cleared");
        return 1;
    }

    private static int getName(CommandContext<FabricClientCommandSource> ctx, String format) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        RegistryWrapper.WrapperLookup registries = getRegistries();
        Text current = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (current == null) {
            current = stack.getName();
        }
        sendFormatted(ctx.getSource(), current, format, registries);
        return 1;
    }

    private static int setName(CommandContext<FabricClientCommandSource> ctx, String miniMessage) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RegistryWrapper.WrapperLookup registries = getRegistries();
        Text text = MiniMessageBridge.miniMessageToVanilla(miniMessage, registries);
        stack.set(DataComponentTypes.CUSTOM_NAME, text);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "name_updated");
        return 1;
    }

    // ================================================================
    //  /rnm lore ...
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> loreNode() {
        return ClientCommandManager.literal("lore")
                .then(ClientCommandManager.literal("get")
                        .then(ClientCommandManager.argument("format", StringArgumentType.word())
                                .suggests(FORMAT_SUGGESTIONS)
                                .executes(ctx -> getWholeLore(ctx, StringArgumentType.getString(ctx, "format")))))
                .then(ClientCommandManager.literal("line")
                        .then(ClientCommandManager.literal("list").executes(CIECommand::listLore))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearLore))
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> addLoreLine(ctx, StringArgumentType.getString(ctx, "value")))))
                        .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                                .then(ClientCommandManager.literal("get")
                                        .then(ClientCommandManager.argument("format", StringArgumentType.word())
                                                .suggests(FORMAT_SUGGESTIONS)
                                                .executes(ctx -> getLoreLine(ctx, IntegerArgumentType.getInteger(ctx, "index"), StringArgumentType.getString(ctx, "format")))))
                                .then(ClientCommandManager.literal("set")
                                        .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> setLoreLine(ctx, IntegerArgumentType.getInteger(ctx, "index"), StringArgumentType.getString(ctx, "value")))))
                                .then(ClientCommandManager.literal("remove")
                                        .executes(ctx -> removeLoreLine(ctx, IntegerArgumentType.getInteger(ctx, "index"))))
                                .then(ClientCommandManager.literal("delete")
                                        .executes(ctx -> removeLoreLine(ctx, IntegerArgumentType.getInteger(ctx, "index"))))
                                .then(ClientCommandManager.literal("insertafter")
                                        .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> insertLoreLine(ctx, IntegerArgumentType.getInteger(ctx, "index") + 1, StringArgumentType.getString(ctx, "value")))))
                                .then(ClientCommandManager.literal("insertbefore")
                                        .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> insertLoreLine(ctx, IntegerArgumentType.getInteger(ctx, "index"), StringArgumentType.getString(ctx, "value")))))));
    }

    private static LoreComponent currentLore(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        return lore != null ? lore : LoreComponent.DEFAULT;
    }

    private static int getWholeLore(CommandContext<FabricClientCommandSource> ctx, String format) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        RegistryWrapper.WrapperLookup registries = getRegistries();
        List<Text> lines = currentLore(stack).lines();
        if (lines.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "lore_empty");
            return 0;
        }
        for (int i = 0; i < lines.size(); i++) {
            int lineNo = i + 1;
            Text line = lines.get(i);
            ctx.getSource().sendFeedback(Text.literal("[" + lineNo + "] "));
            sendFormatted(ctx.getSource(), line, format, registries);
        }
        return lines.size();
    }

    private static int listLore(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return getWholeLore(ctx, "plain");
    }

    private static int clearLore(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.set(DataComponentTypes.LORE, LoreComponent.DEFAULT);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "lore_cleared");
        return 1;
    }

    private static int addLoreLine(CommandContext<FabricClientCommandSource> ctx, String miniMessage) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RegistryWrapper.WrapperLookup registries = getRegistries();
        List<Text> lines = new ArrayList<>(currentLore(stack).lines());
        lines.add(MiniMessageBridge.miniMessageToVanilla(miniMessage, registries));
        stack.set(DataComponentTypes.LORE, new LoreComponent(lines));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "lore_line_added", lines.size());
        return 1;
    }

    private static int getLoreLine(CommandContext<FabricClientCommandSource> ctx, int index, String format) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        RegistryWrapper.WrapperLookup registries = getRegistries();
        Text line = requireLoreLine(ctx, stack, index);
        sendFormatted(ctx.getSource(), line, format, registries);
        return 1;
    }

    private static int setLoreLine(CommandContext<FabricClientCommandSource> ctx, int index, String miniMessage) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RegistryWrapper.WrapperLookup registries = getRegistries();
        List<Text> lines = new ArrayList<>(currentLore(stack).lines());
        checkIndex(index, lines.size());
        lines.set(index - 1, MiniMessageBridge.miniMessageToVanilla(miniMessage, registries));
        stack.set(DataComponentTypes.LORE, new LoreComponent(lines));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "lore_line_updated", index);
        return 1;
    }

    private static int removeLoreLine(CommandContext<FabricClientCommandSource> ctx, int index) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        List<Text> lines = new ArrayList<>(currentLore(stack).lines());
        checkIndex(index, lines.size());
        lines.remove(index - 1);
        stack.set(DataComponentTypes.LORE, new LoreComponent(lines));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "lore_line_removed", index);
        return 1;
    }

    private static int insertLoreLine(CommandContext<FabricClientCommandSource> ctx, int insertAt, String miniMessage) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RegistryWrapper.WrapperLookup registries = getRegistries();
        List<Text> lines = new ArrayList<>(currentLore(stack).lines());
        int clamped = Math.max(1, Math.min(insertAt, lines.size() + 1));
        lines.add(clamped - 1, MiniMessageBridge.miniMessageToVanilla(miniMessage, registries));
        stack.set(DataComponentTypes.LORE, new LoreComponent(lines));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "lore_line_inserted", clamped);
        return 1;
    }

    private static Text requireLoreLine(CommandContext<FabricClientCommandSource> ctx, ItemStack stack, int index) throws CommandSyntaxException {
        List<Text> lines = currentLore(stack).lines();
        checkIndex(index, lines.size());
        return lines.get(index - 1);
    }

    // ================================================================
    //  /rnm book ...
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> bookNode() {
        return ClientCommandManager.literal("book")
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearBook))
                .then(ClientCommandManager.literal("author")
                        .then(ClientCommandManager.literal("get")
                                .then(ClientCommandManager.argument("format", StringArgumentType.word())
                                        .suggests(FORMAT_SUGGESTIONS)
                                        .executes(ctx -> getBookAuthor(ctx, StringArgumentType.getString(ctx, "format")))))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> setBookAuthor(ctx, StringArgumentType.getString(ctx, "value"))))))
                .then(ClientCommandManager.literal("type")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getBookType))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", StringArgumentType.word())
                                        .suggests(BOOK_TYPE_SUGGESTIONS)
                                        .executes(ctx -> setBookType(ctx, StringArgumentType.getString(ctx, "value"))))))
                .then(ClientCommandManager.literal("title")
                        .then(ClientCommandManager.literal("get")
                                .then(ClientCommandManager.argument("format", StringArgumentType.word())
                                        .suggests(FORMAT_SUGGESTIONS)
                                        .executes(ctx -> getBookTitle(ctx, StringArgumentType.getString(ctx, "format")))))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> setBookTitle(ctx, StringArgumentType.getString(ctx, "value"))))))
                .then(ClientCommandManager.literal("page")
                        .then(ClientCommandManager.literal("get")
                                .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                                        .then(ClientCommandManager.argument("format", StringArgumentType.word())
                                                .suggests(FORMAT_SUGGESTIONS)
                                                .executes(ctx -> getBookPage(ctx, IntegerArgumentType.getInteger(ctx, "index"), StringArgumentType.getString(ctx, "format"))))))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                                        .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> setBookPage(ctx, IntegerArgumentType.getInteger(ctx, "index"), StringArgumentType.getString(ctx, "value"))))))
                        .then(ClientCommandManager.literal("list").executes(CIECommand::listBookPages))
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> addBookPage(ctx, StringArgumentType.getString(ctx, "value")))))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                                        .executes(ctx -> removeBookPage(ctx, IntegerArgumentType.getInteger(ctx, "index"))))));
    }

    private static int clearBook(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        BookComponentUtil.remove(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "book_cleared");
        return 1;
    }

    private static int getBookAuthor(CommandContext<FabricClientCommandSource> ctx, String format) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        WrittenBookContentComponent book = BookComponentUtil.getOrCreate(stack);
        sendFormatted(ctx.getSource(), Text.literal(book.author()), format, getRegistries());
        return 1;
    }

    private static int setBookAuthor(CommandContext<FabricClientCommandSource> ctx, String value) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        WrittenBookContentComponent book = BookComponentUtil.withAuthor(BookComponentUtil.getOrCreate(stack), value);
        BookComponentUtil.save(stack, book);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "book_author_updated");
        return 1;
    }

    private static int getBookType(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        WrittenBookContentComponent book = BookComponentUtil.getOrCreate(stack);
        String name = BOOK_TYPES.get(Math.max(0, Math.min(book.generation(), BOOK_TYPES.size() - 1)));
        ctx.getSource().sendFeedback(Text.literal(name + " (generation=" + book.generation() + ")"));
        return 1;
    }

    private static int setBookType(CommandContext<FabricClientCommandSource> ctx, String value) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int generation = BOOK_TYPES.indexOf(value.toLowerCase(Locale.ROOT));
        if (generation < 0) {
            String available = String.join(", ", BOOK_TYPES);
            sendLangFeedback(ctx.getSource(), "book_type_unknown", available);
            return 0;
        }
        WrittenBookContentComponent book = BookComponentUtil.withGeneration(BookComponentUtil.getOrCreate(stack), generation);
        BookComponentUtil.save(stack, book);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "book_type_updated", value);
        return 1;
    }

    private static int getBookTitle(CommandContext<FabricClientCommandSource> ctx, String format) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        WrittenBookContentComponent book = BookComponentUtil.getOrCreate(stack);
        sendFormatted(ctx.getSource(), Text.literal(BookComponentUtil.rawTitle(book)), format, getRegistries());
        return 1;
    }

    private static int setBookTitle(CommandContext<FabricClientCommandSource> ctx, String miniMessage) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Component component = MiniMessageBridge.parse(miniMessage);
        String legacy = LegacyComponentSerializer.legacySection().serialize(component);
        WrittenBookContentComponent book = BookComponentUtil.withTitle(BookComponentUtil.getOrCreate(stack), legacy);
        BookComponentUtil.save(stack, book);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "book_title_updated");
        return 1;
    }

    private static int listBookPages(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        RegistryWrapper.WrapperLookup registries = getRegistries();
        List<Text> pages = BookComponentUtil.pagesAsText(BookComponentUtil.getOrCreate(stack));
        if (pages.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "book_pages_empty");
            return 0;
        }
        for (int i = 0; i < pages.size(); i++) {
            int pageNo = i + 1;
            String preview = MiniMessageBridge.vanillaToPlain(pages.get(i), registries);
            String shortPreview = preview.length() > 40 ? preview.substring(0, 40) + "..." : preview;
            ctx.getSource().sendFeedback(Text.literal("[" + pageNo + "] " + shortPreview));
        }
        return pages.size();
    }

    private static int addBookPage(CommandContext<FabricClientCommandSource> ctx, String value) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RegistryWrapper.WrapperLookup registries = getRegistries();
        WrittenBookContentComponent book = BookComponentUtil.getOrCreate(stack);
        List<Text> pages = new ArrayList<>(BookComponentUtil.pagesAsText(book));
        pages.add(MiniMessageBridge.miniMessageToVanilla(normalizeNewlines(value), registries));
        BookComponentUtil.save(stack, BookComponentUtil.withPages(book, pages));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "book_page_added", pages.size());
        return 1;
    }

    private static int getBookPage(CommandContext<FabricClientCommandSource> ctx, int index, String format) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        RegistryWrapper.WrapperLookup registries = getRegistries();
        List<Text> pages = BookComponentUtil.pagesAsText(BookComponentUtil.getOrCreate(stack));
        checkIndex(index, pages.size());
        sendFormatted(ctx.getSource(), pages.get(index - 1), format, registries);
        return 1;
    }

    private static int setBookPage(CommandContext<FabricClientCommandSource> ctx, int index, String value) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RegistryWrapper.WrapperLookup registries = getRegistries();
        WrittenBookContentComponent book = BookComponentUtil.getOrCreate(stack);
        List<Text> pages = new ArrayList<>(BookComponentUtil.pagesAsText(book));
        checkIndex(index, pages.size());
        pages.set(index - 1, MiniMessageBridge.miniMessageToVanilla(normalizeNewlines(value), registries));
        BookComponentUtil.save(stack, BookComponentUtil.withPages(book, pages));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "book_page_updated", index);
        return 1;
    }

    private static int removeBookPage(CommandContext<FabricClientCommandSource> ctx, int index) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        WrittenBookContentComponent book = BookComponentUtil.getOrCreate(stack);
        List<Text> pages = new ArrayList<>(BookComponentUtil.pagesAsText(book));
        checkIndex(index, pages.size());
        pages.remove(index - 1);
        BookComponentUtil.save(stack, BookComponentUtil.withPages(book, pages));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "book_page_removed", index);
        return 1;
    }

    private static String normalizeNewlines(String input) {
        return input.replace("<newline>", "\n").replace("\\n", "\n");
    }

    // ================================================================
    //  /rnm enchantments ...
    // ================================================================

    private static final SuggestionProvider<FabricClientCommandSource> ENCHANTMENT_SUGGESTIONS =
            registrySuggestions(RegistryKeys.ENCHANTMENT);
    private static final SuggestionProvider<FabricClientCommandSource> POTION_SUGGESTIONS =
            registrySuggestions(RegistryKeys.POTION);
    private static final SuggestionProvider<FabricClientCommandSource> STATUS_EFFECT_SUGGESTIONS =
            registrySuggestions(RegistryKeys.STATUS_EFFECT);
    private static final SuggestionProvider<FabricClientCommandSource> ENTITY_TYPE_SUGGESTIONS =
            registrySuggestions(RegistryKeys.ENTITY_TYPE);
    private static final SuggestionProvider<FabricClientCommandSource> LANGUAGE_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(CIELang.listLanguages(), builder);
    private static final SuggestionProvider<FabricClientCommandSource> GRADIENT_FORMAT_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(GradientFormatUtil.listAllFormats(), builder);
    private static final SuggestionProvider<FabricClientCommandSource> GRADIENT_CUSTOM_FORMAT_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(GradientFormatUtil.listCustomFormats(), builder);
    /** Для аргументов hexs — подсказывает "$имя" по всем сохранённым пресетам, чтобы ввод начинался с '$'. */
    private static final SuggestionProvider<FabricClientCommandSource> GRADIENT_PRESET_HEXS_SUGGESTIONS =
            (ctx, builder) -> {
                List<String> withDollar = new ArrayList<>();
                for (String name : GradientPresetUtil.list()) {
                    withDollar.add("$" + name);
                }
                return CommandSource.suggestMatching(withDollar, builder);
            };
    /** Для gradient preset remove — голые имена пресетов, без '$'. */
    private static final SuggestionProvider<FabricClientCommandSource> GRADIENT_PRESET_NAME_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(GradientPresetUtil.list(), builder);
    private static final SuggestionProvider<FabricClientCommandSource> ATTRIBUTE_SUGGESTIONS =
            registrySuggestions(RegistryKeys.ATTRIBUTE);

    private static <T> SuggestionProvider<FabricClientCommandSource> registrySuggestions(RegistryKey<Registry<T>> registryKey) {
        return (ctx, builder) -> {
            try {
                RegistryWrapper.WrapperLookup registries = getRegistries();
                return registries.getOptional(registryKey)
                        .map(wrapper -> {
                            List<String> ids = new ArrayList<>();
                            wrapper.streamKeys().forEach(key -> {
                                Identifier id = key.getValue();
                                ids.add(id.toString());
                                if ("minecraft".equals(id.getNamespace())) {
                                    ids.add(id.getPath());
                                }
                            });
                            return CommandSource.suggestMatching(ids, builder);
                        })
                        .orElseGet(builder::buildFuture);
            } catch (Exception e) {
                return builder.buildFuture();
            }
        };
    }

    private static <T> SuggestionProvider<FabricClientCommandSource> tagSuggestions(RegistryKey<Registry<T>> registryKey) {
        return (ctx, builder) -> {
            try {
                RegistryWrapper.WrapperLookup registries = getRegistries();
                return registries.getOptional(registryKey)
                        .map(wrapper -> {
                            List<String> ids = new ArrayList<>();
                            wrapper.streamTagKeys().forEach(tag -> {
                                Identifier id = tag.id();
                                ids.add(id.toString());
                                if ("minecraft".equals(id.getNamespace())) {
                                    ids.add(id.getPath());
                                }
                            });
                            return CommandSource.suggestMatching(ids, builder);
                        })
                        .orElseGet(builder::buildFuture);
            } catch (Exception e) {
                return builder.buildFuture();
            }
        };
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> enchantmentsNode() {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("enchantment");
        attachEnchantOps(root, DataComponentTypes.ENCHANTMENTS);
        root.then(attachEnchantOps(ClientCommandManager.literal("stored"), DataComponentTypes.STORED_ENCHANTMENTS));
        root.then(glintNode());
        return root;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> attachEnchantOps(LiteralArgumentBuilder<FabricClientCommandSource> builder, ComponentType<ItemEnchantmentsComponent> type) {
        return builder
                .then(ClientCommandManager.literal("list").executes(ctx -> listEnchantments(ctx, type)))
                .then(ClientCommandManager.literal("clear").executes(ctx -> clearEnchantments(ctx, type)))
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                .suggests(ENCHANTMENT_SUGGESTIONS)
                                .then(ClientCommandManager.argument("level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> addEnchantment(ctx, type,
                                                ctx.getArgument("id", Identifier.class).toString(),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                .suggests(ENCHANTMENT_SUGGESTIONS)
                                .executes(ctx -> removeEnchantment(ctx, type, ctx.getArgument("id", Identifier.class).toString()))));
    }

    private static int listEnchantments(CommandContext<FabricClientCommandSource> ctx, ComponentType<ItemEnchantmentsComponent> type) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        ItemEnchantmentsComponent component = stack.get(type);
        Map<RegistryEntry<Enchantment>, Integer> map = EnchantmentComponentUtil.toMap(component);
        if (map.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "ench_empty");
            return 0;
        }
        for (Map.Entry<RegistryEntry<Enchantment>, Integer> e : map.entrySet()) {
            String id = e.getKey().getKey().map(k -> k.getValue().toString()).orElse("?");
            sendLangFeedback(ctx.getSource(), "ench_entry", id, e.getValue());
        }
        return map.size();
    }

    private static int addEnchantment(CommandContext<FabricClientCommandSource> ctx, ComponentType<ItemEnchantmentsComponent> type, String idStr, int level) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RegistryEntry<Enchantment> entry = resolveEntry(RegistryKeys.ENCHANTMENT, idStr);
        Map<RegistryEntry<Enchantment>, Integer> map = EnchantmentComponentUtil.toMap(stack.get(type));
        map.put(entry, level);
        stack.set(type, EnchantmentComponentUtil.fromMap(map));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "ench_added", idStr, level);
        return 1;
    }

    private static int removeEnchantment(CommandContext<FabricClientCommandSource> ctx, ComponentType<ItemEnchantmentsComponent> type, String idStr) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RegistryEntry<Enchantment> entry = resolveEntry(RegistryKeys.ENCHANTMENT, idStr);
        Map<RegistryEntry<Enchantment>, Integer> map = EnchantmentComponentUtil.toMap(stack.get(type));
        map.remove(entry);
        stack.set(type, EnchantmentComponentUtil.fromMap(map));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "ench_removed", idStr);
        return 1;
    }

    private static int clearEnchantments(CommandContext<FabricClientCommandSource> ctx, ComponentType<ItemEnchantmentsComponent> type) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.set(type, ItemEnchantmentsComponent.DEFAULT);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "ench_cleared");
        return 1;
    }

    // ================================================================
    //  /rnm enchantments glint ...
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> glintNode() {
        return ClientCommandManager.literal("glint")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getGlint))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setGlint(ctx, BoolArgumentType.getBool(ctx, "value")))))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearGlint));
    }

    private static int getGlint(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        Boolean override = stack.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
        sendLangFeedback(ctx.getSource(), "glint_status", override == null ? "не задан (по умолчанию)" : String.valueOf(override));
        return 1;
    }

    private static int setGlint(CommandContext<FabricClientCommandSource> ctx, boolean value) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, value);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "glint_set", value);
        return 1;
    }

    private static int clearGlint(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.remove(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "glint_cleared");
        return 1;
    }

    // ================================================================
    //  /rnm tooltip ...
    // ================================================================

    private static final SuggestionProvider<FabricClientCommandSource> TOOLTIP_COMPONENT_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(TooltipDisplayUtil.HIDEABLE.keySet(), builder);

    private static LiteralArgumentBuilder<FabricClientCommandSource> tooltipNode() {
        return ClientCommandManager.literal("tooltip")
                .then(ClientCommandManager.literal("list").executes(CIECommand::listTooltip))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearTooltip))
                .then(ClientCommandManager.literal("hide")
                        .then(ClientCommandManager.argument("component", StringArgumentType.word())
                                .suggests(TOOLTIP_COMPONENT_SUGGESTIONS)
                                .executes(ctx -> setTooltipHidden(ctx, StringArgumentType.getString(ctx, "component"), true))))
                .then(ClientCommandManager.literal("show")
                        .then(ClientCommandManager.argument("component", StringArgumentType.word())
                                .suggests(TOOLTIP_COMPONENT_SUGGESTIONS)
                                .executes(ctx -> setTooltipHidden(ctx, StringArgumentType.getString(ctx, "component"), false))))
                .then(ClientCommandManager.literal("hideall")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getTooltipHideAll))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setTooltipHideAll(ctx, BoolArgumentType.getBool(ctx, "value"))))));
    }

    private static int getTooltipHideAll(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        TooltipDisplayUtil.TooltipDisplayState component = TooltipDisplayUtil.getOrCreate(stack);
        sendLangFeedback(ctx.getSource(), "tooltip_hideall_status", component.hideTooltip());
        return 1;
    }

    private static int clearTooltip(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.remove(DataComponentTypes.HIDE_TOOLTIP);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "tooltip_cleared");
        return 1;
    }

    private static int listTooltip(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        TooltipDisplayUtil.TooltipDisplayState component = TooltipDisplayUtil.getOrCreate(stack);
        sendLangFeedback(ctx.getSource(), "tooltip_hideall_status", component.hideTooltip());
        if (component.hiddenComponents().isEmpty()) {
            sendLangFeedback(ctx.getSource(), "tooltip_none_hidden");
        } else {
            for (String key : TooltipDisplayUtil.HIDEABLE.keySet()) {
                if (component.hiddenComponents().contains(TooltipDisplayUtil.HIDEABLE.get(key))) {
                    sendLangFeedback(ctx.getSource(), "tooltip_hidden_entry", key);
                }
            }
        }
        return 1;
    }

    private static int setTooltipHidden(CommandContext<FabricClientCommandSource> ctx, String key, boolean hidden) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        ComponentType<?> target = TooltipDisplayUtil.HIDEABLE.get(key.toLowerCase(Locale.ROOT));
        if (target == null) {
            sendLangFeedback(ctx.getSource(), "tooltip_unknown_component", String.join(", ", TooltipDisplayUtil.HIDEABLE.keySet()));
            return 0;
        }

        TooltipDisplayUtil.TooltipDisplayState updated = TooltipDisplayUtil.withHiddenComponent(TooltipDisplayUtil.getOrCreate(stack), target, hidden);
        TooltipDisplayUtil.save(stack, updated);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), hidden ? "tooltip_hidden" : "tooltip_shown", key);
        return 1;
    }

    private static int setTooltipHideAll(CommandContext<FabricClientCommandSource> ctx, boolean hide) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        TooltipDisplayUtil.TooltipDisplayState updated = TooltipDisplayUtil.withHideWholeTooltip(TooltipDisplayUtil.getOrCreate(stack), hide);
        TooltipDisplayUtil.save(stack, updated);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "tooltip_hideall_status", hide);
        return 1;
    }

    // ================================================================
    //  /rnm potion ...
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> potionNode() {
        return ClientCommandManager.literal("potion")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getPotion))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearPotion))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                .suggests(POTION_SUGGESTIONS)
                                .executes(ctx -> setPotion(ctx, ctx.getArgument("id", Identifier.class).toString()))))
                .then(ClientCommandManager.literal("color")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getPotionColor))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("hex", StringArgumentType.word())
                                        .executes(ctx -> setPotionColor(ctx, StringArgumentType.getString(ctx, "hex")))))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearPotionColor)))
                .then(ClientCommandManager.literal("effect")
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                        .suggests(STATUS_EFFECT_SUGGESTIONS)
                                        .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1))
                                                .then(ClientCommandManager.argument("amplifier", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> addPotionEffect(ctx,
                                                                ctx.getArgument("id", Identifier.class).toString(),
                                                                IntegerArgumentType.getInteger(ctx, "seconds"),
                                                                IntegerArgumentType.getInteger(ctx, "amplifier")))))))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearPotionEffects)));
    }

    private static int clearPotion(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.remove(DataComponentTypes.POTION_CONTENTS);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "potion_cleared");
        return 1;
    }

    private static int getPotion(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        PotionContentsComponent content = PotionContentsUtil.getOrCreate(stack);
        String potionId = content.potion().flatMap(p -> p.getKey()).map(k -> k.getValue().toString()).orElse("(нет)");
        String color = content.customColor().map(String::valueOf).orElse("нет");
        sendLangFeedback(ctx.getSource(), "potion_status", potionId, color, content.customEffects().size());
        return 1;
    }

    private static int getPotionColor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        PotionContentsComponent content = PotionContentsUtil.getOrCreate(stack);
        String color = content.customColor().map(c -> String.format("#%06X", c & 0xFFFFFF)).orElse("не задан");
        sendLangFeedback(ctx.getSource(), "potion_color_status", color);
        return 1;
    }

    private static int setPotion(CommandContext<FabricClientCommandSource> ctx, String idStr) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RegistryEntry<Potion> entry = resolveEntry(RegistryKeys.POTION, idStr);
        PotionContentsComponent updated = PotionContentsUtil.withPotion(PotionContentsUtil.getOrCreate(stack), entry);
        PotionContentsUtil.save(stack, updated);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "potion_set", idStr);
        return 1;
    }

    private static int setPotionColor(CommandContext<FabricClientCommandSource> ctx, String hex) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int color;
        try {
            color = Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            sendLangFeedback(ctx.getSource(), "potion_bad_color", hex);
            return 0;
        }
        PotionContentsComponent updated = PotionContentsUtil.withCustomColor(PotionContentsUtil.getOrCreate(stack), color);
        PotionContentsUtil.save(stack, updated);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "potion_color_set", hex.replace("#", ""));
        return 1;
    }

    private static int clearPotionColor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        PotionContentsComponent updated = PotionContentsUtil.withCustomColor(PotionContentsUtil.getOrCreate(stack), null);
        PotionContentsUtil.save(stack, updated);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "potion_color_cleared");
        return 1;
    }

    private static int addPotionEffect(CommandContext<FabricClientCommandSource> ctx, String idStr, int seconds, int amplifier) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RegistryEntry<StatusEffect> entry = resolveEntry(RegistryKeys.STATUS_EFFECT, idStr);
        StatusEffectInstance instance = new StatusEffectInstance(entry, seconds * 20, amplifier);
        PotionContentsComponent updated = PotionContentsUtil.withExtraEffect(PotionContentsUtil.getOrCreate(stack), instance);
        PotionContentsUtil.save(stack, updated);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "potion_effect_added", idStr, seconds, amplifier + 1);
        return 1;
    }

    private static int clearPotionEffects(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        PotionContentsComponent updated = PotionContentsUtil.withClearedEffects(PotionContentsUtil.getOrCreate(stack));
        PotionContentsUtil.save(stack, updated);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "potion_effects_cleared");
        return 1;
    }

    // ================================================================
    //  /rnm attribute ...
    // ================================================================

    private static final List<String> SLOT_NAMES = List.of("any", "mainhand", "offhand", "feet", "legs", "chest", "head", "body");
    private static final SuggestionProvider<FabricClientCommandSource> SLOT_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(SLOT_NAMES, builder);

    private static final List<String> OPERATION_NAMES = List.of("add_value", "add_multiplied_base", "add_multiplied_total");
    private static final SuggestionProvider<FabricClientCommandSource> OPERATION_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(OPERATION_NAMES, builder);

    private static LiteralArgumentBuilder<FabricClientCommandSource> attributeNode() {
        return ClientCommandManager.literal("attribute")
                .then(ClientCommandManager.literal("list").executes(CIECommand::listAttributes))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearAttributes))
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("attribute", IdentifierArgumentType.identifier())
                                .suggests(ATTRIBUTE_SUGGESTIONS)
                                .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                        .then(ClientCommandManager.argument("amount", DoubleArgumentType.doubleArg())
                                                .then(ClientCommandManager.argument("operation", StringArgumentType.word())
                                                        .suggests(OPERATION_SUGGESTIONS)
                                                        .then(ClientCommandManager.argument("slot", StringArgumentType.word())
                                                                .suggests(SLOT_SUGGESTIONS)
                                                                .executes(CIECommand::addAttributeModifier)))))))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                .executes(ctx -> removeAttributeModifier(ctx, ctx.getArgument("id", Identifier.class)))));
    }

    private static int listAttributes(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        AttributeModifiersComponent comp = AttributeComponentUtil.getOrCreate(stack);

        if (comp.modifiers().isEmpty()) {
            sendLangFeedback(ctx.getSource(), "attribute_empty");
            return 0;
        }

        for (AttributeModifiersComponent.Entry entry : comp.modifiers()) {
            String attrId = entry.attribute().getKey().map(k -> k.getValue().toString()).orElse("?");
            String modId = entry.modifier().id().toString();
            double amount = entry.modifier().value();

            ctx.getSource().sendFeedback(Text.literal(
                    "§7- §f" + attrId + " §7(ID: §e" + modId + "§7, Значение: §a" + amount + "§7, Слот: §b" + entry.slot().asString() + "§7)"
            ));
        }
        return comp.modifiers().size();
    }

    private static int addAttributeModifier(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier attrId = ctx.getArgument("attribute", Identifier.class);
        Identifier modId = ctx.getArgument("id", Identifier.class);
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        String opStr = StringArgumentType.getString(ctx, "operation").toUpperCase(Locale.ROOT);
        String slotStr = StringArgumentType.getString(ctx, "slot").toUpperCase(Locale.ROOT);

        RegistryEntry<EntityAttribute> attribute = resolveEntry(RegistryKeys.ATTRIBUTE, attrId.toString());

        // Добавляем объявление переменной operation:
        EntityAttributeModifier.Operation operation = EntityAttributeModifier.Operation.valueOf(opStr);

        AttributeModifierSlot slot = AttributeModifierSlot.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(slotStr.toLowerCase(Locale.ROOT)))
                .result().orElse(AttributeModifierSlot.ANY);

        AttributeModifiersComponent current = AttributeComponentUtil.getOrCreate(stack);
        AttributeModifiersComponent updated = AttributeComponentUtil.addModifier(current, attribute, modId, amount, operation, slot);

        stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, updated);
        syncHandItem(player, stack);

        sendLangFeedback(ctx.getSource(), "attribute_added", modId.toString());
        return 1;
    }

    private static int removeAttributeModifier(CommandContext<FabricClientCommandSource> ctx, Identifier modId) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        AttributeModifiersComponent current = AttributeComponentUtil.getOrCreate(stack);
        AttributeModifiersComponent updated = AttributeComponentUtil.removeModifier(current, modId);

        stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, updated);
        syncHandItem(player, stack);

        sendLangFeedback(ctx.getSource(), "attribute_removed", modId.toString());
        return 1;
    }

    private static int clearAttributes(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.remove(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        syncHandItem(player, stack);

        sendLangFeedback(ctx.getSource(), "attribute_cleared");
        return 1;
    }

    // ================================================================
    //  Утилиты
    // ================================================================

    private static <T> RegistryEntry<T> resolveEntry(RegistryKey<Registry<T>> registryKey, String idStr) throws CommandSyntaxException {
        Identifier id = Identifier.tryParse(idStr);
        if (id == null) {
            throw createException("bad_id", idStr);
        }
        RegistryWrapper.WrapperLookup registries = getRegistries();
        RegistryWrapper.Impl<T> wrapper = registries.getOptional(registryKey)
                .orElseThrow(() -> new IllegalStateException("Реестр недоступен: " + registryKey));
        return wrapper.getOptional(RegistryKey.of(registryKey, id))
                .orElseThrow(() -> createException("bad_id", idStr));
    }

    private static CommandSyntaxException createException(String key, Object... args) {
        Text text = MiniMessageBridge.miniMessageToVanilla(
                CIELang.getFormatted(key, args), getRegistries());
        playFeedbackSound(SoundSettingsUtil.Category.ERROR);
        return new SimpleCommandExceptionType(text).create();
    }

    private static void sendLangFeedback(FabricClientCommandSource source, String key, Object... args) {
        String formattedMsg = CIELang.getFormatted(key, args);
        Text text = MiniMessageBridge.miniMessageToVanilla(formattedMsg, getRegistries());
        source.sendFeedback(text);
        playFeedbackSound(inferSoundCategory(key));
    }

    /**
     * Эвристика категории звука по имени lang-ключа — единственный способ
     * подключить /cie sound сразу ко всем существующим sendLangFeedback
     * вызовам мода (их 200+), не переписывая каждый вручную:
     *  - оканчивается на "_status" -> GET (просто показ текущего значения);
     *  - содержит not_found/empty/unknown/invalid/corrupt -> WARN;
     *  - всё остальное -> SUCCESS (что-то реально изменилось/выполнилось).
     */
    private static SoundSettingsUtil.Category inferSoundCategory(String key) {
        if (key.endsWith("_status")) {
            return SoundSettingsUtil.Category.GET;
        }
        if (key.contains("not_found") || key.contains("already_empty") || key.contains("empty")
                || key.contains("unknown") || key.contains("invalid") || key.contains("corrupt")
                || key.contains("error")) {
            return SoundSettingsUtil.Category.WARN;
        }
        return SoundSettingsUtil.Category.SUCCESS;
    }

    private static void playFeedbackSound(SoundSettingsUtil.Category category) {
        SoundSettingsUtil.SoundSetting setting = SoundSettingsUtil.get(category);
        if (!setting.enabled()) {
            return;
        }
        Identifier soundId = Identifier.tryParse(setting.soundId());
        if (soundId == null) {
            return;
        }
        SoundEvent event = Registries.SOUND_EVENT.get(soundId);
        if (event == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(event, 1.0f));
    }

    private static ClientPlayerEntity requireCreativePlayer(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null || !player.isCreative()) {
            throw createException("not_creative");
        }
        return player;
    }

    /**
     * Открывает экран ПОСЛЕ того как текущий тик клиента полностью
     * завершится — в частности, после того как ChatScreen.keyPressed()
     * (обрабатывающий Enter, которым была отправлена эта же команда)
     * дойдёт до своей собственной строки close()/setScreen(null).
     *
     * ВАЖНО: client.execute(...) для этого НЕ подходит — очередь
     * ThreadExecutor дренится синхронно ещё внутри того же вызова
     * onKey/keyPressed, так что наш setScreen(...) успевал выполниться
     * ДО того как ChatScreen сам вызывал setScreen(null) при закрытии
     * чата после отправки сообщения — и тот же самый кадр стирал уже
     * открытый нами экран. END_CLIENT_TICK гарантированно срабатывает
     * позже — уже после того как весь input этого тика обработан.
     */
    private static void openScreenNextTick(java.util.function.Supplier<net.minecraft.client.gui.screen.Screen> screenSupplier) {
        MinecraftClient client = MinecraftClient.getInstance();
        System.out.println("CIE-DEBUG: openScreenNextTick called, registering END_CLIENT_TICK");
        boolean[] fired = {false};
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (fired[0]) return;
            fired[0] = true;
            System.out.println("CIE-DEBUG: END_CLIENT_TICK fired, currentScreen before=" + client.currentScreen);
            try {
                net.minecraft.client.gui.screen.Screen s = screenSupplier.get();
                System.out.println("CIE-DEBUG: screen created=" + s);
                client.setScreen(s);
                System.out.println("CIE-DEBUG: currentScreen after=" + client.currentScreen);
            } catch (Throwable t) {
                System.out.println("CIE-DEBUG: EXCEPTION in openScreenNextTick:");
                t.printStackTrace();
            }
        });
    }

    private static ItemStack requireItem(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) throw createException("no_item");
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        return stack;
    }

    private static void checkIndex(int index, int size) throws CommandSyntaxException {
        if (index < 1 || index > size) {
            throw createException("index_out_of_bounds", size);
        }
    }

    private static void syncHandItem(ClientPlayerEntity player, ItemStack stack) {
        int selectedSlot = player.getInventory().selectedSlot;
        int packetSlot = 36 + selectedSlot;

        // КРИТИЧНО: обновляем локальный инвентарь СРАЗУ. Пакет ниже только
        // уведомляет сервер — сам по себе он не меняет то, что видит клиент.
        // Без этой строки предмет визуально не менялся, пока сервер не пришлёт
        // полный ресинк инвентаря (например, при перезаходе).
        player.getInventory().setStack(selectedSlot, stack);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(packetSlot, stack));
        }
    }

    /**
     * Синхронизирует произвольный слот PlayerInventory (не обязательно
     * выбранный) с сервером — используется там, где новый предмет
     * кладётся не в руку, а в первый свободный слот (banner convertToShield,
     * по аналогии с /cie give). Для слотов 0-8 (хотбар) пересчитывает
     * индекс в адресацию PlayerScreenHandler, как это уже делает giveItem.
     */
    private static void syncSlot(ClientPlayerEntity player, int inventorySlot, ItemStack stack) {
        int packetSlot = inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(packetSlot, stack));
        }
    }

    private static RegistryWrapper.WrapperLookup getRegistries() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            return client.world.getRegistryManager();
        }
        throw new IllegalStateException("Мир не загружен.");
    }

    private static void sendFormatted(FabricClientCommandSource source, Text text, String format, RegistryWrapper.WrapperLookup registries) {
        String normalized = format.toLowerCase(Locale.ROOT);

        String rawOutput = switch (normalized) {
            case "json" -> MiniMessageBridge.toJson(text, registries);
            case "mm" -> MiniMessageBridge.vanillaToMiniMessage(text, registries);
            case "plain" -> MiniMessageBridge.vanillaToPlain(text, registries);
            default -> null;
        };

        if (rawOutput == null) {
            sendLangFeedback(source, "unknown_format", format);
            return;
        }

        sendCopyableRaw(source, rawOutput);
    }

    /** Шлёт в чат [Скопировать]-кнопку (click-to-copy + hover) + сырой текст. Текст не парсится как MiniMessage. */
    private static void sendCopyableRaw(FabricClientCommandSource source, String rawOutput) {
        RegistryWrapper.WrapperLookup registries = getRegistries();

        String buttonText = CIELang.getFormatted("copy_button");
        String hoverText = CIELang.getFormatted("copy_hover");

        Component copyButtonComponent = MiniMessageBridge.parse(buttonText)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(rawOutput))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(MiniMessageBridge.parse(hoverText)));

        Text vanillaButton = MiniMessageBridge.toVanillaText(copyButtonComponent, registries);

        Text finalMessage = Text.empty()
                .append(colorizeStructured(rawOutput))
                .append(Text.literal("\n"))
                .append(vanillaButton);

        source.sendFeedback(finalMessage);
    }

    // ================================================================
    //  Подсветка JSON / компонентного синтаксиса (/give ...[...]) в чате:
    //  цвета настраиваются через /cie coloring get/set/reset.
    // ================================================================

    private static final Pattern STRUCTURED_NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?[a-zA-Z]?$");

    private static Text colorizeStructured(String raw) {
        MutableText result = Text.empty();
        int len = raw.length();
        int i = 0;

        while (i < len) {
            char c = raw.charAt(i);

            if (Character.isWhitespace(c)) {
                int start = i;
                while (i < len && Character.isWhitespace(raw.charAt(i))) i++;
                result.append(Text.literal(raw.substring(start, i)));
                continue;
            }

            if (c == '{' || c == '}' || c == '[' || c == ']' || c == ',' || c == ':' || c == '=' || c == ';') {
                result.append(colored(String.valueOf(c), ColoringConfigUtil.get(ColoringConfigUtil.Slot.BRACKET)));
                i++;
                continue;
            }

            if (c == '"' || c == '\'') {
                char quote = c;
                int start = i;
                i++;
                while (i < len) {
                    char cur = raw.charAt(i);
                    if (cur == '\\' && i + 1 < len) {
                        i += 2;
                        continue;
                    }
                    if (cur == quote) {
                        i++;
                        break;
                    }
                    i++;
                }
                int end = Math.min(i, len);
                boolean closed = end > start && end - start >= 2 && raw.charAt(end - 1) == quote;
                String inner = closed ? raw.substring(start + 1, end - 1) : raw.substring(start + 1, end);
                boolean isKey = nextIsSeparator(raw, end);

                int bracketColor = ColoringConfigUtil.get(ColoringConfigUtil.Slot.BRACKET);
                int contentColor = ColoringConfigUtil.get(isKey ? ColoringConfigUtil.Slot.KEY : ColoringConfigUtil.Slot.VALUE);

                result.append(colored(String.valueOf(quote), bracketColor));
                result.append(colored(inner, contentColor));
                if (closed) {
                    result.append(colored(String.valueOf(quote), bracketColor));
                }
                continue;
            }

            int start = i;
            while (i < len) {
                char cur = raw.charAt(i);
                if (cur == '{' || cur == '}' || cur == '[' || cur == ']' || cur == ',' || cur == ':'
                        || cur == '=' || cur == ';' || cur == '"' || cur == '\'' || Character.isWhitespace(cur)) {
                    break;
                }
                i++;
            }
            String token = raw.substring(start, i);
            if (token.isEmpty()) {
                // подстраховка: символ, который мы не распознали ни в одной из веток выше
                result.append(Text.literal(String.valueOf(c)));
                i++;
                continue;
            }
            boolean isKey = nextIsSeparator(raw, i);
            int color;
            if (isKey) {
                color = ColoringConfigUtil.get(ColoringConfigUtil.Slot.KEY);
            } else if (STRUCTURED_NUMBER.matcher(token).matches()) {
                color = ColoringConfigUtil.get(ColoringConfigUtil.Slot.COUNT);
            } else {
                color = ColoringConfigUtil.get(ColoringConfigUtil.Slot.VALUE);
            }
            result.append(colored(token, color));
        }

        return result;
    }

    private static MutableText colored(String text, int rgb) {
        return Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    /** true, если следующий (за пропуском пробелов) символ — ':' или '=', т.е. текущий токен — ключ. */
    private static boolean nextIsSeparator(String raw, int index) {
        int i = index;
        while (i < raw.length() && Character.isWhitespace(raw.charAt(i))) i++;
        if (i >= raw.length()) return false;
        char c = raw.charAt(i);
        return c == ':' || c == '=';
    }

    // ================================================================
    //  /rnm deathprotection ...  (DEATH_PROTECTION, тотем бессмертия)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> deathProtectionNode() {
        return ClientCommandManager.literal("deathprotection")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getDeathProtection))
                .then(ClientCommandManager.literal("set").executes(CIECommand::setDeathProtection))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearDeathProtection))
                .then(ClientCommandManager.literal("effect")
                        .then(ClientCommandManager.literal("list").executes(CIECommand::listDeathEffects))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearDeathEffects))
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                        .suggests(STATUS_EFFECT_SUGGESTIONS)
                                        .then(ClientCommandManager.argument("duration", IntegerArgumentType.integer(1))
                                                .then(ClientCommandManager.argument("amplifier", IntegerArgumentType.integer(0))
                                                        .then(ClientCommandManager.argument("probability", FloatArgumentType.floatArg(0f, 1f))
                                                                .executes(ctx -> addDeathEffect(ctx,
                                                                        StringArgumentType.getString(ctx, "id"),
                                                                        IntegerArgumentType.getInteger(ctx, "duration"),
                                                                        IntegerArgumentType.getInteger(ctx, "amplifier"),
                                                                        FloatArgumentType.getFloat(ctx, "probability"),
                                                                        false, true, true))
                                                                .then(ClientCommandManager.argument("ambient", BoolArgumentType.bool())
                                                                        .then(ClientCommandManager.argument("particles", BoolArgumentType.bool())
                                                                                .then(ClientCommandManager.argument("icon", BoolArgumentType.bool())
                                                                                        .executes(ctx -> addDeathEffect(ctx,
                                                                                                StringArgumentType.getString(ctx, "id"),
                                                                                                IntegerArgumentType.getInteger(ctx, "duration"),
                                                                                                IntegerArgumentType.getInteger(ctx, "amplifier"),
                                                                                                FloatArgumentType.getFloat(ctx, "probability"),
                                                                                                BoolArgumentType.getBool(ctx, "ambient"),
                                                                                                BoolArgumentType.getBool(ctx, "particles"),
                                                                                                BoolArgumentType.getBool(ctx, "icon"))))))))))));
    }

    private static int getDeathProtection(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        boolean present = DeathProtectionUtil.isPresent(stack);
        sendLangFeedback(ctx.getSource(), "deathprotection_status", present);
        return 1;
    }

    private static int setDeathProtection(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        DeathProtectionUtil.save(stack, DeathProtectionUtil.getOrCreate(stack));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "deathprotection_set");
        return 1;
    }

    private static int clearDeathProtection(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        DeathProtectionUtil.remove(stack);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "deathprotection_cleared");
        return 1;
    }

    private static int listDeathEffects(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        var effects = DeathProtectionUtil.effects(DeathProtectionUtil.getOrCreate(stack));
        if (effects.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "deathprotection_effects_empty");
            return 0;
        }
        for (int i = 0; i < effects.size(); i++) {
            sendLangFeedback(ctx.getSource(), "deathprotection_effect_entry", i + 1, String.valueOf(effects.get(i)));
        }
        return effects.size();
    }

    private static int clearDeathEffects(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        DeathProtectionUtil.save(stack, DeathProtectionUtil.withClearedEffects(DeathProtectionUtil.getOrCreate(stack)));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "deathprotection_effect_cleared");
        return 1;
    }

    private static int addDeathEffect(CommandContext<FabricClientCommandSource> ctx, String idStr, int duration, int amplifier,
                                      float probability, boolean ambient, boolean particles, boolean icon) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RegistryEntry<StatusEffect> entry = resolveEntry(RegistryKeys.STATUS_EFFECT, idStr);
        StatusEffectInstance instance = new StatusEffectInstance(entry, duration, amplifier, ambient, particles, icon);

        DeathProtectionComponent updated = DeathProtectionUtil.withExtraEffect(DeathProtectionUtil.getOrCreate(stack), instance, probability);
        DeathProtectionUtil.save(stack, updated);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "deathprotection_effect_added", idStr, duration, amplifier + 1);
        return 1;
    }

    // ================================================================
    //  /rnm export  — сгенерировать /give-команду, воспроизводящую предмет
    // ================================================================

    private static int exportGiveCommand(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        String command = ExportUtil.toGiveCommand(stack);
        sendCopyableRaw(ctx.getSource(), command);
        return 1;
    }

    private static int exportJson(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        String json = ExportUtil.toJson(stack, getRegistries());
        sendCopyableRaw(ctx.getSource(), json);
        return 1;
    }

    // ================================================================
    //  /rnm undo — откат последнего изменения предмета
    // ================================================================

    private static int undo(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack previous = UndoUtil.pop(player);
        if (previous == null) {
            sendLangFeedback(ctx.getSource(), "undo_empty");
            return 0;
        }
        UndoUtil.pushRedoSnapshot(player, player.getMainHandStack());
        syncHandItem(player, previous);
        StatsUtil.incrementUndo();
        sendLangFeedback(ctx.getSource(), "undo_done");
        return 1;
    }

    private static int redo(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack next = UndoUtil.popRedo(player);
        if (next == null) {
            sendLangFeedback(ctx.getSource(), "redo_empty");
            return 0;
        }
        UndoUtil.pushUndoSnapshot(player, player.getMainHandStack());
        syncHandItem(player, next);
        StatsUtil.incrementRedo();
        sendLangFeedback(ctx.getSource(), "redo_done");
        return 1;
    }

    // ================================================================
    //  /cie repeat — повторяет последнюю отправленную команду мода
    //  (не считая сам repeat, см. CommandHistoryUtil)
    // ================================================================

    private static int repeatLast(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            throw createException("no_item");
        }
        String last = CommandHistoryUtil.getLastCommand(player.getUuid());
        if (last == null) {
            sendLangFeedback(ctx.getSource(), "repeat_empty");
            return 0;
        }
        StatsUtil.incrementRepeat();
        return DISPATCHER.execute(last, ctx.getSource());
    }

    // ================================================================
    //  /cie stats — шуточная личная статистика
    // ================================================================

    private static int showStats(CommandContext<FabricClientCommandSource> ctx) {
        StatsUtil.Snapshot snap = StatsUtil.snapshot();
        FabricClientCommandSource source = ctx.getSource();
        sendLangFeedback(source, "stats_header");
        sendLangFeedback(source, "stats_items_edited", snap.itemsEdited);
        sendLangFeedback(source, "stats_undo_used", snap.undoUsed);
        sendLangFeedback(source, "stats_redo_used", snap.redoUsed);
        sendLangFeedback(source, "stats_chaos_used", snap.chaosUsed);
        sendLangFeedback(source, "stats_repeat_used", snap.repeatUsed);
        sendLangFeedback(source, "stats_macros_recorded", snap.macrosRecorded);
        sendLangFeedback(source, "stats_macros_played", snap.macrosPlayed);
        if (snap.topField.isPresent()) {
            sendLangFeedback(source, "stats_top_field", snap.topField.get().getKey(), snap.topField.get().getValue());
        } else {
            sendLangFeedback(source, "stats_top_field_none");
        }
        return 1;
    }

    // ================================================================
    //  /cie import <snbt> — создаёт предмет из полного SNBT стека
    //  (id/count/components), см. ImportUtil
    // ================================================================

    private static int importItem(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        String snbt = StringArgumentType.getString(ctx, "snbt");

        ItemStack stack;
        try {
            stack = ImportUtil.fromSnbt(snbt, getRegistries());
        } catch (Exception e) {
            sendLangFeedback(ctx.getSource(), "import_parse_error", String.valueOf(e.getMessage()));
            return 0;
        }
        if (stack.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "import_empty_result");
            return 0;
        }

        int emptySlot = player.getInventory().getEmptySlot();
        if (emptySlot < 0) {
            sendLangFeedback(ctx.getSource(), "storage_inventory_full");
            return 0;
        }
        int packetSlot = emptySlot < 9 ? 36 + emptySlot : emptySlot;
        player.getInventory().setStack(emptySlot, stack);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(packetSlot, stack));
        }

        sendLangFeedback(ctx.getSource(), "import_success",
                Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount());
        return 1;
    }

    // ================================================================
    //  /cie macro — запись и воспроизведение последовательностей команд
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> macroNode() {
        return ClientCommandManager.literal("macro")
                .then(ClientCommandManager.literal("start").executes(CIECommand::macroStart))
                .then(ClientCommandManager.literal("stop").executes(CIECommand::macroStop))
                .then(ClientCommandManager.literal("records")
                        .then(ClientCommandManager.literal("list").executes(CIECommand::macroRecordsList))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::macroRecordsClear))
                        .then(ClientCommandManager.literal("get")
                                .then(ClientCommandManager.argument("record", StringArgumentType.word())
                                        .suggests(MACRO_RECORD_SUGGESTIONS)
                                        .executes(CIECommand::macroRecordsGet)))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("record", StringArgumentType.word())
                                        .suggests(MACRO_RECORD_SUGGESTIONS)
                                        .executes(CIECommand::macroRecordsRemove)))
                        .then(ClientCommandManager.literal("play")
                                .then(ClientCommandManager.argument("record", StringArgumentType.word())
                                        .suggests(MACRO_RECORD_SUGGESTIONS)
                                        .executes(CIECommand::macroRecordsPlay))));
    }

    private static int macroStart(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        if (CommandHistoryUtil.isRecording(player.getUuid())) {
            sendLangFeedback(ctx.getSource(), "macro_already_recording");
            return 0;
        }
        CommandHistoryUtil.startRecording(player.getUuid());
        sendLangFeedback(ctx.getSource(), "macro_recording_started");
        return 1;
    }

    private static int macroStop(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        List<String> recorded = CommandHistoryUtil.stopRecording(player.getUuid());
        if (recorded == null) {
            sendLangFeedback(ctx.getSource(), "macro_not_recording");
            return 0;
        }
        if (recorded.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "macro_empty_recording");
            return 0;
        }
        String name;
        try {
            name = MacroUtil.saveAutoNamed(recorded);
        } catch (java.io.IOException e) {
            sendLangFeedback(ctx.getSource(), "macro_save_error", String.valueOf(e.getMessage()));
            return 0;
        }
        StatsUtil.incrementMacrosRecorded();
        sendLangFeedback(ctx.getSource(), "macro_saved", name, recorded.size());
        return 1;
    }

    private static int macroRecordsList(CommandContext<FabricClientCommandSource> ctx) {
        List<String> names = MacroUtil.names();
        if (names.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "macro_list_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "macro_list", String.join(", ", names));
        return names.size();
    }

    private static int macroRecordsClear(CommandContext<FabricClientCommandSource> ctx) {
        int removed = MacroUtil.clear();
        sendLangFeedback(ctx.getSource(), "macro_cleared", removed);
        return removed;
    }

    private static int macroRecordsGet(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "record");
        List<String> commands;
        try {
            commands = MacroUtil.load(name);
        } catch (Exception e) {
            sendLangFeedback(ctx.getSource(), "macro_not_found", name);
            return 0;
        }
        FabricClientCommandSource source = ctx.getSource();
        sendLangFeedback(source, "macro_info_header", name, commands.size());
        for (int i = 0; i < commands.size(); i++) {
            sendLangFeedback(source, "macro_info_entry", i + 1, commands.get(i));
        }
        return commands.size();
    }

    private static int macroRecordsRemove(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "record");
        boolean removed = MacroUtil.delete(name);
        if (!removed) {
            sendLangFeedback(ctx.getSource(), "macro_not_found", name);
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "macro_removed", name);
        return 1;
    }

    private static int macroRecordsPlay(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "record");
        List<String> commands;
        try {
            commands = MacroUtil.load(name);
        } catch (Exception e) {
            sendLangFeedback(ctx.getSource(), "macro_not_found", name);
            return 0;
        }
        if (commands.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "macro_empty_recording");
            return 0;
        }
        FabricClientCommandSource source = ctx.getSource();
        int executed = 0;
        for (String cmd : commands) {
            try {
                DISPATCHER.execute(cmd, source);
                executed++;
            } catch (CommandSyntaxException e) {
                // Одна проблемная строка не должна прерывать воспроизведение
                // всего макроса целиком — та же философия, что и в /cie export.
                sendLangFeedback(source, "macro_play_step_failed", cmd, String.valueOf(e.getMessage()));
            }
        }
        StatsUtil.incrementMacrosPlayed();
        sendLangFeedback(source, "macro_play_done", name, executed, commands.size());
        return executed;
    }

    // ================================================================
    //  /cie template — снимок компонентов предмета, накладываемый на
    //  другой предмет через apply. См. TemplateUtil для деталей формата.
    // ================================================================

    private static final SuggestionProvider<FabricClientCommandSource> TEMPLATE_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(TemplateUtil.list(), builder);

    private static LiteralArgumentBuilder<FabricClientCommandSource> templateNode() {
        return ClientCommandManager.literal("template")
                .then(ClientCommandManager.literal("list").executes(CIECommand::listTemplates))
                .then(ClientCommandManager.literal("create")
                        .then(ClientCommandManager.argument("template", StringArgumentType.word())
                                .executes(CIECommand::createTemplate)))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("template", StringArgumentType.word())
                                .suggests(TEMPLATE_SUGGESTIONS)
                                .executes(CIECommand::removeTemplate)))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearTemplates))
                .then(ClientCommandManager.literal("apply")
                        .then(ClientCommandManager.argument("template", StringArgumentType.word())
                                .suggests(TEMPLATE_SUGGESTIONS)
                                .executes(CIECommand::applyTemplate)));
    }

    private static int listTemplates(CommandContext<FabricClientCommandSource> ctx) {
        List<String> names = TemplateUtil.list();
        if (names.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "template_list_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "template_list", String.join(", ", names));
        return names.size();
    }

    /**
     * /cie template create <name> — снимает КОМПОНЕНТЫ (не сам item) с
     * предмета в руке через ComponentDiffUtil.diffFromDefault (та же логика,
     * что и в /cie export — см. класс), сохраняет под именем. Не требует
     * креатив-режима: это read-only операция над предметом.
     */
    private static int createTemplate(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        String name = StringArgumentType.getString(ctx, "template");
        try {
            TemplateUtil.create(name, stack, getRegistries());
        } catch (IllegalArgumentException e) {
            sendLangFeedback(ctx.getSource(), "template_create_error", name, e.getMessage());
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "template_created", name);
        return 1;
    }

    private static int removeTemplate(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "template");
        if (!TemplateUtil.remove(name)) {
            sendLangFeedback(ctx.getSource(), "template_unknown", name);
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "template_removed", name);
        return 1;
    }

    private static int clearTemplates(CommandContext<FabricClientCommandSource> ctx) {
        int removed = TemplateUtil.clear();
        sendLangFeedback(ctx.getSource(), "template_cleared", removed);
        return 1;
    }

    /** /cie template apply <name> — накладывает сохранённый набор компонентов на предмет в руке. */
    private static int applyTemplate(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        String name = StringArgumentType.getString(ctx, "template");
        if (!TemplateUtil.exists(name)) {
            sendLangFeedback(ctx.getSource(), "template_unknown", name);
            return 0;
        }

        UndoUtil.pushSnapshot(player, stack);
        TemplateUtil.ApplyResult result = TemplateUtil.apply(name, stack, getRegistries());
        if (result == null) {
            sendLangFeedback(ctx.getSource(), "template_unknown", name);
            return 0;
        }

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "template_applied", name, result.set(), result.removed());
        return 1;
    }

    // ================================================================
    //  /cie — без аргументов, инфо-экран мода
    // ================================================================

    private static int showInfoScreen(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(ModInfoUtil.buildInfoScreen());
        return 1;
    }

    // ================================================================
    //  /cie diff — сравнивает компоненты main hand и offhand
    // ================================================================

    private static int diffHands(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) throw createException("no_item");

        ItemStack main = player.getMainHandStack();
        ItemStack off = player.getOffHandStack();
        if (main.isEmpty() && off.isEmpty()) {
            throw createException("no_item");
        }

        List<DiffUtil.Entry> entries = DiffUtil.diff(main, off);
        if (entries.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "diff_identical");
            return 1;
        }

        sendLangFeedback(ctx.getSource(), "diff_header", entries.size());
        for (DiffUtil.Entry entry : entries) {
            switch (entry.kind()) {
                case ONLY_LEFT -> sendLangFeedback(ctx.getSource(), "diff_only_main", entry.componentId(), entry.leftValue());
                case ONLY_RIGHT -> sendLangFeedback(ctx.getSource(), "diff_only_offhand", entry.componentId(), entry.rightValue());
                case DIFFERENT -> sendLangFeedback(ctx.getSource(), "diff_different", entry.componentId(), entry.leftValue(), entry.rightValue());
            }
        }
        return entries.size();
    }

    // ================================================================
    //  /cie chaos [<overwrite>] — рандомные компоненты, просто фан
    // ================================================================

    private static int chaosDefault(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return chaos(ctx, false);
    }

    private static int chaosWithOverwrite(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return chaos(ctx, BoolArgumentType.getBool(ctx, "overwrite"));
    }

    private static int chaos(CommandContext<FabricClientCommandSource> ctx, boolean overwrite) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        ChaosUtil.apply(stack, overwrite);

        syncHandItem(player, stack);
        StatsUtil.incrementChaos();
        sendLangFeedback(ctx.getSource(), "chaos_applied");
        return 1;
    }

    // ================================================================
    //  /cie clearinv — полная очистка инвентаря игрока (CLEAR_INVENTORY)
    // ================================================================

    private static int clearInventory(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        int cleared = ClearInventoryUtil.clear(player);
        if (cleared == 0) {
            sendLangFeedback(ctx.getSource(), "clearinv_already_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "clearinv_done", cleared);
        return cleared;
    }

    private static int clearInventoryHotbar(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return clearInventoryPart(ctx, ClearInventoryUtil::clearHotbar, "clearinv_done_hotbar", "clearinv_already_empty_hotbar");
    }

    private static int clearInventoryArmor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return clearInventoryPart(ctx, ClearInventoryUtil::clearArmor, "clearinv_done_armor", "clearinv_already_empty_armor");
    }

    private static int clearInventoryOffhand(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return clearInventoryPart(ctx, ClearInventoryUtil::clearOffhand, "clearinv_done_offhand", "clearinv_already_empty_offhand");
    }

    private static int clearInventoryHand(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return clearInventoryPart(ctx, ClearInventoryUtil::clearHand, "clearinv_done_hand", "clearinv_already_empty_hand");
    }

    private static int clearInventoryMain(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return clearInventoryPart(ctx, ClearInventoryUtil::clearMain, "clearinv_done_inventory", "clearinv_already_empty_inventory");
    }

    private static int clearInventoryPart(CommandContext<FabricClientCommandSource> ctx,
                                          java.util.function.Function<ClientPlayerEntity, Integer> action,
                                          String doneKey, String emptyKey) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        int cleared = action.apply(player);
        if (cleared == 0) {
            sendLangFeedback(ctx.getSource(), emptyKey);
            return 0;
        }
        sendLangFeedback(ctx.getSource(), doneKey, cleared);
        return cleared;
    }

    // ================================================================
    //  /cie math ...  (MATH)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> mathNode() {
        return ClientCommandManager.literal("math")
                .then(ClientCommandManager.literal("expression")
                        .then(ClientCommandManager.argument("expression", StringArgumentType.greedyString())
                                .executes(CIECommand::mathExpression)))
                .then(ClientCommandManager.literal("history")
                        .executes(ctx -> mathHistory(ctx, 10))
                        .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> mathHistory(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                .then(ClientCommandManager.literal("random")
                        .then(ClientCommandManager.argument("range", StringArgumentType.word())
                                .executes(CIECommand::mathRandom)));
    }

    private static int mathExpression(CommandContext<FabricClientCommandSource> ctx) {
        String expression = StringArgumentType.getString(ctx, "expression");
        try {
            double result = MathUtil.evaluate(expression);
            sendLangFeedback(ctx.getSource(), "math_result", expression, formatMathResult(result));
            return 1;
        } catch (IllegalArgumentException e) {
            sendLangFeedback(ctx.getSource(), "math_parse_error", e.getMessage());
            return 0;
        }
    }

    private static int mathHistory(CommandContext<FabricClientCommandSource> ctx, int count) {
        List<String> history = MathUtil.getHistory(count);
        if (history.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "math_history_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "math_history_header", history.size());
        for (String entry : history) {
            ctx.getSource().sendFeedback(MiniMessageBridge.miniMessageToVanilla(
                    "<gray> - <white>" + entry, getRegistries()));
        }
        return history.size();
    }

    private static int mathRandom(CommandContext<FabricClientCommandSource> ctx) {
        String range = StringArgumentType.getString(ctx, "range");
        try {
            String result = MathUtil.randomInRange(range);
            sendLangFeedback(ctx.getSource(), "math_random_result", range, result);
            return 1;
        } catch (IllegalArgumentException e) {
            sendLangFeedback(ctx.getSource(), "math_random_error", e.getMessage());
            return 0;
        }
    }

    private static String formatMathResult(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    // ================================================================
    //  /cie stack ...  (STACK)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> stackNode() {
        return ClientCommandManager.literal("stack")
                .then(ClientCommandManager.argument("stackType", IntegerArgumentType.integer(1))
                        .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(0))
                                .executes(CIECommand::stackCalculate)));
    }

    private static int stackCalculate(CommandContext<FabricClientCommandSource> ctx) {
        int stackType = IntegerArgumentType.getInteger(ctx, "stackType");
        int count = IntegerArgumentType.getInteger(ctx, "count");
        int stacks = count / stackType;
        int remainder = count % stackType;
        sendLangFeedback(ctx.getSource(), "stack_result", count, stackType, stacks, remainder);
        return stacks;
    }

    // ================================================================
    //  /cie mouseHistory ...  (MOUSE_HISTORY)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> mouseHistoryNode() {
        return ClientCommandManager.literal("mouseHistory")
                .executes(CIECommand::openMouseHistory)
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearMouseHistory))
                .then(ClientCommandManager.literal("getLast").executes(CIECommand::getLastMouseHistory));
    }

    private static int openMouseHistory(CommandContext<FabricClientCommandSource> ctx) {
        // ВАЖНО: ChatScreen.keyPressed(Enter) сначала выполняет команду,
        // а ПОСЛЕ этого сам закрывает себя через setScreen(null),
        // безусловно затирая любой экран, который команда успела открыть
        // синхронно — client.execute(...) не спасает, потому что очередь
        // дренится ещё внутри того же keyPressed(). openScreenNextTick
        // откладывает открытие на END_CLIENT_TICK, который срабатывает
        // уже после того, как ChatScreen закроет сам себя.
        MinecraftClient client = MinecraftClient.getInstance();
        openScreenNextTick(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) {
                return null;
            }
            com.cie.screen.MouseHistoryScreenHandler handler =
                    new com.cie.screen.MouseHistoryScreenHandler(player.getInventory(), MouseHistoryUtil.getAll());
            return new com.cie.screen.MouseHistoryScreen(handler, player.getInventory());
        });
        return 1;
    }

    private static int clearMouseHistory(CommandContext<FabricClientCommandSource> ctx) {
        MouseHistoryUtil.clear();
        sendLangFeedback(ctx.getSource(), "mouse_history_cleared");
        return 1;
    }

    private static int getLastMouseHistory(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack last = MouseHistoryUtil.getLast();
        if (last.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "mouse_history_empty");
            return 0;
        }

        int emptySlot = player.getInventory().getEmptySlot();
        if (emptySlot < 0) {
            sendLangFeedback(ctx.getSource(), "storage_inventory_full");
            return 0;
        }
        player.getInventory().setStack(emptySlot, last);
        syncSlot(player, emptySlot, last);

        sendLangFeedback(ctx.getSource(), "mouse_history_last_given", last.getName().getString());
        return 1;
    }

    // ================================================================
    //  /rnm firework ...  — конструктор фейерверков (FIREWORKS)
    // ================================================================

    private static final List<String> FIREWORK_SHAPES = List.of("small_ball", "large_ball", "star", "creeper", "burst");
    private static final SuggestionProvider<FabricClientCommandSource> FIREWORK_SHAPE_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(FIREWORK_SHAPES, builder);

    private static LiteralArgumentBuilder<FabricClientCommandSource> fireworkNode() {
        return ClientCommandManager.literal("firework")
                .then(ClientCommandManager.literal("flight")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getFireworkFlight))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("ticks", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setFireworkFlight(ctx, IntegerArgumentType.getInteger(ctx, "ticks")))))
                        .then(ClientCommandManager.literal("reset")
                                .executes(ctx -> setFireworkFlight(ctx, 1))))
                .then(ClientCommandManager.literal("explosion")
                        .then(ClientCommandManager.literal("list").executes(CIECommand::listFireworkExplosions))
                        .then(ClientCommandManager.literal("get")
                                .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                        .executes(ctx -> getFireworkExplosion(ctx, IntegerArgumentType.getInteger(ctx, "id")))))
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("shape", StringArgumentType.word())
                                        .suggests(FIREWORK_SHAPE_SUGGESTIONS)
                                        .executes(ctx -> addFireworkExplosion(ctx,
                                                StringArgumentType.getString(ctx, "shape"), false, false))
                                        .then(ClientCommandManager.argument("trail", BoolArgumentType.bool())
                                                .executes(ctx -> addFireworkExplosion(ctx,
                                                        StringArgumentType.getString(ctx, "shape"),
                                                        BoolArgumentType.getBool(ctx, "trail"), false))
                                                .then(ClientCommandManager.argument("twinkle", BoolArgumentType.bool())
                                                        .executes(ctx -> addFireworkExplosion(ctx,
                                                                StringArgumentType.getString(ctx, "shape"),
                                                                BoolArgumentType.getBool(ctx, "trail"),
                                                                BoolArgumentType.getBool(ctx, "twinkle")))))))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                        .executes(ctx -> removeFireworkExplosion(ctx, IntegerArgumentType.getInteger(ctx, "id")))))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearFireworkExplosions))

                        .then(ClientCommandManager.literal("shape")
                                .then(ClientCommandManager.literal("get")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> getFireworkShape(ctx, IntegerArgumentType.getInteger(ctx, "id")))))
                                .then(ClientCommandManager.literal("set")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .then(ClientCommandManager.argument("shape", StringArgumentType.word())
                                                        .suggests(FIREWORK_SHAPE_SUGGESTIONS)
                                                        .executes(ctx -> setFireworkShape(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "shape"))))))
                                .then(ClientCommandManager.literal("reset")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> resetFireworkShape(ctx, IntegerArgumentType.getInteger(ctx, "id"))))))

                        .then(ClientCommandManager.literal("trail")
                                .then(ClientCommandManager.literal("get")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> getFireworkTrail(ctx, IntegerArgumentType.getInteger(ctx, "id")))))
                                .then(ClientCommandManager.literal("set")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(ctx -> setFireworkTrail(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                                BoolArgumentType.getBool(ctx, "value"))))))
                                .then(ClientCommandManager.literal("reset")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> resetFireworkTrail(ctx, IntegerArgumentType.getInteger(ctx, "id"))))))

                        .then(ClientCommandManager.literal("twinkle")
                                .then(ClientCommandManager.literal("get")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> getFireworkTwinkle(ctx, IntegerArgumentType.getInteger(ctx, "id")))))
                                .then(ClientCommandManager.literal("set")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(ctx -> setFireworkTwinkle(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                                BoolArgumentType.getBool(ctx, "value"))))))
                                .then(ClientCommandManager.literal("reset")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> resetFireworkTwinkle(ctx, IntegerArgumentType.getInteger(ctx, "id"))))))

                        .then(ClientCommandManager.literal("color")
                                .then(ClientCommandManager.literal("list")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> listFireworkColors(ctx, IntegerArgumentType.getInteger(ctx, "id"), false))))
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .then(ClientCommandManager.argument("hex", StringArgumentType.word())
                                                        .executes(ctx -> addFireworkColor(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "hex"), false)))))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .then(ClientCommandManager.argument("colorIndex", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> removeFireworkColor(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                                IntegerArgumentType.getInteger(ctx, "colorIndex"), false)))))
                                .then(ClientCommandManager.literal("reset")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> clearFireworkColors(ctx, IntegerArgumentType.getInteger(ctx, "id"), false)))))

                        .then(ClientCommandManager.literal("fadeColor")
                                .then(ClientCommandManager.literal("list")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> listFireworkColors(ctx, IntegerArgumentType.getInteger(ctx, "id"), true))))
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .then(ClientCommandManager.argument("hex", StringArgumentType.word())
                                                        .executes(ctx -> addFireworkColor(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "hex"), true)))))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .then(ClientCommandManager.argument("colorIndex", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> removeFireworkColor(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                                IntegerArgumentType.getInteger(ctx, "colorIndex"), true)))))
                                .then(ClientCommandManager.literal("reset")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> clearFireworkColors(ctx, IntegerArgumentType.getInteger(ctx, "id"), true))))));
    }

    private static int getFireworkFlight(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "firework_flight_status", FireworksUtil.getOrCreate(stack).flightDuration());
        return 1;
    }

    private static int setFireworkFlight(CommandContext<FabricClientCommandSource> ctx, int ticks) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        FireworksUtil.save(stack, FireworksUtil.withFlightDuration(FireworksUtil.getOrCreate(stack), ticks));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "firework_flight_set", ticks);
        return 1;
    }

    private static int listFireworkExplosions(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        List<FireworkExplosionComponent> explosions = FireworksUtil.getOrCreate(stack).explosions();
        if (explosions.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "firework_explosions_empty");
            return 0;
        }
        for (int i = 0; i < explosions.size(); i++) {
            FireworkExplosionComponent e = explosions.get(i);
            sendLangFeedback(ctx.getSource(), "firework_explosion_entry",
                    i + 1, e.shape().name(), e.colors().size(), e.fadeColors().size(), e.hasTrail(), e.hasTwinkle());
        }
        return explosions.size();
    }

    private static int getFireworkExplosion(CommandContext<FabricClientCommandSource> ctx, int index) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        List<FireworkExplosionComponent> explosions = FireworksUtil.getOrCreate(stack).explosions();
        checkIndex(index, explosions.size());

        FireworkExplosionComponent e = explosions.get(index - 1);
        sendLangFeedback(ctx.getSource(), "firework_explosion_entry",
                index, e.shape().name(), e.colors().size(), e.fadeColors().size(), e.hasTrail(), e.hasTwinkle());
        return 1;
    }

    private static int addFireworkExplosion(CommandContext<FabricClientCommandSource> ctx, String shapeStr, boolean trail, boolean twinkle) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        FireworkExplosionComponent.Type shape;
        try {
            shape = FireworkExplosionComponent.Type.valueOf(shapeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sendLangFeedback(ctx.getSource(), "firework_bad_shape", shapeStr);
            return 0;
        }
        UndoUtil.pushSnapshot(player, stack);

        FireworkExplosionComponent explosion = new FireworkExplosionComponent(shape, new IntArrayList(), new IntArrayList(), trail, twinkle);
        FireworksUtil.save(stack, FireworksUtil.withExtraExplosion(FireworksUtil.getOrCreate(stack), explosion));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "firework_explosion_added", shapeStr);
        return 1;
    }

    private static int removeFireworkExplosion(CommandContext<FabricClientCommandSource> ctx, int index) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        FireworksComponent current = FireworksUtil.getOrCreate(stack);
        checkIndex(index, current.explosions().size());
        UndoUtil.pushSnapshot(player, stack);

        FireworksUtil.save(stack, FireworksUtil.withoutExplosion(current, index - 1));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "firework_explosion_removed", index);
        return 1;
    }

    private static int clearFireworkExplosions(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        FireworksUtil.save(stack, FireworksUtil.withClearedExplosions(FireworksUtil.getOrCreate(stack)));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "firework_explosions_cleared");
        return 1;
    }

    private static int getFireworkShape(CommandContext<FabricClientCommandSource> ctx, int index) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        List<FireworkExplosionComponent> explosions = FireworksUtil.getOrCreate(stack).explosions();
        checkIndex(index, explosions.size());
        sendLangFeedback(ctx.getSource(), "firework_shape_status", index, explosions.get(index - 1).shape().name());
        return 1;
    }

    private static int setFireworkShape(CommandContext<FabricClientCommandSource> ctx, int index, String shapeStr) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        FireworksComponent current = FireworksUtil.getOrCreate(stack);
        checkIndex(index, current.explosions().size());

        FireworkExplosionComponent.Type shape;
        try {
            shape = FireworkExplosionComponent.Type.valueOf(shapeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sendLangFeedback(ctx.getSource(), "firework_bad_shape", shapeStr);
            return 0;
        }
        UndoUtil.pushSnapshot(player, stack);

        FireworkExplosionComponent explosion = FireworksUtil.withShape(current.explosions().get(index - 1), shape);
        FireworksUtil.save(stack, FireworksUtil.withReplacedExplosion(current, index - 1, explosion));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "firework_shape_set", index, shapeStr);
        return 1;
    }

    private static int resetFireworkShape(CommandContext<FabricClientCommandSource> ctx, int index) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        FireworksComponent current = FireworksUtil.getOrCreate(stack);
        checkIndex(index, current.explosions().size());
        UndoUtil.pushSnapshot(player, stack);

        FireworkExplosionComponent explosion = FireworksUtil.withShape(current.explosions().get(index - 1), FireworkExplosionComponent.Type.SMALL_BALL);
        FireworksUtil.save(stack, FireworksUtil.withReplacedExplosion(current, index - 1, explosion));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "firework_shape_reset", index);
        return 1;
    }

    private static int getFireworkTrail(CommandContext<FabricClientCommandSource> ctx, int index) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        List<FireworkExplosionComponent> explosions = FireworksUtil.getOrCreate(stack).explosions();
        checkIndex(index, explosions.size());
        sendLangFeedback(ctx.getSource(), "firework_trail_status", index, explosions.get(index - 1).hasTrail());
        return 1;
    }

    private static int setFireworkTrail(CommandContext<FabricClientCommandSource> ctx, int index, boolean value) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        FireworksComponent current = FireworksUtil.getOrCreate(stack);
        checkIndex(index, current.explosions().size());
        UndoUtil.pushSnapshot(player, stack);

        FireworkExplosionComponent explosion = FireworksUtil.withTrail(current.explosions().get(index - 1), value);
        FireworksUtil.save(stack, FireworksUtil.withReplacedExplosion(current, index - 1, explosion));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "firework_trail_set", index, value);
        return 1;
    }

    private static int resetFireworkTrail(CommandContext<FabricClientCommandSource> ctx, int index) throws CommandSyntaxException {
        int result = setFireworkTrail(ctx, index, false);
        if (result > 0) {
            sendLangFeedback(ctx.getSource(), "firework_trail_reset", index);
        }
        return result;
    }

    private static int getFireworkTwinkle(CommandContext<FabricClientCommandSource> ctx, int index) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        List<FireworkExplosionComponent> explosions = FireworksUtil.getOrCreate(stack).explosions();
        checkIndex(index, explosions.size());
        sendLangFeedback(ctx.getSource(), "firework_twinkle_status", index, explosions.get(index - 1).hasTwinkle());
        return 1;
    }

    private static int setFireworkTwinkle(CommandContext<FabricClientCommandSource> ctx, int index, boolean value) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        FireworksComponent current = FireworksUtil.getOrCreate(stack);
        checkIndex(index, current.explosions().size());
        UndoUtil.pushSnapshot(player, stack);

        FireworkExplosionComponent explosion = FireworksUtil.withTwinkle(current.explosions().get(index - 1), value);
        FireworksUtil.save(stack, FireworksUtil.withReplacedExplosion(current, index - 1, explosion));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "firework_twinkle_set", index, value);
        return 1;
    }

    private static int resetFireworkTwinkle(CommandContext<FabricClientCommandSource> ctx, int index) throws CommandSyntaxException {
        int result = setFireworkTwinkle(ctx, index, false);
        if (result > 0) {
            sendLangFeedback(ctx.getSource(), "firework_twinkle_reset", index);
        }
        return result;
    }

    private static int addFireworkColor(CommandContext<FabricClientCommandSource> ctx, int index, String hex, boolean fade) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        FireworksComponent current = FireworksUtil.getOrCreate(stack);
        checkIndex(index, current.explosions().size());

        int color;
        try {
            color = Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            sendLangFeedback(ctx.getSource(), "potion_bad_color", hex);
            return 0;
        }
        UndoUtil.pushSnapshot(player, stack);

        FireworkExplosionComponent explosion = current.explosions().get(index - 1);
        explosion = fade ? FireworksUtil.withFadeColor(explosion, color) : FireworksUtil.withColor(explosion, color);
        FireworksUtil.save(stack, FireworksUtil.withReplacedExplosion(current, index - 1, explosion));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), fade ? "firework_fadecolor_added" : "firework_color_added", index, hex.replace("#", ""));
        return 1;
    }

    private static int removeFireworkColor(CommandContext<FabricClientCommandSource> ctx, int index, int colorIndex, boolean fade) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        FireworksComponent current = FireworksUtil.getOrCreate(stack);
        checkIndex(index, current.explosions().size());

        FireworkExplosionComponent explosion = current.explosions().get(index - 1);
        IntList colors = fade ? explosion.fadeColors() : explosion.colors();
        checkIndex(colorIndex, colors.size());
        UndoUtil.pushSnapshot(player, stack);

        FireworkExplosionComponent updated = FireworksUtil.withoutColor(explosion, colorIndex - 1, fade);
        FireworksUtil.save(stack, FireworksUtil.withReplacedExplosion(current, index - 1, updated));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), fade ? "firework_fadecolor_removed" : "firework_color_removed", index, colorIndex);
        return 1;
    }

    private static int listFireworkColors(CommandContext<FabricClientCommandSource> ctx, int index, boolean fade) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        FireworksComponent current = FireworksUtil.getOrCreate(stack);
        checkIndex(index, current.explosions().size());

        FireworkExplosionComponent explosion = current.explosions().get(index - 1);
        IntList colors = fade ? explosion.fadeColors() : explosion.colors();
        if (colors.isEmpty()) {
            sendLangFeedback(ctx.getSource(), fade ? "firework_fadecolor_list_empty" : "firework_color_list_empty", index);
            return 0;
        }
        for (int i = 0; i < colors.size(); i++) {
            String hex = String.format("%06X", colors.getInt(i));
            sendLangFeedback(ctx.getSource(), fade ? "firework_fadecolor_list_entry" : "firework_color_list_entry", i + 1, hex);
        }
        return colors.size();
    }

    private static int clearFireworkColors(CommandContext<FabricClientCommandSource> ctx, int index, boolean fade) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        FireworksComponent current = FireworksUtil.getOrCreate(stack);
        checkIndex(index, current.explosions().size());
        UndoUtil.pushSnapshot(player, stack);

        FireworkExplosionComponent explosion = FireworksUtil.withClearedColors(current.explosions().get(index - 1), fade);
        FireworksUtil.save(stack, FireworksUtil.withReplacedExplosion(current, index - 1, explosion));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), fade ? "firework_fadecolor_cleared" : "firework_color_cleared", index);
        return 1;
    }

    // ================================================================
    //  /rnm storage ...  — постоянное хранилище предметов на диске
    //  (.minecraft/cie/storage/<name>.json)
    // ================================================================

    private static final SuggestionProvider<FabricClientCommandSource> STORAGE_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(StorageUtil.names(), builder);

    // ================================================================
    //  /cie give <id> <count> <components>
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> giveNode() {
        return ClientCommandManager.literal("give")
                .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                        .suggests(ITEM_SUGGESTIONS)
                        .executes(ctx -> giveItem(ctx, 1, null))
                        .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> giveItem(ctx, IntegerArgumentType.getInteger(ctx, "count"), null))
                                .then(ClientCommandManager.argument("components", StringArgumentType.greedyString())
                                        .executes(ctx -> giveItem(ctx, IntegerArgumentType.getInteger(ctx, "count"),
                                                StringArgumentType.getString(ctx, "components"))))));
    }

    private static int giveItem(CommandContext<FabricClientCommandSource> ctx, int count, String componentsSnbt) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);

        Identifier itemId = ctx.getArgument("id", Identifier.class);
        Item item = Registries.ITEM.get(itemId);

        ItemStack stack;
        try {
            stack = UseRemainderUtil.buildRemainderStack(item, count, componentsSnbt, getRegistries());
        } catch (Exception e) {
            sendLangFeedback(ctx.getSource(), "component_parse_error", e.getMessage());
            return 0;
        }

        int emptySlot = player.getInventory().getEmptySlot();
        if (emptySlot < 0) {
            sendLangFeedback(ctx.getSource(), "storage_inventory_full");
            return 0;
        }
        int packetSlot = emptySlot < 9 ? 36 + emptySlot : emptySlot;

        player.getInventory().setStack(emptySlot, stack);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(packetSlot, stack));
        }

        sendLangFeedback(ctx.getSource(), "give_success", itemId.toString(), count);
        return 1;
    }

    // ================================================================
    //  /cie edit ...
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> editNode(CommandRegistryAccess buildContext) {
        return ClientCommandManager.literal("edit")
                .then(nameNode())
                .then(loreNode())
                .then(bookNode())
                .then(enchantmentsNode())
                .then(tooltipNode())
                .then(potionNode())
                .then(attributeNode())
                .then(trimNode())
                .then(colorNode())
                .then(countNode())
                .then(durabilityNode())
                .then(equipableNode())
                .then(playerHeadNode())
                .then(foodNode())
                .then(componentNode())
                .then(materialNode(buildContext))
                .then(consumableNode(buildContext))
                .then(deathProtectionNode())
                .then(fireworkNode())
                .then(itemModelNode())
                .then(bannerNode())
                .then(gliderNode())
                .then(useCooldownNode())
                .then(useRemainderNode())
                .then(repairableNode())
                .then(repairCostNode())
                .then(swingAnimationNode())
                .then(rarityNode())
                .then(jukeboxPlayableNode())
                .then(damageResistantNode())
                .then(customModelDataNode())
                .then(suspiciousStewEffectsNode())
                .then(signNode())
                .then(entitySettingsNode())
                .then(villagerDataNode())
                .then(armorStandNode())
                .then(spawnerNode())
                .then(enchantableNode())
                .then(containerNode())
                .then(bundleNode());
    }

    // ================================================================
    //  /cie reloadConfig — перечитывает strings.json и coloring.json,
    //  не трогая /cie storage (для него отдельный /cie storage reload).
    // ================================================================

    private static int reloadConfig(CommandContext<FabricClientCommandSource> ctx) {
        CIELang.load();
        ColoringConfigUtil.reload();
        sendLangFeedback(ctx.getSource(), "config_reloaded");
        return 1;
    }

    // ================================================================
    //  /cie coloring get/set/reset key|value|count|bracket <hex_or_&color>
    // ================================================================

    private static final List<String> COLORING_SLOTS = List.of("key", "value", "count", "bracket");

    private static final SuggestionProvider<FabricClientCommandSource> COLORING_SLOT_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(COLORING_SLOTS, builder);

    private static LiteralArgumentBuilder<FabricClientCommandSource> coloringNode() {
        return ClientCommandManager.literal("coloring")
                .then(ClientCommandManager.literal("get")
                        .then(ClientCommandManager.argument("slot", StringArgumentType.word())
                                .suggests(COLORING_SLOT_SUGGESTIONS)
                                .executes(ctx -> getColoring(ctx, StringArgumentType.getString(ctx, "slot")))))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("slot", StringArgumentType.word())
                                .suggests(COLORING_SLOT_SUGGESTIONS)
                                .then(ClientCommandManager.argument("color", StringArgumentType.word())
                                        .executes(ctx -> setColoring(ctx,
                                                StringArgumentType.getString(ctx, "slot"),
                                                StringArgumentType.getString(ctx, "color"))))))
                .then(ClientCommandManager.literal("reset")
                        .then(ClientCommandManager.argument("slot", StringArgumentType.word())
                                .suggests(COLORING_SLOT_SUGGESTIONS)
                                .executes(ctx -> resetColoring(ctx, StringArgumentType.getString(ctx, "slot")))));
    }

    private static ColoringConfigUtil.Slot parseColoringSlot(CommandContext<FabricClientCommandSource> ctx, String slotStr) throws CommandSyntaxException {
        return switch (slotStr.toLowerCase(Locale.ROOT)) {
            case "key" -> ColoringConfigUtil.Slot.KEY;
            case "value" -> ColoringConfigUtil.Slot.VALUE;
            case "count" -> ColoringConfigUtil.Slot.COUNT;
            case "bracket" -> ColoringConfigUtil.Slot.BRACKET;
            default -> throw createException("coloring_bad_slot", slotStr);
        };
    }

    /** Принимает как #RRGGBB / RRGGBB, так и легаси-код &0..&f,&a..&f (регистронезависимо). */
    private static int parseColorInput(CommandContext<FabricClientCommandSource> ctx, String input) throws CommandSyntaxException {
        String trimmed = input.trim();
        if (trimmed.length() == 2 && trimmed.charAt(0) == '&') {
            Formatting formatting = Formatting.byCode(Character.toLowerCase(trimmed.charAt(1)));
            Integer rgb = formatting == null ? null : formatting.getColorValue();
            if (rgb == null) {
                throw createException("coloring_bad_color", input);
            }
            return rgb;
        }
        try {
            return Integer.parseInt(trimmed.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            throw createException("coloring_bad_color", input);
        }
    }

    private static int getColoring(CommandContext<FabricClientCommandSource> ctx, String slotStr) throws CommandSyntaxException {
        ColoringConfigUtil.Slot slot = parseColoringSlot(ctx, slotStr);
        String hex = String.format("%06X", ColoringConfigUtil.get(slot));
        sendLangFeedback(ctx.getSource(), "coloring_status", slotStr.toLowerCase(Locale.ROOT), hex);
        return 1;
    }

    private static int setColoring(CommandContext<FabricClientCommandSource> ctx, String slotStr, String colorStr) throws CommandSyntaxException {
        ColoringConfigUtil.Slot slot = parseColoringSlot(ctx, slotStr);
        int rgb = parseColorInput(ctx, colorStr);
        ColoringConfigUtil.set(slot, rgb);

        String hex = String.format("%06X", rgb);
        sendLangFeedback(ctx.getSource(), "coloring_set", slotStr.toLowerCase(Locale.ROOT), hex);
        return 1;
    }

    private static int resetColoring(CommandContext<FabricClientCommandSource> ctx, String slotStr) throws CommandSyntaxException {
        ColoringConfigUtil.Slot slot = parseColoringSlot(ctx, slotStr);
        ColoringConfigUtil.reset(slot);

        String hex = String.format("%06X", ColoringConfigUtil.getDefault(slot));
        sendLangFeedback(ctx.getSource(), "coloring_reset", slotStr.toLowerCase(Locale.ROOT), hex);
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> storageNode() {
        return ClientCommandManager.literal("storage")
                .then(ClientCommandManager.literal("save")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> storageSave(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(ClientCommandManager.literal("give")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .suggests(STORAGE_SUGGESTIONS)
                                .executes(ctx -> storageGive(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(ClientCommandManager.literal("info")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .suggests(STORAGE_SUGGESTIONS)
                                .executes(ctx -> storageInfo(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(ClientCommandManager.literal("list").executes(CIECommand::storageList))
                .then(ClientCommandManager.literal("delete")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .suggests(STORAGE_SUGGESTIONS)
                                .executes(ctx -> storageDelete(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(ClientCommandManager.literal("clear")
                        .executes(CIECommand::storageClearRequest)
                        .then(ClientCommandManager.literal("confirm").executes(CIECommand::storageClearConfirm)))
                .then(ClientCommandManager.literal("reload").executes(CIECommand::storageReload));
    }

    private static int storageReload(CommandContext<FabricClientCommandSource> ctx) {
        StorageUtil.ReloadResult result = StorageUtil.reload();
        sendLangFeedback(ctx.getSource(), "storage_reloaded", result.validCount);
        if (!result.quarantined.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "storage_boot_corrupt_header", result.quarantined.size());
            for (String name : result.quarantined) {
                sendLangFeedback(ctx.getSource(), "storage_boot_corrupt_entry", name);
            }
        }
        return 1;
    }

    private static int storageSave(CommandContext<FabricClientCommandSource> ctx, String name) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        RegistryWrapper.WrapperLookup registries = getRegistries();
        try {
            StorageUtil.save(name, stack, registries);
        } catch (StorageUtil.InvalidNameException e) {
            sendLangFeedback(ctx.getSource(), "storage_invalid_name", name);
            return 0;
        } catch (java.io.IOException e) {
            sendLangFeedback(ctx.getSource(), "storage_io_error", e.getMessage());
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "storage_saved", name);
        return 1;
    }

    private static int storageGive(CommandContext<FabricClientCommandSource> ctx, String name) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        RegistryWrapper.WrapperLookup registries = getRegistries();

        StorageUtil.StoredItem stored;
        try {
            stored = StorageUtil.load(name, registries);
        } catch (StorageUtil.InvalidNameException e) {
            sendLangFeedback(ctx.getSource(), "storage_invalid_name", name);
            return 0;
        } catch (StorageUtil.CorruptEntryException e) {
            sendLangFeedback(ctx.getSource(), "storage_corrupt_entry", name);
            return 0;
        } catch (java.io.IOException e) {
            sendLangFeedback(ctx.getSource(), "storage_not_found", name);
            return 0;
        }

        int emptySlot = player.getInventory().getEmptySlot();
        if (emptySlot < 0) {
            sendLangFeedback(ctx.getSource(), "storage_inventory_full");
            return 0;
        }

        int packetSlot = emptySlot < 9 ? 36 + emptySlot : emptySlot;

        // КРИТИЧНО: та же самая болезнь, что была в syncHandItem — пакет ниже
        // только уведомляет сервер, а клиент своё отображение инвентаря сам
        // не обновляет. Без этой строки предмет визуально появляется только
        // после ресинка с сервером (например, при перезаходе).
        player.getInventory().setStack(emptySlot, stored.stack);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(packetSlot, stored.stack));
        }

        sendLangFeedback(ctx.getSource(), "storage_given", name);
        return 1;
    }

    private static int storageInfo(CommandContext<FabricClientCommandSource> ctx, String name) {
        StorageUtil.InfoResult info;
        try {
            info = StorageUtil.info(name);
        } catch (StorageUtil.InvalidNameException e) {
            sendLangFeedback(ctx.getSource(), "storage_invalid_name", name);
            return 0;
        } catch (StorageUtil.CorruptEntryException e) {
            sendLangFeedback(ctx.getSource(), "storage_corrupt_entry", name);
            return 0;
        } catch (java.io.IOException e) {
            sendLangFeedback(ctx.getSource(), "storage_not_found", name);
            return 0;
        }

        sendLangFeedback(ctx.getSource(), "storage_info_material", name, info.material);

        String displayName = info.rawName != null ? MiniMessageBridge.toPlain(MiniMessageBridge.parse(info.rawName)) : null;
        if (displayName != null) {
            sendLangFeedback(ctx.getSource(), "storage_info_name", displayName);
        } else {
            sendLangFeedback(ctx.getSource(), "storage_info_name_none");
        }

        if (info.rawLore.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "storage_info_lore_none");
        } else {
            for (int i = 0; i < info.rawLore.size(); i++) {
                String plainLine = MiniMessageBridge.toPlain(MiniMessageBridge.parse(info.rawLore.get(i)));
                sendLangFeedback(ctx.getSource(), "storage_info_lore_entry", i + 1, plainLine);
            }
        }
        return 1;
    }

    private static int storageList(CommandContext<FabricClientCommandSource> ctx) {
        List<String> names = StorageUtil.names();
        if (names.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "storage_list_empty");
            return 0;
        }
        for (String name : names) {
            sendLangFeedback(ctx.getSource(), "storage_list_entry", name);
        }
        return names.size();
    }

    private static int storageDelete(CommandContext<FabricClientCommandSource> ctx, String name) {
        boolean removed;
        try {
            removed = StorageUtil.delete(name);
        } catch (StorageUtil.InvalidNameException e) {
            sendLangFeedback(ctx.getSource(), "storage_invalid_name", name);
            return 0;
        }
        sendLangFeedback(ctx.getSource(), removed ? "storage_deleted" : "storage_not_found", name);
        return removed ? 1 : 0;
    }

    private static int storageClearRequest(CommandContext<FabricClientCommandSource> ctx) {
        int count = StorageUtil.requestClear();
        if (count == 0) {
            StorageUtil.cancelClear();
            sendLangFeedback(ctx.getSource(), "storage_list_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "storage_clear_pending", count);
        return 1;
    }

    private static int storageClearConfirm(CommandContext<FabricClientCommandSource> ctx) {
        if (!StorageUtil.hasPendingClear()) {
            sendLangFeedback(ctx.getSource(), "storage_clear_no_pending");
            return 0;
        }
        int removed = StorageUtil.confirmClear();
        sendLangFeedback(ctx.getSource(), "storage_clear_done", removed);
        return removed;
    }

    // ================================================================
    //  /cie itemModel ...  (ITEM_MODEL)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> itemModelNode() {
        return ClientCommandManager.literal("itemModel")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getItemModel))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("item", IdentifierArgumentType.identifier())
                                .suggests(ITEM_SUGGESTIONS)
                                .executes(CIECommand::setItemModel)))
                .then(ClientCommandManager.literal("reset").executes(CIECommand::resetItemModel));
    }

    private static int resetItemModel(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        ItemModelUtil.removeItemModel(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "item_model_reset");
        return 1;
    }

    private static int getItemModel(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        Identifier model = ItemModelUtil.getItemModel(stack);
        sendLangFeedback(ctx.getSource(), "item_model_status", model == null ? "нет" : model.toString());
        return 1;
    }

    private static int setItemModel(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier model = ctx.getArgument("item", Identifier.class);
        ItemModelUtil.setItemModel(stack, model);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "item_model_set", model.toString());
        return 1;
    }

    // ================================================================
    //  /cie glider ...  (GLIDER)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> gliderNode() {
        return ClientCommandManager.literal("glider")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getGlider))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                .executes(CIECommand::setGlider)));
    }

    private static int getGlider(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "glider_status", GliderUtil.hasGlider(stack));
        return 1;
    }

    private static int setGlider(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        boolean val = BoolArgumentType.getBool(ctx, "value");
        GliderUtil.setGlider(stack, val);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "glider_set", val);
        return 1;
    }

    // ================================================================
    //  /cie cooldown ...  (USE_COOLDOWN)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> useCooldownNode() {
        return ClientCommandManager.literal("cooldown")
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearCooldown))
                .then(ClientCommandManager.literal("group")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getCooldownGroup))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                        .executes(CIECommand::setCooldownGroup)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::clearCooldown)))
                .then(ClientCommandManager.literal("seconds")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getCooldownSeconds))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("secs", FloatArgumentType.floatArg(0.0f))
                                        .executes(CIECommand::setCooldownSeconds)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::clearCooldown)));
    }

    private static int clearCooldown(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        UseCooldownUtil.removeUseCooldown(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "cooldown_cleared");
        return 1;
    }

    private static int getCooldownGroup(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        Identifier group = UseCooldownUtil.getGroup(stack);
        sendLangFeedback(ctx.getSource(), "cooldown_group_status", group == null ? "нет" : group.toString());
        return 1;
    }

    private static int setCooldownGroup(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier group = ctx.getArgument("id", Identifier.class);
        UseCooldownUtil.setGroup(stack, group);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "cooldown_group_set", group.toString());
        return 1;
    }

    private static int getCooldownSeconds(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "cooldown_seconds_status", UseCooldownUtil.getSeconds(stack));
        return 1;
    }

    private static int setCooldownSeconds(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        float secs = FloatArgumentType.getFloat(ctx, "secs");
        UseCooldownUtil.setSeconds(stack, secs);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "cooldown_seconds_set", secs);
        return 1;
    }

    // ================================================================
    //  /cie remainder ...  (USE_REMAINDER)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> useRemainderNode() {
        return ClientCommandManager.literal("remainder")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getRemainder))
                .then(ClientCommandManager.literal("reset").executes(CIECommand::resetRemainder))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                .suggests(ITEM_SUGGESTIONS)
                                .executes(ctx -> setRemainder(ctx, 1, null))
                                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> setRemainder(ctx, IntegerArgumentType.getInteger(ctx, "count"), null))
                                        .then(ClientCommandManager.argument("components", StringArgumentType.greedyString())
                                                .executes(ctx -> setRemainder(ctx, IntegerArgumentType.getInteger(ctx, "count"),
                                                        StringArgumentType.getString(ctx, "components")))))));
    }

    private static int resetRemainder(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        UseRemainderUtil.removeRemainder(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "remainder_cleared");
        return 1;
    }

    private static int getRemainder(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        ItemStack remainder = UseRemainderUtil.getRemainder(stack);
        if (remainder == null || remainder.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "remainder_status_none");
        } else {
            sendLangFeedback(ctx.getSource(), "remainder_status",
                    Registries.ITEM.getId(remainder.getItem()).toString(), remainder.getCount());
        }
        return 1;
    }

    private static int setRemainder(CommandContext<FabricClientCommandSource> ctx, int count, String componentsSnbt) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier itemId = ctx.getArgument("id", Identifier.class);
        Item item = Registries.ITEM.get(itemId);

        try {
            ItemStack remainder = UseRemainderUtil.buildRemainderStack(item, count, componentsSnbt, getRegistries());
            UseRemainderUtil.setRemainder(stack, remainder);
        } catch (Exception e) {
            sendLangFeedback(ctx.getSource(), "component_parse_error", e.getMessage());
            return 0;
        }

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "remainder_set", itemId.toString(), count);
        return 1;
    }

    // ================================================================
    //  /cie banner ...  (BANNER_PATTERNS, BASE_COLOR)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> bannerNode() {
        return ClientCommandManager.literal("banner")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getBannerPatterns))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearBanner))
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("ids", IdentifierArgumentType.identifier())
                                .suggests(BANNER_PATTERN_SUGGESTIONS)
                                .then(ClientCommandManager.argument("color", StringArgumentType.word())
                                        .suggests(DYE_COLOR_SUGGESTIONS)
                                        .executes(CIECommand::addBannerPattern))))
                .then(ClientCommandManager.literal("insert")
                        .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                                .then(ClientCommandManager.argument("pattern", IdentifierArgumentType.identifier())
                                        .suggests(BANNER_PATTERN_SUGGESTIONS)
                                        .then(ClientCommandManager.argument("color", StringArgumentType.word())
                                                .suggests(DYE_COLOR_SUGGESTIONS)
                                                .executes(CIECommand::insertBannerPattern)))))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                                .executes(CIECommand::removeBannerPattern)))
                .then(ClientCommandManager.literal("base")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getBannerBaseColor))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("color", StringArgumentType.word())
                                        .suggests(DYE_COLOR_SUGGESTIONS)
                                        .executes(CIECommand::setBannerBaseColor))))
                .then(ClientCommandManager.literal("convertToShield").executes(CIECommand::convertBannerToShield));
    }

    private static DyeColor parseDyeColor(String name) throws CommandSyntaxException {
        DyeColor color = DyeColor.byName(name.toLowerCase(Locale.ROOT), null);
        if (color == null) {
            throw createException("bad_id", name);
        }
        return color;
    }

    private static int getBannerPatterns(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);

        var component = stack.get(DataComponentTypes.BANNER_PATTERNS);
        List<BannerPatternsComponent.Layer> layers =
                component != null ? component.layers() : List.of();

        if (layers.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "banner_empty");
            return 0;
        } else {
            for (int i = 0; i < layers.size(); i++) {
                BannerPatternsComponent.Layer layer = layers.get(i);

                String patternId = layer.pattern().getKey()
                        .map(k -> k.getValue().toString())
                        .orElse("?");

                String colorName = layer.color().asString();

                sendLangFeedback(ctx.getSource(), "banner_layer_entry", i + 1, patternId, colorName);
            }
        }
        return layers.size();
    }

    private static int addBannerPattern(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier patternId = ctx.getArgument("ids", Identifier.class);
        String colorStr = StringArgumentType.getString(ctx, "color");
        DyeColor color = parseDyeColor(colorStr);

        if (patternId.toString().equals("minecraft:base")) {
            BannerUtil.INSTANCE.setBaseColor(stack, color);
            syncHandItem(player, stack);
            sendLangFeedback(ctx.getSource(), "banner_base_set", color.asString());
            return 1;
        }

        RegistryEntry<net.minecraft.block.entity.BannerPattern> pattern =
                resolveEntry(RegistryKeys.BANNER_PATTERN, patternId.toString());
        BannerUtil.INSTANCE.addPattern(stack, pattern, color);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "banner_layer_added", patternId.toString(), color.asString());
        return 1;
    }

    private static int insertBannerPattern(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int index = IntegerArgumentType.getInteger(ctx, "index");
        Identifier patternId = ctx.getArgument("pattern", Identifier.class);
        String colorStr = StringArgumentType.getString(ctx, "color");
        DyeColor color = parseDyeColor(colorStr);

        RegistryEntry<net.minecraft.block.entity.BannerPattern> pattern =
                resolveEntry(RegistryKeys.BANNER_PATTERN, patternId.toString());
        BannerUtil.INSTANCE.insertPattern(stack, index - 1, pattern, color);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "banner_layer_inserted", index, patternId.toString(), color.asString());
        return 1;
    }

    private static int removeBannerPattern(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int index = IntegerArgumentType.getInteger(ctx, "index");
        List<BannerPatternsComponent.Layer> layers = BannerUtil.INSTANCE.getLayers(stack);
        checkIndex(index, layers.size());
        BannerUtil.INSTANCE.removePattern(stack, index - 1);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "banner_layer_removed", index);
        return 1;
    }

    private static int getBannerBaseColor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        DyeColor color = BannerUtil.INSTANCE.getBaseColor(stack);
        sendLangFeedback(ctx.getSource(), "banner_base_status", color == null ? "нет" : color.asString());
        return 1;
    }

    private static int setBannerBaseColor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        DyeColor color = parseDyeColor(StringArgumentType.getString(ctx, "color"));

        // Перезаписываем стек новым объектом, у которого изменился тип предмета и базовый цвет
        stack = BannerUtil.INSTANCE.setBaseColor(stack, color);

        // Обязательно устанавливаем новый стек обратно игроку в руку,
        // чтобы клиент и сервер увидели замену предмета (например, синего баннера на черный)
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, stack);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "banner_base_set", color.asString());
        return 1;
    }

    private static int clearBanner(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        BannerUtil.INSTANCE.clear(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "banner_cleared");
        return 1;
    }

    /**
     * Выдаёт новый щит с текущим узором баннера в руке. Предмет в руке
     * НЕ заменяется и не расходуется — щит кладётся в первый свободный
     * слот, как /cie give. Требует, чтобы предмет в руке был баннером
     * (у него могут быть либо узоры, либо просто базовый цвет, либо и то,
     * и другое).
     */
    private static int convertBannerToShield(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack source = player.getMainHandStack();
        if (source.isEmpty()) throw createException("no_item");

        List<BannerPatternsComponent.Layer> layers = BannerUtil.INSTANCE.getLayers(source);
        DyeColor baseColor = BannerUtil.INSTANCE.getBaseColor(source);
        if (baseColor == null && layers.isEmpty()) {
            throw createException("banner_convert_no_pattern");
        }
        if (baseColor == null) {
            baseColor = DyeColor.WHITE;
        }

        ItemStack shield = new ItemStack(net.minecraft.item.Items.SHIELD);
        shield.set(DataComponentTypes.BASE_COLOR, baseColor);
        if (!layers.isEmpty()) {
            shield.set(DataComponentTypes.BANNER_PATTERNS,
                    new BannerPatternsComponent(layers));
        }

        int emptySlot = player.getInventory().getEmptySlot();
        if (emptySlot < 0) {
            sendLangFeedback(ctx.getSource(), "storage_inventory_full");
            return 0;
        }
        player.getInventory().setStack(emptySlot, shield);
        syncSlot(player, emptySlot, shield);

        sendLangFeedback(ctx.getSource(), "banner_convert_success");
        return 1;
    }

    // ================================================================
    //  /cie edit customModelData ...  (CUSTOM_MODEL_DATA)
    // ================================================================

    /** Принимает CSV вида "12,123,14" и парсит в список float. */
    private static List<Float> parseFloatCsv(String raw) throws CommandSyntaxException {
        List<Float> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                result.add(Float.parseFloat(trimmed));
            } catch (NumberFormatException e) {
                throw createException("cmd_bad_number", trimmed);
            }
        }
        return result;
    }

    /** Принимает CSV вида "true,false,true" и парсит в список boolean. */
    private static List<Boolean> parseBooleanCsv(String raw) throws CommandSyntaxException {
        List<Boolean> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) continue;
            if (!trimmed.equals("true") && !trimmed.equals("false")) {
                throw createException("cmd_bad_bool", trimmed);
            }
            result.add(Boolean.parseBoolean(trimmed));
        }
        return result;
    }

    /** Принимает CSV произвольных строк, разделённых запятой (без экранирования запятой внутри значения). */
    private static List<String> parseStringCsv(String raw) {
        List<String> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> customModelDataNode() {
        return ClientCommandManager.literal("customModelData")
                .then(ClientCommandManager.literal("floats")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getCmdFloats))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("floats", StringArgumentType.greedyString())
                                        .executes(CIECommand::setCmdFloats)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetCmdFloats)))
                .then(ClientCommandManager.literal("strings")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getCmdStrings))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("strings", StringArgumentType.greedyString())
                                        .executes(CIECommand::setCmdStrings)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetCmdStrings)))
                .then(ClientCommandManager.literal("flags")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getCmdFlags))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("flags", StringArgumentType.greedyString())
                                        .executes(CIECommand::setCmdFlags)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetCmdFlags)))
                .then(ClientCommandManager.literal("colors")
                        .then(ClientCommandManager.literal("list").executes(CIECommand::listCmdColors))
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("hex", StringArgumentType.word())
                                        .executes(CIECommand::addCmdColor)))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("hex", StringArgumentType.word())
                                        .executes(CIECommand::removeCmdColor)))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearCmdColors)));
    }

    // -- floats --

    private static int getCmdFloats(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "cmd_floats_status", CustomModelDataUtil.Floats.get(stack).toString());
        return 1;
    }

    private static int setCmdFloats(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        List<Float> values = parseFloatCsv(StringArgumentType.getString(ctx, "floats"));
        CustomModelDataUtil.Floats.set(stack, values);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "cmd_floats_set", values.toString());
        return 1;
    }

    private static int resetCmdFloats(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        CustomModelDataUtil.Floats.reset(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "cmd_floats_reset");
        return 1;
    }

    // -- strings --

    private static int getCmdStrings(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "cmd_strings_status", CustomModelDataUtil.Strings.get(stack).toString());
        return 1;
    }

    private static int setCmdStrings(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        List<String> values = parseStringCsv(StringArgumentType.getString(ctx, "strings"));
        CustomModelDataUtil.Strings.set(stack, values);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "cmd_strings_set", values.toString());
        return 1;
    }

    private static int resetCmdStrings(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        CustomModelDataUtil.Strings.reset(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "cmd_strings_reset");
        return 1;
    }

    // -- flags --

    private static int getCmdFlags(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "cmd_flags_status", CustomModelDataUtil.Flags.get(stack).toString());
        return 1;
    }

    private static int setCmdFlags(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        List<Boolean> values = parseBooleanCsv(StringArgumentType.getString(ctx, "flags"));
        CustomModelDataUtil.Flags.set(stack, values);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "cmd_flags_set", values.toString());
        return 1;
    }

    private static int resetCmdFlags(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        CustomModelDataUtil.Flags.reset(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "cmd_flags_reset");
        return 1;
    }

    // -- colors --

    private static int listCmdColors(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "cmd_colors_status", CustomModelDataUtil.Colors.getHex(stack).toString());
        return 1;
    }

    private static int addCmdColor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String hexInput = StringArgumentType.getString(ctx, "hex");
        int rgb;
        try {
            rgb = CustomModelDataUtil.Colors.parseHex(hexInput);
        } catch (IllegalArgumentException e) {
            throw createException("cmd_bad_color", hexInput);
        }
        CustomModelDataUtil.Colors.add(stack, rgb);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "cmd_color_added", CustomModelDataUtil.Colors.toHex(rgb));
        return 1;
    }

    private static int removeCmdColor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String hexInput = StringArgumentType.getString(ctx, "hex");
        int rgb;
        try {
            rgb = CustomModelDataUtil.Colors.parseHex(hexInput);
        } catch (IllegalArgumentException e) {
            throw createException("cmd_bad_color", hexInput);
        }
        boolean removed = CustomModelDataUtil.Colors.remove(stack, rgb);
        if (!removed) {
            sendLangFeedback(ctx.getSource(), "cmd_color_not_found", CustomModelDataUtil.Colors.toHex(rgb));
            return 0;
        }
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "cmd_color_removed", CustomModelDataUtil.Colors.toHex(rgb));
        return 1;
    }

    private static int clearCmdColors(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        CustomModelDataUtil.Colors.clear(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "cmd_colors_cleared");
        return 1;
    }

    // ================================================================
    //  /cie edit SuspiciousStewEffects ...  (SUSPICIOUS_STEW_EFFECTS)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> suspiciousStewEffectsNode() {
        return ClientCommandManager.literal("SuspiciousStewEffects")
                .then(ClientCommandManager.literal("effects")
                        .then(ClientCommandManager.literal("list").executes(CIECommand::listStewEffects))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearStewEffects))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("effect", StringArgumentType.word())
                                        .suggests(STATUS_EFFECT_SUGGESTIONS)
                                        .executes(CIECommand::removeStewEffect)))
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("effect", StringArgumentType.word())
                                        .suggests(STATUS_EFFECT_SUGGESTIONS)
                                        .then(ClientCommandManager.argument("duration", IntegerArgumentType.integer(1))
                                                .executes(CIECommand::addStewEffect)))));
    }

    private static int listStewEffects(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        List<SuspiciousStewEffectsComponent.StewEffect> effects =
                SuspiciousStewEffectsUtil.getEffects(stack);
        if (effects.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "stew_effects_empty");
            return 0;
        }
        for (int i = 0; i < effects.size(); i++) {
            var effect = effects.get(i);
            String effectId = effect.effect().getIdAsString();
            sendLangFeedback(ctx.getSource(), "stew_effect_entry", i + 1, effectId, effect.duration());
        }
        return effects.size();
    }

    private static int clearStewEffects(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        SuspiciousStewEffectsUtil.clear(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "stew_effects_cleared");
        return 1;
    }

    private static int removeStewEffect(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String idStr = StringArgumentType.getString(ctx, "effect");
        RegistryEntry<StatusEffect> entry = resolveEntry(RegistryKeys.STATUS_EFFECT, idStr);
        int removed = SuspiciousStewEffectsUtil.remove(stack, entry);
        if (removed == 0) {
            sendLangFeedback(ctx.getSource(), "stew_effect_not_found", idStr);
            return 0;
        }
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "stew_effect_removed", idStr, removed);
        return removed;
    }

    private static int addStewEffect(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String idStr = StringArgumentType.getString(ctx, "effect");
        int duration = IntegerArgumentType.getInteger(ctx, "duration");
        RegistryEntry<StatusEffect> entry = resolveEntry(RegistryKeys.STATUS_EFFECT, idStr);
        SuspiciousStewEffectsUtil.add(stack, entry, duration);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "stew_effect_added", idStr, duration);
        return 1;
    }

    // ================================================================
    //  /cie edit sign ...  (BLOCK_ENTITY_DATA — sign)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> signNode() {
        return ClientCommandManager.literal("sign")
                .then(ClientCommandManager.literal("type")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getSignType))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("type", StringArgumentType.word())
                                        .suggests(SIGN_TYPE_SUGGESTIONS)
                                        .executes(CIECommand::setSignType)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetSignType)))
                .then(ClientCommandManager.literal("waxed")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getSignWaxed))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(CIECommand::setSignWaxed))))
                .then(signSideNode("frontSide", SignBlockEntityUtil.FRONT))
                .then(signSideNode("backSide", SignBlockEntityUtil.BACK));
    }

    /** Общая под-структура glowing/baseColor/lines — одинаковая для frontSide и backSide, различается только ключом стороны в NBT. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> signSideNode(String literalName, String sideKey) {
        return ClientCommandManager.literal(literalName)
                .then(ClientCommandManager.literal("glowing")
                        .then(ClientCommandManager.literal("get")
                                .executes(ctx -> getSignGlowing(ctx, sideKey)))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setSignGlowing(ctx, sideKey)))))
                .then(ClientCommandManager.literal("baseColor")
                        .then(ClientCommandManager.literal("get")
                                .executes(ctx -> getSignBaseColor(ctx, sideKey)))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("color", StringArgumentType.word())
                                        .suggests(DYE_COLOR_SUGGESTIONS)
                                        .executes(ctx -> setSignBaseColor(ctx, sideKey))))
                        .then(ClientCommandManager.literal("reset")
                                .executes(ctx -> resetSignBaseColor(ctx, sideKey))))
                .then(ClientCommandManager.literal("lines")
                        .then(ClientCommandManager.literal("list")
                                .executes(ctx -> listSignLines(ctx, sideKey)))
                        .then(ClientCommandManager.literal("clear")
                                .executes(ctx -> clearSignLines(ctx, sideKey)))
                        .then(ClientCommandManager.literal("get")
                                .then(ClientCommandManager.argument("line", IntegerArgumentType.integer(1, SignBlockEntityUtil.LINE_COUNT))
                                        .executes(ctx -> getSignLine(ctx, sideKey))))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("line", IntegerArgumentType.integer(1, SignBlockEntityUtil.LINE_COUNT))
                                        .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                                .executes(ctx -> setSignLine(ctx, sideKey)))))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("line", IntegerArgumentType.integer(1, SignBlockEntityUtil.LINE_COUNT))
                                        .executes(ctx -> removeSignLine(ctx, sideKey)))));
    }

    // -- type --

    private static int getSignType(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "sign_type_status", Registries.ITEM.getId(SignBlockEntityUtil.getType(stack)).toString());
        return 1;
    }

    private static int setSignType(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String type = StringArgumentType.getString(ctx, "type");
        var newItem = SignBlockEntityUtil.resolveTypeItem(type);
        if (newItem == null) {
            throw createException("sign_unknown_type", type);
        }
        ItemStack newStack = SignBlockEntityUtil.withType(stack, newItem);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, newStack);
        syncHandItem(player, newStack);
        sendLangFeedback(ctx.getSource(), "sign_type_set", type);
        return 1;
    }

    private static int resetSignType(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        ItemStack newStack = SignBlockEntityUtil.reset(stack);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, newStack);
        syncHandItem(player, newStack);
        sendLangFeedback(ctx.getSource(), "sign_type_reset");
        return 1;
    }

    // -- waxed --

    private static int getSignWaxed(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "sign_waxed_status", SignBlockEntityUtil.isWaxed(stack));
        return 1;
    }

    private static int setSignWaxed(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        boolean value = BoolArgumentType.getBool(ctx, "value");
        SignBlockEntityUtil.setWaxed(stack, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "sign_waxed_set", value);
        return 1;
    }

    // -- glowing --

    private static int getSignGlowing(CommandContext<FabricClientCommandSource> ctx, String side) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "sign_glowing_status", SignBlockEntityUtil.isGlowing(stack, side));
        return 1;
    }

    private static int setSignGlowing(CommandContext<FabricClientCommandSource> ctx, String side) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        boolean value = BoolArgumentType.getBool(ctx, "value");
        SignBlockEntityUtil.setGlowing(stack, side, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "sign_glowing_set", value);
        return 1;
    }

    // -- baseColor --

    private static int getSignBaseColor(CommandContext<FabricClientCommandSource> ctx, String side) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "sign_base_color_status", SignBlockEntityUtil.getBaseColor(stack, side).getId());
        return 1;
    }

    private static int setSignBaseColor(CommandContext<FabricClientCommandSource> ctx, String side) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        DyeColor color = parseDyeColor(StringArgumentType.getString(ctx, "color"));
        SignBlockEntityUtil.setBaseColor(stack, side, color);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "sign_base_color_set", color.getId());
        return 1;
    }

    private static int resetSignBaseColor(CommandContext<FabricClientCommandSource> ctx, String side) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        SignBlockEntityUtil.resetBaseColor(stack, side);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "sign_base_color_reset");
        return 1;
    }

    // -- lines --

    private static int listSignLines(CommandContext<FabricClientCommandSource> ctx, String side) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        List<String> lines = SignBlockEntityUtil.getLines(stack, side, getRegistries());
        for (int i = 0; i < lines.size(); i++) {
            sendLangFeedback(ctx.getSource(), "sign_line_entry", i + 1, lines.get(i));
        }
        return lines.size();
    }

    private static int clearSignLines(CommandContext<FabricClientCommandSource> ctx, String side) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        SignBlockEntityUtil.clearLines(stack, side, getRegistries());
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "sign_lines_cleared");
        return 1;
    }

    private static int getSignLine(CommandContext<FabricClientCommandSource> ctx, String side) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        int line = IntegerArgumentType.getInteger(ctx, "line");
        String text = SignBlockEntityUtil.getLine(stack, side, line - 1, getRegistries());
        sendLangFeedback(ctx.getSource(), "sign_line_status", line, text);
        return 1;
    }

    private static int setSignLine(CommandContext<FabricClientCommandSource> ctx, String side) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int line = IntegerArgumentType.getInteger(ctx, "line");
        String text = StringArgumentType.getString(ctx, "text");
        SignBlockEntityUtil.setLine(stack, side, line - 1, text, getRegistries());
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "sign_line_set", line);
        return 1;
    }

    private static int removeSignLine(CommandContext<FabricClientCommandSource> ctx, String side) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int line = IntegerArgumentType.getInteger(ctx, "line");
        SignBlockEntityUtil.removeLine(stack, side, line - 1, getRegistries());
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "sign_line_removed", line);
        return 1;
    }

    // ================================================================
    //  /cie edit EntitySettings ...  (ENTITY_DATA — яйцо призыва)
    // ================================================================

    /** "boots" в командах -> "feet" в NBT-ключах equipment (расхождение имён предмета брони и NBT-слота — стандартное для ванильного equipment). */
    private static final Map<String, String> EQUIPMENT_SLOT_TO_NBT_KEY = Map.of(
            "head", "head", "chest", "chest", "legs", "legs",
            "boots", "feet", "mainhand", "mainhand", "offhand", "offhand");

    private static LiteralArgumentBuilder<FabricClientCommandSource> entitySettingsNode() {
        return ClientCommandManager.literal("EntitySettings")
                .then(ClientCommandManager.literal("motion")
                        .then(entityMotionAxisNode("x", 0))
                        .then(entityMotionAxisNode("y", 1))
                        .then(entityMotionAxisNode("z", 2)))
                .then(ClientCommandManager.literal("customName")
                        .then(ClientCommandManager.literal("name")
                                .then(ClientCommandManager.literal("get").executes(CIECommand::getEntityCustomName))
                                .then(ClientCommandManager.literal("set")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                                .executes(CIECommand::setEntityCustomName)))
                                .then(ClientCommandManager.literal("reset").executes(CIECommand::resetEntityCustomName)))
                        .then(ClientCommandManager.literal("customNameVisible")
                                .then(ClientCommandManager.literal("get").executes(CIECommand::getEntityCustomNameVisible))
                                .then(ClientCommandManager.literal("set")
                                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(CIECommand::setEntityCustomNameVisible)))))
                .then(ClientCommandManager.literal("tags")
                        .then(ClientCommandManager.literal("list").executes(CIECommand::listEntityTags))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearEntityTags))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("tag", StringArgumentType.word())
                                        .executes(CIECommand::removeEntityTag)))
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("tag", StringArgumentType.word())
                                        .executes(CIECommand::addEntityTag))))
                .then(ClientCommandManager.literal("entity")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getEntityType))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("entity", StringArgumentType.word())
                                        .suggests(ENTITY_TYPE_SUGGESTIONS)
                                        .executes(CIECommand::setEntityType)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetEntityType)))
                .then(ClientCommandManager.literal("facing")
                        .then(ClientCommandManager.literal("yaw")
                                .then(ClientCommandManager.literal("get").executes(CIECommand::getEntityYaw))
                                .then(ClientCommandManager.literal("set")
                                        .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg())
                                                .executes(CIECommand::setEntityYaw)))
                                .then(ClientCommandManager.literal("reset").executes(CIECommand::resetEntityYaw)))
                        .then(ClientCommandManager.literal("pitch")
                                .then(ClientCommandManager.literal("get").executes(CIECommand::getEntityPitch))
                                .then(ClientCommandManager.literal("set")
                                        .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(-90, 90))
                                                .executes(CIECommand::setEntityPitch)))
                                .then(ClientCommandManager.literal("reset").executes(CIECommand::resetEntityPitch))))
                .then(entityBoolFlagNode("invulnerable", EntitySettingsUtil::getInvulnerable, EntitySettingsUtil::setInvulnerable))
                .then(entityBoolFlagNode("silent", EntitySettingsUtil::getSilent, EntitySettingsUtil::setSilent))
                .then(entityBoolFlagNode("noAI", EntitySettingsUtil::getNoAI, EntitySettingsUtil::setNoAI))
                .then(entityBoolFlagNode("canPickUpLoot", EntitySettingsUtil::getCanPickUpLoot, EntitySettingsUtil::setCanPickUpLoot))
                .then(entityBoolFlagNode("noGravity", EntitySettingsUtil::getNoGravity, EntitySettingsUtil::setNoGravity))
                .then(entityBoolFlagNode("visualFire", EntitySettingsUtil::getVisualFire, EntitySettingsUtil::setVisualFire))
                .then(entityBoolFlagNode("glowing", EntitySettingsUtil::getGlowing, EntitySettingsUtil::setGlowing))
                .then(ClientCommandManager.literal("health")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getEntityHealth))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0))
                                        .executes(CIECommand::setEntityHealth)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetEntityHealth)))
                .then(ClientCommandManager.literal("equipment")
                        .then(equipmentSlotNode("head"))
                        .then(equipmentSlotNode("chest"))
                        .then(equipmentSlotNode("legs"))
                        .then(equipmentSlotNode("boots"))
                        .then(equipmentSlotNode("mainhand"))
                        .then(equipmentSlotNode("offhand")));
    }

    // -- motion (общий фактор для x/y/z) --

    private static LiteralArgumentBuilder<FabricClientCommandSource> entityMotionAxisNode(String literalName, int axis) {
        return ClientCommandManager.literal(literalName)
                .then(ClientCommandManager.literal("get").executes(ctx -> getEntityMotion(ctx, axis)))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("value", DoubleArgumentType.doubleArg())
                                .executes(ctx -> setEntityMotion(ctx, axis))))
                .then(ClientCommandManager.literal("reset").executes(ctx -> resetEntityMotion(ctx, axis)));
    }

    private static int getEntityMotion(CommandContext<FabricClientCommandSource> ctx, int axis) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "entity_motion_status", EntitySettingsUtil.getMotion(stack, axis));
        return 1;
    }

    private static int setEntityMotion(CommandContext<FabricClientCommandSource> ctx, int axis) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        double value = DoubleArgumentType.getDouble(ctx, "value");
        EntitySettingsUtil.setMotion(stack, axis, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_motion_set", value);
        return 1;
    }

    private static int resetEntityMotion(CommandContext<FabricClientCommandSource> ctx, int axis) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        EntitySettingsUtil.resetMotion(stack, axis);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_motion_reset");
        return 1;
    }

    // -- customName --

    private static int getEntityCustomName(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "entity_custom_name_status", EntitySettingsUtil.getCustomName(stack, getRegistries()));
        return 1;
    }

    private static int setEntityCustomName(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String name = StringArgumentType.getString(ctx, "name");
        EntitySettingsUtil.setCustomName(stack, name, getRegistries());
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_custom_name_set");
        return 1;
    }

    private static int resetEntityCustomName(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        EntitySettingsUtil.resetCustomName(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_custom_name_reset");
        return 1;
    }

    private static int getEntityCustomNameVisible(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "entity_custom_name_visible_status", EntitySettingsUtil.isCustomNameVisible(stack));
        return 1;
    }

    private static int setEntityCustomNameVisible(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        boolean value = BoolArgumentType.getBool(ctx, "value");
        EntitySettingsUtil.setCustomNameVisible(stack, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_custom_name_visible_set", value);
        return 1;
    }

    // -- tags --

    private static int listEntityTags(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        List<String> tags = EntitySettingsUtil.getTags(stack);
        if (tags.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "entity_tags_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "entity_tags_status", String.join(", ", tags));
        return tags.size();
    }

    private static int clearEntityTags(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        EntitySettingsUtil.clearTags(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_tags_cleared");
        return 1;
    }

    private static int removeEntityTag(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String tag = StringArgumentType.getString(ctx, "tag");
        boolean removed = EntitySettingsUtil.removeTag(stack, tag);
        if (!removed) {
            sendLangFeedback(ctx.getSource(), "entity_tag_not_found", tag);
            return 0;
        }
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_tag_removed", tag);
        return 1;
    }

    private static int addEntityTag(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String tag = StringArgumentType.getString(ctx, "tag");
        EntitySettingsUtil.addTag(stack, tag);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_tag_added", tag);
        return 1;
    }

    // -- entity type --

    private static int getEntityType(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        var type = EntitySettingsUtil.getCurrentType(stack);
        sendLangFeedback(ctx.getSource(), "entity_type_status", Registries.ENTITY_TYPE.getId(type).toString());
        return 1;
    }

    private static int setEntityType(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String idStr = StringArgumentType.getString(ctx, "entity");
        Identifier id = Identifier.tryParse(idStr);
        if (id == null || !Registries.ENTITY_TYPE.containsId(id)) {
            throw createException("entity_unknown_type", idStr);
        }
        EntitySettingsUtil.setEntity(stack, Registries.ENTITY_TYPE.get(id));
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_type_set", idStr);
        return 1;
    }

    private static int resetEntityType(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        EntitySettingsUtil.resetEntity(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_type_reset");
        return 1;
    }

    // -- facing --

    private static int getEntityYaw(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "entity_yaw_status", EntitySettingsUtil.getYaw(stack));
        return 1;
    }

    private static int setEntityYaw(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        float value = FloatArgumentType.getFloat(ctx, "value");
        EntitySettingsUtil.setYaw(stack, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_yaw_set", value);
        return 1;
    }

    private static int resetEntityYaw(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        EntitySettingsUtil.resetYaw(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_yaw_reset");
        return 1;
    }

    private static int getEntityPitch(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "entity_pitch_status", EntitySettingsUtil.getPitch(stack));
        return 1;
    }

    private static int setEntityPitch(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        float value = FloatArgumentType.getFloat(ctx, "value");
        EntitySettingsUtil.setPitch(stack, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_pitch_set", value);
        return 1;
    }

    private static int resetEntityPitch(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        EntitySettingsUtil.resetPitch(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_pitch_reset");
        return 1;
    }

    // -- общий boolean-флаг (invulnerable/silent/noAI/canPickUpLoot/noGravity/visualFire/glowing) --

    private static LiteralArgumentBuilder<FabricClientCommandSource> entityBoolFlagNode(
            String literalName,
            java.util.function.Function<ItemStack, Boolean> getter,
            java.util.function.BiConsumer<ItemStack, Boolean> setter) {
        return ClientCommandManager.literal(literalName)
                .then(ClientCommandManager.literal("get")
                        .executes(ctx -> getEntityBoolFlag(ctx, literalName, getter)))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setEntityBoolFlag(ctx, literalName, setter))));
    }

    private static int getEntityBoolFlag(CommandContext<FabricClientCommandSource> ctx, String flagName,
                                         java.util.function.Function<ItemStack, Boolean> getter) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "entity_flag_status", flagName, getter.apply(stack));
        return 1;
    }

    private static int setEntityBoolFlag(CommandContext<FabricClientCommandSource> ctx, String flagName,
                                         java.util.function.BiConsumer<ItemStack, Boolean> setter) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        boolean value = BoolArgumentType.getBool(ctx, "value");
        setter.accept(stack, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_flag_set", flagName, value);
        return 1;
    }

    // -- health --

    private static int getEntityHealth(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "entity_health_status", EntitySettingsUtil.getHealth(stack));
        return 1;
    }

    private static int setEntityHealth(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        float value = FloatArgumentType.getFloat(ctx, "value");
        EntitySettingsUtil.setHealth(stack, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_health_set", value);
        return 1;
    }

    private static int resetEntityHealth(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        EntitySettingsUtil.resetHealth(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_health_reset");
        return 1;
    }

    // -- equipment --

    private static LiteralArgumentBuilder<FabricClientCommandSource> equipmentSlotNode(String slotLiteral) {
        return ClientCommandManager.literal(slotLiteral)
                .then(ClientCommandManager.literal("get")
                        .executes(ctx -> getEntityEquipment(ctx, slotLiteral)))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.literal("fromStorage")
                                .executes(ctx -> setEquipmentFromStorage(ctx, slotLiteral)))
                        .then(ClientCommandManager.literal("fromOffHand")
                                .executes(ctx -> setEquipmentFromOffHand(ctx, slotLiteral, -1))
                                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> setEquipmentFromOffHand(ctx, slotLiteral, IntegerArgumentType.getInteger(ctx, "count")))))
                        .then(ClientCommandManager.literal("new")
                                .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                        .suggests(ITEM_SUGGESTIONS)
                                        .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> setEquipmentNew(ctx, slotLiteral, ""))
                                                .then(ClientCommandManager.argument("components", StringArgumentType.greedyString())
                                                        .executes(ctx -> setEquipmentNew(ctx, slotLiteral,
                                                                StringArgumentType.getString(ctx, "components"))))))))
                .then(ClientCommandManager.literal("dropChance")
                        .then(ClientCommandManager.literal("get")
                                .executes(ctx -> getEquipmentDropChance(ctx, slotLiteral)))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("chance", FloatArgumentType.floatArg(0, 1))
                                        .executes(ctx -> setEquipmentDropChance(ctx, slotLiteral))))
                        .then(ClientCommandManager.literal("reset")
                                .executes(ctx -> resetEquipmentDropChance(ctx, slotLiteral))));
    }

    private static int getEntityEquipment(CommandContext<FabricClientCommandSource> ctx, String slotLiteral) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        String nbtKey = EQUIPMENT_SLOT_TO_NBT_KEY.get(slotLiteral);
        ItemStack equipped = EntitySettingsUtil.getEquipment(stack, nbtKey, getRegistries());
        if (equipped.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "entity_equipment_empty", slotLiteral);
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "entity_equipment_status", slotLiteral,
                Registries.ITEM.getId(equipped.getItem()).toString(), equipped.getCount());
        return 1;
    }

    /**
     * Раньше принимала typed <name> из старого storage. Теперь — клик-пикер:
     * запрашивает выбор в StoragePicker, открывает /cie storage, а сам
     * setEquipment выполняется АСИНХРОННО в момент клика по предмету
     * (см. StoragePicker.completePick вызванный из StorageScreen).
     *
     * Слот руки фиксируем ЗАРАНЕЕ (selectedSlot) и пишем через syncSlot
     * по этому индексу, а не через syncHandItem/getSelectedSlot() — если
     * игрок успеет переключить хотбар, пока storage открыт, результат
     * всё равно попадёт в правильный (изначальный) слот, а не в тот,
     * что выбран на момент клика.
     */
    private static int setEquipmentFromStorage(CommandContext<FabricClientCommandSource> ctx, String slotLiteral) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String nbtKey = EQUIPMENT_SLOT_TO_NBT_KEY.get(slotLiteral);
        RegistryWrapper.WrapperLookup registries = getRegistries();
        int selectedSlot = player.getInventory().selectedSlot;

        StoragePicker.requestPick(picked -> {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p == null) {
                return;
            }
            ItemStack egg = p.getInventory().getStack(selectedSlot);
            if (egg.isEmpty()) {
                return;
            }
            EntitySettingsUtil.setEquipment(egg, nbtKey, picked, registries);
            syncSlot(p, selectedSlot, egg);
        });

        openStoragePage(ctx.getSource(), StoragePageUtil.getCurrentPageIndex());
        sendLangFeedback(ctx.getSource(), "entity_equipment_pick_prompt", slotLiteral);
        return 1;
    }

    private static int setEquipmentFromOffHand(CommandContext<FabricClientCommandSource> ctx, String slotLiteral, int countOverride) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        ItemStack offHand = player.getOffHandStack();
        if (offHand.isEmpty()) {
            throw createException("entity_equipment_offhand_empty");
        }
        UndoUtil.pushSnapshot(player, stack);
        ItemStack toEquip = offHand.copy();
        if (countOverride > 0) {
            toEquip.setCount(countOverride);
        }
        String nbtKey = EQUIPMENT_SLOT_TO_NBT_KEY.get(slotLiteral);
        EntitySettingsUtil.setEquipment(stack, nbtKey, toEquip, getRegistries());
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_equipment_set_offhand", slotLiteral);
        return 1;
    }

    private static int setEquipmentNew(CommandContext<FabricClientCommandSource> ctx, String slotLiteral, String componentsSnbt) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        Identifier itemId = ctx.getArgument("id", Identifier.class);
        Item newItem = Registries.ITEM.get(itemId);
        int count = IntegerArgumentType.getInteger(ctx, "count");

        ItemStack toEquip;
        try {
            toEquip = UseRemainderUtil.buildRemainderStack(newItem, count, componentsSnbt, getRegistries());
        } catch (Exception e) {
            sendLangFeedback(ctx.getSource(), "component_parse_error", e.getMessage());
            return 0;
        }

        UndoUtil.pushSnapshot(player, stack);
        String nbtKey = EQUIPMENT_SLOT_TO_NBT_KEY.get(slotLiteral);
        EntitySettingsUtil.setEquipment(stack, nbtKey, toEquip, getRegistries());
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_equipment_set", slotLiteral, itemId.toString());
        return 1;
    }

    private static int getEquipmentDropChance(CommandContext<FabricClientCommandSource> ctx, String slotLiteral) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        String nbtKey = EQUIPMENT_SLOT_TO_NBT_KEY.get(slotLiteral);
        sendLangFeedback(ctx.getSource(), "entity_drop_chance_status", slotLiteral, EntitySettingsUtil.getDropChance(stack, nbtKey));
        return 1;
    }

    private static int setEquipmentDropChance(CommandContext<FabricClientCommandSource> ctx, String slotLiteral) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        float chance = FloatArgumentType.getFloat(ctx, "chance");
        String nbtKey = EQUIPMENT_SLOT_TO_NBT_KEY.get(slotLiteral);
        EntitySettingsUtil.setDropChance(stack, nbtKey, chance);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_drop_chance_set", slotLiteral, chance);
        return 1;
    }

    private static int resetEquipmentDropChance(CommandContext<FabricClientCommandSource> ctx, String slotLiteral) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String nbtKey = EQUIPMENT_SLOT_TO_NBT_KEY.get(slotLiteral);
        EntitySettingsUtil.resetDropChance(stack, nbtKey);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_drop_chance_reset", slotLiteral);
        return 1;
    }

    // ================================================================
    //  /cie language ...  (файлы .minecraft/cie/languages/*.json)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> languageNode() {
        return ClientCommandManager.literal("language")
                .executes(CIECommand::getLanguage)
                .then(ClientCommandManager.literal("get").executes(CIECommand::getLanguage))
                .then(ClientCommandManager.literal("list").executes(CIECommand::listLanguages))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("lang", StringArgumentType.word())
                                .suggests(LANGUAGE_SUGGESTIONS)
                                .executes(CIECommand::setLanguage)))
                .then(ClientCommandManager.literal("create")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(CIECommand::createLanguage)));
    }

    private static int listLanguages(CommandContext<FabricClientCommandSource> ctx) {
        List<String> langs = CIELang.listLanguages();
        sendLangFeedback(ctx.getSource(), "language_list", String.join(", ", langs));
        return langs.size();
    }

    private static int createLanguage(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        boolean created = CIELang.createLanguage(name);
        if (!created) {
            sendLangFeedback(ctx.getSource(), "language_create_error", name);
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "language_created", name);
        return 1;
    }

    private static int getLanguage(CommandContext<FabricClientCommandSource> ctx) {
        sendLangFeedback(ctx.getSource(), "language_current", CIELang.getCurrentLanguage(),
                String.join(", ", CIELang.listLanguages()));
        return 1;
    }

    private static int setLanguage(CommandContext<FabricClientCommandSource> ctx) {
        String lang = StringArgumentType.getString(ctx, "lang");
        boolean ok = CIELang.setLanguage(lang);
        if (!ok) {
            // до переключения языка (не найден) — сообщение шлём на ТЕКУЩЕМ (старом) языке, это ок
            sendLangFeedback(ctx.getSource(), "language_not_found", lang);
            return 0;
        }
        // после успешного переключения — уже на НОВОМ языке
        sendLangFeedback(ctx.getSource(), "language_set", lang);
        return 1;
    }

    // ================================================================
    //  /cie gradient ...  (форматы + генерация градиентов)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> gradientNode() {
        return ClientCommandManager.literal("gradient")
                .then(ClientCommandManager.literal("format")
                        .then(ClientCommandManager.literal("list").executes(CIECommand::listGradientFormats))
                        .then(ClientCommandManager.literal("custom")
                                .then(ClientCommandManager.literal("create")
                                        .then(ClientCommandManager.argument("format", GradientFormatArgumentType.formatName())
                                                .then(ClientCommandManager.argument("hexPrefix", StringArgumentType.string())
                                                        .then(ClientCommandManager.argument("hexSuffix", StringArgumentType.string())
                                                                .executes(ctx -> createCustomGradientFormat(ctx, ""))
                                                                .then(ClientCommandManager.argument("hexSeparator", StringArgumentType.string())
                                                                        .executes(ctx -> createCustomGradientFormat(ctx,
                                                                                StringArgumentType.getString(ctx, "hexSeparator"))))))))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("format", GradientFormatArgumentType.formatName())
                                                .suggests(GRADIENT_CUSTOM_FORMAT_SUGGESTIONS)
                                                .executes(CIECommand::removeCustomGradientFormat)))
                                .then(ClientCommandManager.literal("clear")
                                        .then(ClientCommandManager.argument("format", GradientFormatArgumentType.formatName())
                                                .suggests(GRADIENT_CUSTOM_FORMAT_SUGGESTIONS)
                                                .executes(CIECommand::clearCustomGradientFormat)))
                                .then(ClientCommandManager.literal("list").executes(CIECommand::listCustomGradientFormats))
                                .then(ClientCommandManager.argument("format", GradientFormatArgumentType.formatName())
                                        .suggests(GRADIENT_CUSTOM_FORMAT_SUGGESTIONS)
                                        .then(ClientCommandManager.literal("get").executes(CIECommand::getCustomGradientFormat))))
                        .then(ClientCommandManager.argument("format", GradientFormatArgumentType.formatName())
                                .suggests(GRADIENT_FORMAT_SUGGESTIONS)
                                .then(ClientCommandManager.literal("get").executes(CIECommand::getGradientFormatExample))))
                .then(ClientCommandManager.literal("preset")
                        .then(ClientCommandManager.literal("list").executes(CIECommand::listGradientPresets))
                        .then(ClientCommandManager.literal("create")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("hexs", StringArgumentType.string())
                                                .executes(CIECommand::createGradientPreset))))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests(GRADIENT_PRESET_NAME_SUGGESTIONS)
                                        .executes(CIECommand::removeGradientPreset)))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearGradientPresets)))
                .then(ClientCommandManager.literal("create")
                        .then(ClientCommandManager.literal("gradient")
                                .then(ClientCommandManager.argument("format", GradientFormatArgumentType.formatName())
                                        .suggests(GRADIENT_FORMAT_SUGGESTIONS)
                                        .then(ClientCommandManager.argument("hexs", GradientFormatArgumentType.formatName())
                                                .suggests(GRADIENT_PRESET_HEXS_SUGGESTIONS)
                                                .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                                        .executes(CIECommand::createGradientCommand)))))
                        .then(ClientCommandManager.literal("rainbow")
                                .then(ClientCommandManager.argument("format", GradientFormatArgumentType.formatName())
                                        .suggests(GRADIENT_FORMAT_SUGGESTIONS)
                                        .then(ClientCommandManager.argument("saturation", IntegerArgumentType.integer(0, 100))
                                                .then(ClientCommandManager.argument("brightness", IntegerArgumentType.integer(0, 100))
                                                        .then(ClientCommandManager.argument("shade", IntegerArgumentType.integer(1, 7))
                                                                .then(ClientCommandManager.argument("step", IntegerArgumentType.integer(1, 100))
                                                                        .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                                                                .executes(CIECommand::createRainbowCommand))))))))
                        .then(ClientCommandManager.literal("alternation")
                                .then(ClientCommandManager.argument("format", GradientFormatArgumentType.formatName())
                                        .suggests(GRADIENT_FORMAT_SUGGESTIONS)
                                        .then(ClientCommandManager.argument("distance", IntegerArgumentType.integer(1, 100))
                                                .then(ClientCommandManager.argument("hexs", GradientFormatArgumentType.formatName())
                                                        .suggests(GRADIENT_PRESET_HEXS_SUGGESTIONS)
                                                        .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                                                .executes(CIECommand::createAlternationCommand))))))
                        .then(ClientCommandManager.literal("random")
                                .then(ClientCommandManager.argument("format", GradientFormatArgumentType.formatName())
                                        .suggests(GRADIENT_FORMAT_SUGGESTIONS)
                                        .then(ClientCommandManager.argument("colorsCount", IntegerArgumentType.integer(1, 50))
                                                .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                                        .executes(CIECommand::createRandomCommand))))));
    }

    // -- format list/get/custom --

    private static int listGradientFormats(CommandContext<FabricClientCommandSource> ctx) {
        sendLangFeedback(ctx.getSource(), "gradient_format_list", String.join(", ", GradientFormatUtil.listAllFormats()));
        return 1;
    }

    private static int listCustomGradientFormats(CommandContext<FabricClientCommandSource> ctx) {
        List<String> custom = GradientFormatUtil.listCustomFormats();
        if (custom.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "gradient_format_custom_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "gradient_format_list", String.join(", ", custom));
        return custom.size();
    }

    private static int getGradientFormatExample(CommandContext<FabricClientCommandSource> ctx) {
        String format = StringArgumentType.getString(ctx, "format");
        if (!GradientFormatUtil.exists(format)) {
            sendLangFeedback(ctx.getSource(), "gradient_format_unknown", format);
            return 0;
        }
        String example = GradientFormatUtil.render(format, "FFFFFF", 'X');
        sendLangFeedback(ctx.getSource(), "gradient_format_example", format, example);
        return 1;
    }

    private static int getCustomGradientFormat(CommandContext<FabricClientCommandSource> ctx) {
        String format = StringArgumentType.getString(ctx, "format");
        GradientFormatUtil.CustomFormat custom = GradientFormatUtil.getCustomFormat(format);
        if (custom == null) {
            sendLangFeedback(ctx.getSource(), "gradient_format_unknown", format);
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "gradient_format_custom_details", format,
                custom.prefix(), custom.suffix(), custom.separator());
        return 1;
    }

    private static int createCustomGradientFormat(CommandContext<FabricClientCommandSource> ctx, String separator) {
        String format = StringArgumentType.getString(ctx, "format");
        String prefix = StringArgumentType.getString(ctx, "hexPrefix");
        String suffix = StringArgumentType.getString(ctx, "hexSuffix");
        try {
            GradientFormatUtil.createCustomFormat(format, prefix, suffix, separator);
        } catch (IllegalArgumentException e) {
            sendLangFeedback(ctx.getSource(), "gradient_format_create_error", format, e.getMessage());
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "gradient_format_created", format);
        return 1;
    }

    private static int removeCustomGradientFormat(CommandContext<FabricClientCommandSource> ctx) {
        String format = StringArgumentType.getString(ctx, "format");
        boolean removed = GradientFormatUtil.removeCustomFormat(format);
        if (!removed) {
            sendLangFeedback(ctx.getSource(), "gradient_format_unknown", format);
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "gradient_format_removed", format);
        return 1;
    }

    private static int clearCustomGradientFormat(CommandContext<FabricClientCommandSource> ctx) {
        String format = StringArgumentType.getString(ctx, "format");
        boolean cleared = GradientFormatUtil.clearCustomFormat(format);
        if (!cleared) {
            sendLangFeedback(ctx.getSource(), "gradient_format_unknown", format);
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "gradient_format_cleared", format);
        return 1;
    }

    // -- preset list/create/remove/clear --

    private static int listGradientPresets(CommandContext<FabricClientCommandSource> ctx) {
        List<String> names = GradientPresetUtil.list();
        if (names.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "gradient_preset_list_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "gradient_preset_list", String.join(", ", names));
        return names.size();
    }

    private static int createGradientPreset(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String hexsCsv = StringArgumentType.getString(ctx, "hexs");
        try {
            GradientPresetUtil.create(name, hexsCsv);
        } catch (IllegalArgumentException e) {
            sendLangFeedback(ctx.getSource(), "gradient_preset_create_error", name, e.getMessage());
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "gradient_preset_created", name);
        return 1;
    }

    private static int removeGradientPreset(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        boolean removed = GradientPresetUtil.remove(name);
        if (!removed) {
            sendLangFeedback(ctx.getSource(), "gradient_preset_unknown", name);
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "gradient_preset_removed", name);
        return 1;
    }

    private static int clearGradientPresets(CommandContext<FabricClientCommandSource> ctx) {
        int removed = GradientPresetUtil.clear();
        sendLangFeedback(ctx.getSource(), "gradient_preset_cleared", removed);
        return 1;
    }

    // -- create gradient/rainbow/alternation/random --

    /**
     * Раскрывает hexs-аргумент: если начинается с '$' — это ссылка на
     * сохранённый /cie gradient preset (см. GradientPresetUtil), иначе —
     * обычный CSV-список hex-цветов, как и раньше. Кидает
     * IllegalArgumentException как и GradientColorUtil.parseHexList —
     * либо с кривым hex-токеном, либо (для пресетов) с самим именем
     * пресета, которого не существует, — см. reportHexsError.
     */
    private static List<String> resolveHexs(String raw) {
        if (raw.startsWith("$")) {
            String presetName = raw.substring(1);
            List<String> preset = GradientPresetUtil.get(presetName);
            if (preset == null) {
                throw new IllegalArgumentException(presetName);
            }
            return preset;
        }
        return GradientColorUtil.parseHexList(raw);
    }

    private static void reportHexsError(FabricClientCommandSource source, String raw, IllegalArgumentException e) {
        if (raw.startsWith("$")) {
            sendLangFeedback(source, "gradient_preset_unknown", e.getMessage());
        } else {
            sendLangFeedback(source, "gradient_bad_hex", e.getMessage());
        }
    }

    private static int createGradientCommand(CommandContext<FabricClientCommandSource> ctx) {
        String format = StringArgumentType.getString(ctx, "format");
        if (!GradientFormatUtil.exists(format)) {
            sendLangFeedback(ctx.getSource(), "gradient_format_unknown", format);
            return 0;
        }
        String text = StringArgumentType.getString(ctx, "text");
        String hexsRaw = StringArgumentType.getString(ctx, "hexs");
        List<String> hexs;
        try {
            hexs = resolveHexs(hexsRaw);
        } catch (IllegalArgumentException e) {
            reportHexsError(ctx.getSource(), hexsRaw, e);
            return 0;
        }
        List<GradientColorUtil.CharColor> chars = GradientColorUtil.gradient(text, hexs);
        sendGradientResult(ctx.getSource(), chars, format);
        return 1;
    }

    private static int createRainbowCommand(CommandContext<FabricClientCommandSource> ctx) {
        String format = StringArgumentType.getString(ctx, "format");
        if (!GradientFormatUtil.exists(format)) {
            sendLangFeedback(ctx.getSource(), "gradient_format_unknown", format);
            return 0;
        }
        int saturation = IntegerArgumentType.getInteger(ctx, "saturation");
        int brightness = IntegerArgumentType.getInteger(ctx, "brightness");
        int shade = IntegerArgumentType.getInteger(ctx, "shade");
        int step = IntegerArgumentType.getInteger(ctx, "step");
        String text = StringArgumentType.getString(ctx, "text");
        List<GradientColorUtil.CharColor> chars = GradientColorUtil.rainbow(text, saturation, brightness, shade, step);
        sendGradientResult(ctx.getSource(), chars, format);
        return 1;
    }

    private static int createAlternationCommand(CommandContext<FabricClientCommandSource> ctx) {
        String format = StringArgumentType.getString(ctx, "format");
        if (!GradientFormatUtil.exists(format)) {
            sendLangFeedback(ctx.getSource(), "gradient_format_unknown", format);
            return 0;
        }
        int distance = IntegerArgumentType.getInteger(ctx, "distance");
        String hexsRaw = StringArgumentType.getString(ctx, "hexs");
        List<String> hexs;
        try {
            hexs = resolveHexs(hexsRaw);
        } catch (IllegalArgumentException e) {
            reportHexsError(ctx.getSource(), hexsRaw, e);
            return 0;
        }
        String text = StringArgumentType.getString(ctx, "text");
        List<GradientColorUtil.CharColor> chars = GradientColorUtil.alternation(text, distance, hexs);
        sendGradientResult(ctx.getSource(), chars, format);
        return 1;
    }

    private static int createRandomCommand(CommandContext<FabricClientCommandSource> ctx) {
        String format = StringArgumentType.getString(ctx, "format");
        if (!GradientFormatUtil.exists(format)) {
            sendLangFeedback(ctx.getSource(), "gradient_format_unknown", format);
            return 0;
        }
        String text = StringArgumentType.getString(ctx, "text");
        int colorsCount = IntegerArgumentType.getInteger(ctx, "colorsCount");
        List<GradientColorUtil.CharColor> chars = GradientColorUtil.random(text, colorsCount);
        sendGradientResult(ctx.getSource(), chars, format);
        return 1;
    }

    /**
     * Отправляет в чат раскрашенный превью-текст и под ним 2 отдельные
     * кнопки-копии:
     *  - "Скопировать текст" — копирует ЧИСТЫЙ текст (те же символы, без
     *    каких-либо кодов форматирования);
     *  - "Скопировать код" — копирует RAW-строку в выбранном формате
     *    (то, что реально нужно вставить в MiniMessage/легаси/etc, чтобы
     *    получить этот градиент).
     *
     * Для формата "json" код собирается ОДНИМ валидным JSON-массивом
     * (GradientFormatUtil.renderJsonArray), а не конкатенацией по символам
     * — иначе получился бы не-JSON мусор.
     */
    private static void sendGradientResult(FabricClientCommandSource source, List<GradientColorUtil.CharColor> chars, String format) {
        StringBuilder plainBuilder = new StringBuilder();
        MutableText coloredText = Text.empty();
        for (GradientColorUtil.CharColor cc : chars) {
            plainBuilder.append(cc.character());
            int rgb = Integer.parseInt(cc.hexUpper(), 16);
            coloredText.append(Text.literal(String.valueOf(cc.character())).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
        }
        String plainText = plainBuilder.toString();
        String code = format.equals("json")
                ? GradientFormatUtil.renderJsonArray(chars)
                : buildRawCode(chars, format);

        RegistryWrapper.WrapperLookup registries = getRegistries();

        // Текст кнопок в lang-файле — это MiniMessage-разметка
        // (<dark_gray>[<gradient:...>...</gradient>]), а не голый текст.
        // Text.literal(...) её не парсит и печатает угловые теги как есть.
        // Как и в sendCopyableRaw: parse() -> Component (вешаем clickEvent) ->
        // toVanillaText() -> нормальный раскрашенный vanilla Text.
        Component copyTextComponent = MiniMessageBridge.parse(CIELang.get("gradient_copy_text_button"))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(plainText));
        Component copyCodeComponent = MiniMessageBridge.parse(CIELang.get("gradient_copy_code_button"))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(code));

        MutableText copyTextButton = Text.literal(" ").append(MiniMessageBridge.toVanillaText(copyTextComponent, registries));
        MutableText copyCodeButton = Text.literal(" ").append(MiniMessageBridge.toVanillaText(copyCodeComponent, registries));

        MutableText previewLine = coloredText.copy().append(copyTextButton);
        // Раскраска кода через colorizeStructured — те же настраиваемые цвета
        // (/cie coloring get/set/reset), что и у остального структурированного
        // вывода (/give ...[...]), вместо плоского нераскрашенного текста.
        MutableText codeLine = Text.empty()
                .append(colorizeStructured(code))
                .append(copyCodeButton);

        // previewLine и codeLine объединяются в ОДНО сообщение с явным "\n" —
        // два отдельных sendFeedback() визуально слипались в одну строку чата.
        Text finalMessage = Text.empty()
                .append(previewLine)
                .append(Text.literal("\n"))
                .append(codeLine);

        source.sendFeedback(finalMessage);
    }

    private static String buildRawCode(List<GradientColorUtil.CharColor> chars, String format) {
        StringBuilder raw = new StringBuilder();
        for (GradientColorUtil.CharColor cc : chars) {
            raw.append(GradientFormatUtil.render(format, cc.hexUpper(), cc.character()));
        }
        return raw.toString();
    }

    // ================================================================
    //  /cie sound ...  (звук фидбека: error/warn/get/success)
    // ================================================================

    private static final List<String> SOUND_CATEGORIES = List.of("error", "warn", "get", "success");
    private static final SuggestionProvider<FabricClientCommandSource> SOUND_EVENT_SUGGESTIONS =
            registrySuggestions(RegistryKeys.SOUND_EVENT);

    /**
     * /cie sound <error|warn|get|success> get / enabled <bool> / type get / type set <sound>
     * Категория теперь литерал (не аргумент) — их ровно 4, фиксированные,
     * структура каждой категории одинакова, поэтому строим её один раз в
     * soundCategoryNode(...) и переиспользуем на все 4.
     */
    private static LiteralArgumentBuilder<FabricClientCommandSource> soundNode() {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("sound");
        for (SoundSettingsUtil.Category category : SoundSettingsUtil.Category.values()) {
            root.then(soundCategoryNode(category));
        }
        return root;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> soundCategoryNode(SoundSettingsUtil.Category category) {
        return ClientCommandManager.literal(category.key)
                .then(ClientCommandManager.literal("get").executes(ctx -> getSoundSetting(ctx, category)))
                .then(ClientCommandManager.literal("enabled")
                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setSoundEnabled(ctx, category))))
                .then(ClientCommandManager.literal("type")
                        .then(ClientCommandManager.literal("get").executes(ctx -> getSoundType(ctx, category)))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("sound", GradientFormatArgumentType.formatName())
                                        .suggests(SOUND_EVENT_SUGGESTIONS)
                                        .executes(ctx -> setSoundType(ctx, category)))));
    }

    private static int getSoundSetting(CommandContext<FabricClientCommandSource> ctx, SoundSettingsUtil.Category category) {
        SoundSettingsUtil.SoundSetting setting = SoundSettingsUtil.get(category);
        sendLangFeedback(ctx.getSource(), "sound_status", category.key, setting.enabled(), setting.soundId());
        return 1;
    }

    private static int setSoundEnabled(CommandContext<FabricClientCommandSource> ctx, SoundSettingsUtil.Category category) {
        boolean value = BoolArgumentType.getBool(ctx, "value");
        SoundSettingsUtil.setEnabled(category, value);
        sendLangFeedback(ctx.getSource(), "sound_enabled_set", category.key, value);
        return 1;
    }

    private static int getSoundType(CommandContext<FabricClientCommandSource> ctx, SoundSettingsUtil.Category category) {
        sendLangFeedback(ctx.getSource(), "sound_type_status", category.key, SoundSettingsUtil.get(category).soundId());
        return 1;
    }

    private static int setSoundType(CommandContext<FabricClientCommandSource> ctx, SoundSettingsUtil.Category category) throws CommandSyntaxException {
        String value = StringArgumentType.getString(ctx, "sound");
        Identifier soundId = Identifier.tryParse(value);
        if (soundId == null || !Registries.SOUND_EVENT.containsId(soundId)) {
            throw createException("sound_unknown", value);
        }
        SoundSettingsUtil.setSound(category, soundId.toString());
        sendLangFeedback(ctx.getSource(), "sound_set", category.key, soundId.toString());
        return 1;
    }

    // ================================================================
    //  /cie storage ...  (страничный визуальный storage, 100 страниц по 54 слота)
    // ================================================================

    private static final SuggestionProvider<FabricClientCommandSource> STORAGE_PAGE_SUGGESTIONS =
            (ctx, builder) -> {
                List<String> names = new ArrayList<>();
                for (String entry : StoragePageUtil.listNonEmptyPages(getRegistries())) {
                    names.add(entry.substring(entry.indexOf(':') + 1));
                }
                return CommandSource.suggestMatching(names, builder);
            };

    private static LiteralArgumentBuilder<FabricClientCommandSource> storagePagesNode() {
        return ClientCommandManager.literal("storage")
                .executes(CIECommand::openStorageCurrent)
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .suggests(STORAGE_PAGE_SUGGESTIONS)
                        .executes(CIECommand::openStorageByName))
                .then(ClientCommandManager.literal("page")
                        .then(ClientCommandManager.literal("list").executes(CIECommand::storagePageList))
                        .then(ClientCommandManager.literal("open")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests(STORAGE_PAGE_SUGGESTIONS)
                                        .executes(CIECommand::openStorageByName)))
                        .then(ClientCommandManager.literal("clear")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests(STORAGE_PAGE_SUGGESTIONS)
                                        .executes(CIECommand::storagePageClearRequest)
                                        .then(ClientCommandManager.literal("confirm")
                                                .executes(CIECommand::storagePageClearConfirm))))
                        .then(ClientCommandManager.literal("rename")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests(STORAGE_PAGE_SUGGESTIONS)
                                        .then(ClientCommandManager.argument("newName", StringArgumentType.greedyString())
                                                .executes(CIECommand::storagePageRename))))
                        .then(ClientCommandManager.literal("lock")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests(STORAGE_PAGE_SUGGESTIONS)
                                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(CIECommand::storagePageLock)))))
                .then(ClientCommandManager.literal("clear")
                        .executes(CIECommand::storageClearAllRequest)
                        .then(ClientCommandManager.literal("confirm").executes(CIECommand::storageClearAllConfirm)))
                .then(ClientCommandManager.literal("save")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .suggests(STORAGE_PAGE_SUGGESTIONS)
                                .executes(CIECommand::storagePageSave)));
    }

    private static void openStoragePage(FabricClientCommandSource source, int pageIndex) {
        // Тот же гочу-фикс, что и у mouseHistory/armorStand/pickColor:
        // ChatScreen сам закроется после выполнения команды и синхронный
        // client.execute(...) не спасает — очередь дренится ещё внутри
        // того же keyPressed(). openScreenNextTick откладывает открытие
        // на END_CLIENT_TICK, который срабатывает уже после закрытия чата.
        MinecraftClient client = MinecraftClient.getInstance();
        RegistryWrapper.WrapperLookup registries = getRegistries();
        openScreenNextTick(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) {
                return null;
            }
            StoragePageUtil.setCurrentPageIndex(pageIndex);
            StoragePageUtil.Page page = StoragePageUtil.loadPage(pageIndex, registries);
            com.cie.screen.StorageScreenHandler handler =
                    new com.cie.screen.StorageScreenHandler(player.getInventory(), pageIndex, page);
            return new com.cie.screen.StorageScreen(handler, player.getInventory(), registries);
        });
    }

    private static int openStorageCurrent(CommandContext<FabricClientCommandSource> ctx) {
        openStoragePage(ctx.getSource(), StoragePageUtil.getCurrentPageIndex());
        return 1;
    }

    private static int openStorageByName(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        int idx = StoragePageUtil.findPageIndexByName(name, getRegistries());
        if (idx == -1) {
            throw createException("storage_page_not_found", name);
        }
        openStoragePage(ctx.getSource(), idx);
        return 1;
    }

    private static int storagePageList(CommandContext<FabricClientCommandSource> ctx) {
        List<String> pages = StoragePageUtil.listNonEmptyPages(getRegistries());
        if (pages.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "storage_page_list_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "storage_page_list", String.join(", ", pages));
        return pages.size();
    }

    private static int storagePageClearRequest(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        if (StoragePageUtil.findPageIndexByName(name, getRegistries()) == -1) {
            throw createException("storage_page_not_found", name);
        }
        sendLangFeedback(ctx.getSource(), "storage_page_clear_confirm", name);
        return 1;
    }

    private static int storagePageClearConfirm(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        RegistryWrapper.WrapperLookup registries = getRegistries();
        int idx = StoragePageUtil.findPageIndexByName(name, registries);
        if (idx == -1) {
            throw createException("storage_page_not_found", name);
        }
        StoragePageUtil.clearPageItems(idx, registries);
        sendLangFeedback(ctx.getSource(), "storage_page_cleared", name);
        return 1;
    }

    private static int storagePageRename(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        String newName = StringArgumentType.getString(ctx, "newName");
        RegistryWrapper.WrapperLookup registries = getRegistries();
        int idx = StoragePageUtil.findPageIndexByName(name, registries);
        if (idx == -1) {
            throw createException("storage_page_not_found", name);
        }
        StoragePageUtil.renamePage(idx, newName, registries);
        sendLangFeedback(ctx.getSource(), "storage_page_renamed", name, newName);
        return 1;
    }

    private static int storagePageLock(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        boolean value = BoolArgumentType.getBool(ctx, "value");
        RegistryWrapper.WrapperLookup registries = getRegistries();
        int idx = StoragePageUtil.findPageIndexByName(name, registries);
        if (idx == -1) {
            throw createException("storage_page_not_found", name);
        }
        StoragePageUtil.setLocked(idx, value, registries);
        sendLangFeedback(ctx.getSource(), "storage_page_lock_set", name, value);
        return 1;
    }

    private static int storageClearAllRequest(CommandContext<FabricClientCommandSource> ctx) {
        sendLangFeedback(ctx.getSource(), "storage_clear_all_confirm");
        return 1;
    }

    private static int storageClearAllConfirm(CommandContext<FabricClientCommandSource> ctx) {
        StoragePageUtil.clearAllPages(getRegistries());
        sendLangFeedback(ctx.getSource(), "storage_clear_all_done");
        return 1;
    }

    private static int storagePageSave(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        String name = StringArgumentType.getString(ctx, "name");
        RegistryWrapper.WrapperLookup registries = getRegistries();
        int idx = StoragePageUtil.findPageIndexByName(name, registries);
        if (idx == -1) {
            throw createException("storage_page_not_found", name);
        }
        boolean ok = StoragePageUtil.saveItemToFirstFreeSlot(idx, stack, registries);
        if (!ok) {
            sendLangFeedback(ctx.getSource(), "storage_page_full", name);
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "storage_page_saved", name);
        return 1;
    }

    // ================================================================
    //  /cie repairable ...  (REPAIRABLE)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> repairableNode() {
        return ClientCommandManager.literal("repairable")
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearRepairable))
                .then(ClientCommandManager.literal("get").executes(CIECommand::getRepairable))
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("item", IdentifierArgumentType.identifier())
                                .suggests(ITEM_SUGGESTIONS)
                                .executes(CIECommand::addRepairable)))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("item", IdentifierArgumentType.identifier())
                                .suggests(ITEM_SUGGESTIONS)
                                .executes(CIECommand::removeRepairable)));
    }

    private static int clearRepairable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RepairableUtil.clear(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "repairable_cleared");
        return 1;
    }

    private static int getRepairable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        List<String> items = RepairableUtil.get(stack);
        if (items.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "repairable_empty");
        } else {
            for (int i = 0; i < items.size(); i++) {
                sendLangFeedback(ctx.getSource(), "repairable_entry", i + 1, items.get(i));
            }
        }
        return items.size();
    }

    private static int addRepairable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier itemId = ctx.getArgument("item", Identifier.class);
        RegistryEntry<Item> entry = resolveEntry(RegistryKeys.ITEM, itemId.toString());
        RepairableUtil.add(stack, entry);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "repairable_added", itemId.toString());
        return 1;
    }

    private static int removeRepairable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier itemId = ctx.getArgument("item", Identifier.class);
        RegistryEntry<Item> entry = resolveEntry(RegistryKeys.ITEM, itemId.toString());
        RepairableUtil.remove(stack, entry);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "repairable_removed", itemId.toString());
        return 1;
    }

    // ================================================================
    //  /cie repairCost ...  (REPAIR_COST)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> repairCostNode() {
        return ClientCommandManager.literal("repairCost")
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearRepairCost))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("cost", IntegerArgumentType.integer())
                                .executes(CIECommand::setRepairCost)))
                .then(ClientCommandManager.literal("get").executes(CIECommand::getRepairCost))
                .then(ClientCommandManager.literal("reset").executes(CIECommand::resetRepairCost));
    }

    private static int clearRepairCost(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RepairCostUtil.clear(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "repair_cost_cleared");
        return 1;
    }

    private static int setRepairCost(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int cost = IntegerArgumentType.getInteger(ctx, "cost");
        RepairCostUtil.set(stack, cost);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "repair_cost_set", cost);
        return 1;
    }

    private static int getRepairCost(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "repair_cost_status", RepairCostUtil.get(stack));
        return 1;
    }

    private static int resetRepairCost(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RepairCostUtil.reset(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "repair_cost_reset");
        return 1;
    }

    // ================================================================
    //  /cie swingAnimation ...  (кастомная анимация взмаха)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> swingAnimationNode() {
        return ClientCommandManager.literal("swingAnimation")
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearSwingAnimation))
                .then(ClientCommandManager.literal("animation")
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> CommandSource.suggestMatching(ANIMATION_SUGGESTIONS_LIST, builder))
                                        .executes(CIECommand::setSwingAnimationType)))
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getSwingAnimationType))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetSwingAnimationType)))
                .then(ClientCommandManager.literal("duration")
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("dur", IntegerArgumentType.integer(0))
                                        .executes(CIECommand::setSwingAnimationDuration)))
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getSwingAnimationDuration))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetSwingAnimationDuration)));
    }

    private static int clearSwingAnimation(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        SwingAnimationUtil.clear(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "swing_animation_cleared");
        return 1;
    }

    private static int setSwingAnimationType(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String type = StringArgumentType.getString(ctx, "type");
        SwingAnimationUtil.AnimationSub.set(stack, type);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "swing_animation_type_set", type);
        return 1;
    }

    private static int getSwingAnimationType(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "swing_animation_type_status", SwingAnimationUtil.AnimationSub.get(stack));
        return 1;
    }

    private static int resetSwingAnimationType(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        SwingAnimationUtil.AnimationSub.reset(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "swing_animation_type_reset");
        return 1;
    }

    private static int setSwingAnimationDuration(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int dur = IntegerArgumentType.getInteger(ctx, "dur");
        SwingAnimationUtil.DurationSub.set(stack, dur);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "swing_animation_duration_set", dur);
        return 1;
    }

    private static int getSwingAnimationDuration(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "swing_animation_duration_status", SwingAnimationUtil.DurationSub.get(stack));
        return 1;
    }

    private static int resetSwingAnimationDuration(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        SwingAnimationUtil.DurationSub.reset(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "swing_animation_duration_reset");
        return 1;
    }

    // ================================================================
    //  /cie rarity ...  (RARITY)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> rarityNode() {
        return ClientCommandManager.literal("rarity")
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearRarity))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("rar", StringArgumentType.word())
                                .suggests(RARITY_SUGGESTIONS)
                                .executes(CIECommand::setRarity)))
                .then(ClientCommandManager.literal("get").executes(CIECommand::getRarity));
    }

    private static int clearRarity(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        RarityUtil.clear(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "rarity_cleared");
        return 1;
    }

    private static int setRarity(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String rar = StringArgumentType.getString(ctx, "rar");
        RarityUtil.set(stack, rar);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "rarity_set", rar.toLowerCase(Locale.ROOT));
        return 1;
    }

    private static int getRarity(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "rarity_status", RarityUtil.get(stack));
        return 1;
    }

    // ================================================================
    //  /cie jukeboxPlayable ...  (JUKEBOX_PLAYABLE)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> jukeboxPlayableNode() {
        return ClientCommandManager.literal("jukeboxPlayable")
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                .suggests(JUKEBOX_SONG_SUGGESTIONS)
                                .executes(CIECommand::setJukeboxPlayable)))
                .then(ClientCommandManager.literal("get").executes(CIECommand::getJukeboxPlayable))
                .then(ClientCommandManager.literal("reset").executes(CIECommand::resetJukeboxPlayable));
    }

    private static int setJukeboxPlayable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier songId = ctx.getArgument("id", Identifier.class);
        JukeboxPlayableUtil.set(stack, songId.toString());

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "jukebox_playable_set", songId.toString());
        return 1;
    }

    private static int getJukeboxPlayable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "jukebox_playable_status", JukeboxPlayableUtil.get(stack));
        return 1;
    }

    private static int resetJukeboxPlayable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        JukeboxPlayableUtil.reset(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "jukebox_playable_reset");
        return 1;
    }

    // ================================================================
    //  /cie damageResistant ...  (DAMAGE_RESISTANT)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> damageResistantNode() {
        return ClientCommandManager.literal("damageResistant")
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearDamageResistant))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("type", IdentifierArgumentType.identifier())
                                .suggests(DAMAGE_TYPE_TAG_SUGGESTIONS)
                                .executes(CIECommand::setDamageResistant)))
                .then(ClientCommandManager.literal("get").executes(CIECommand::getDamageResistant))
                .then(ClientCommandManager.literal("reset").executes(CIECommand::clearDamageResistant));
    }

    private static int clearDamageResistant(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        DamageResistantUtil.clear(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "damage_resistant_cleared");
        return 1;
    }

    private static int setDamageResistant(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String type = ctx.getArgument("type", Identifier.class).toString();
        DamageResistantUtil.set(stack, type);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "damage_resistant_set", type);
        return 1;
    }

    private static int getDamageResistant(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "damage_resistant_status", DamageResistantUtil.get(stack));
        return 1;
    }


    // ================================================================
    //  /cie edit villagerData ...  (ENTITY_DATA — тот же механизм, что и
    //  entitySettingsNode, только villager-специфичные поля, см.
    //  VillagerDataUtil)
    // ================================================================

    private static final SuggestionProvider<FabricClientCommandSource> VILLAGER_PROFESSION_SUGGESTIONS =
            (ctx, builder) -> {
                for (Identifier id : Registries.VILLAGER_PROFESSION.getIds()) {
                    builder.suggest(id.toString());
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<FabricClientCommandSource> VILLAGER_BIOME_SUGGESTIONS =
            (ctx, builder) -> {
                for (Identifier id : Registries.VILLAGER_TYPE.getIds()) {
                    builder.suggest(id.toString());
                }
                return builder.buildFuture();
            };

    private static LiteralArgumentBuilder<FabricClientCommandSource> villagerDataNode() {
        return ClientCommandManager.literal("villagerData")
                .then(ClientCommandManager.literal("profession")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getVillagerProfession))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("prof", StringArgumentType.word())
                                        .suggests(VILLAGER_PROFESSION_SUGGESTIONS)
                                        .executes(CIECommand::setVillagerProfession)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetVillagerProfession)))
                .then(ClientCommandManager.literal("biome")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getVillagerBiome))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("biome", StringArgumentType.word())
                                        .suggests(VILLAGER_BIOME_SUGGESTIONS)
                                        .executes(CIECommand::setVillagerBiome)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetVillagerBiome)))
                .then(ClientCommandManager.literal("level")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getVillagerLevel))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("level", IntegerArgumentType.integer(1, 5))
                                        .executes(CIECommand::setVillagerLevel)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetVillagerLevel)))
                .then(ClientCommandManager.literal("willing")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getVillagerWilling))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(CIECommand::setVillagerWilling))))
                .then(ClientCommandManager.literal("lastRestock")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getVillagerLastRestock))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("last", LongArgumentType.longArg())
                                        .executes(CIECommand::setVillagerLastRestock)))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetVillagerLastRestock)))
                .then(villagerTradesNode());
    }

    // -- profession --

    private static int getVillagerProfession(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "villager_profession_status", VillagerDataUtil.getProfession(stack));
        return 1;
    }

    private static int setVillagerProfession(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String prof = StringArgumentType.getString(ctx, "prof");
        VillagerDataUtil.setProfession(stack, prof);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_profession_set", prof);
        return 1;
    }

    private static int resetVillagerProfession(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        VillagerDataUtil.resetProfession(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_profession_reset");
        return 1;
    }

    // -- biome --

    private static int getVillagerBiome(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "villager_biome_status", VillagerDataUtil.getBiome(stack));
        return 1;
    }

    private static int setVillagerBiome(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        String biome = StringArgumentType.getString(ctx, "biome");
        VillagerDataUtil.setBiome(stack, biome);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_biome_set", biome);
        return 1;
    }

    private static int resetVillagerBiome(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        VillagerDataUtil.resetBiome(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_biome_reset");
        return 1;
    }

    // -- level --

    private static int getVillagerLevel(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "villager_level_status", VillagerDataUtil.getLevel(stack));
        return 1;
    }

    private static int setVillagerLevel(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int level = IntegerArgumentType.getInteger(ctx, "level");
        VillagerDataUtil.setLevel(stack, level);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_level_set", level);
        return 1;
    }

    private static int resetVillagerLevel(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        VillagerDataUtil.resetLevel(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_level_reset");
        return 1;
    }

    // -- willing --

    private static int getVillagerWilling(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "villager_willing_status", VillagerDataUtil.getWilling(stack));
        return 1;
    }

    private static int setVillagerWilling(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        boolean value = BoolArgumentType.getBool(ctx, "value");
        VillagerDataUtil.setWilling(stack, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_willing_set", value);
        return 1;
    }

    // -- lastRestock --

    private static int getVillagerLastRestock(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "villager_last_restock_status", VillagerDataUtil.getLastRestock(stack));
        return 1;
    }

    private static int setVillagerLastRestock(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        long value = LongArgumentType.getLong(ctx, "last");
        VillagerDataUtil.setLastRestock(stack, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_last_restock_set", value);
        return 1;
    }

    private static int resetVillagerLastRestock(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        VillagerDataUtil.resetLastRestock(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_last_restock_reset");
        return 1;
    }

    // ================================================================
    //  villagerData trades (Offers.Recipes)
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> villagerTradesNode() {
        return ClientCommandManager.literal("trades")
                .then(ClientCommandManager.literal("list").executes(CIECommand::listVillagerTrades))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearVillagerTrades))
                .then(ClientCommandManager.literal("create")
                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                .executes(CIECommand::createVillagerTrade)))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                .executes(CIECommand::removeVillagerTrade)))
                .then(ClientCommandManager.literal("get")
                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                .executes(CIECommand::getVillagerTrade)))
                .then(ClientCommandManager.literal("edit")
                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                                .then(ClientCommandManager.literal("rewardExp")
                                        .then(ClientCommandManager.literal("get").executes(CIECommand::getTradeRewardExp))
                                        .then(ClientCommandManager.literal("set")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(CIECommand::setTradeRewardExp))))
                                .then(villagerTradeIntFieldNode("maxUses",
                                        VillagerDataUtil::getMaxUses, VillagerDataUtil::setMaxUses, VillagerDataUtil::resetMaxUses))
                                .then(villagerTradeIntFieldNode("uses",
                                        VillagerDataUtil::getUses, VillagerDataUtil::setUses, VillagerDataUtil::resetUses))
                                .then(villagerTradeIntFieldNode("xp",
                                        VillagerDataUtil::getXp, VillagerDataUtil::setXp, VillagerDataUtil::resetXp))
                                .then(villagerTradeIntFieldNode("priceMultiplier",
                                        VillagerDataUtil::getPriceMultiplier, VillagerDataUtil::setPriceMultiplier, VillagerDataUtil::resetPriceMultiplier))
                                .then(villagerTradeIntFieldNode("specialPrice",
                                        VillagerDataUtil::getSpecialPrice, VillagerDataUtil::setSpecialPrice, VillagerDataUtil::resetSpecialPrice))
                                .then(villagerTradeIntFieldNode("demand",
                                        VillagerDataUtil::getDemand, VillagerDataUtil::setDemand, VillagerDataUtil::resetDemand))
                                .then(villagerTradeItemsNode())));
    }

    /** Общий билдер для числовых полей сделки (maxUses/uses/xp/priceMultiplier/specialPrice/demand) — id читается из внешнего аргумента "id" в момент исполнения. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> villagerTradeIntFieldNode(
            String literalName,
            java.util.function.BiFunction<ItemStack, Integer, Integer> getter,
            TriConsumer<ItemStack, Integer, Integer> setter,
            java.util.function.BiConsumer<ItemStack, Integer> resetter) {
        return ClientCommandManager.literal(literalName)
                .then(ClientCommandManager.literal("get").executes(ctx -> getTradeIntField(ctx, literalName, getter)))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("value", IntegerArgumentType.integer())
                                .executes(ctx -> setTradeIntField(ctx, literalName, setter))))
                .then(ClientCommandManager.literal("reset").executes(ctx -> resetTradeIntField(ctx, literalName, resetter)));
    }

    @FunctionalInterface
    private interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    private static int getTradeIntField(CommandContext<FabricClientCommandSource> ctx, String literalName,
                                        java.util.function.BiFunction<ItemStack, Integer, Integer> getter) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));
        sendLangFeedback(ctx.getSource(), "villager_trade_field_status", literalName, id, getter.apply(stack, id));
        return 1;
    }

    private static int setTradeIntField(CommandContext<FabricClientCommandSource> ctx, String literalName,
                                        TriConsumer<ItemStack, Integer, Integer> setter) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));
        UndoUtil.pushSnapshot(player, stack);

        int value = IntegerArgumentType.getInteger(ctx, "value");
        setter.accept(stack, id, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_trade_field_set", literalName, id, value);
        return 1;
    }

    private static int resetTradeIntField(CommandContext<FabricClientCommandSource> ctx, String literalName,
                                          java.util.function.BiConsumer<ItemStack, Integer> resetter) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));
        UndoUtil.pushSnapshot(player, stack);

        resetter.accept(stack, id);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_trade_field_reset", literalName, id);
        return 1;
    }

    // -- trades: list/clear/create/remove/get --

    private static int listVillagerTrades(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        int count = VillagerDataUtil.tradeCount(stack);
        if (count == 0) {
            sendLangFeedback(ctx.getSource(), "villager_trades_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "villager_trades_count", count);
        return count;
    }

    private static int clearVillagerTrades(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        VillagerDataUtil.clearTrades(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_trades_cleared");
        return 1;
    }

    private static int createVillagerTrade(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        int id = IntegerArgumentType.getInteger(ctx, "id");
        int maxInsertPos = VillagerDataUtil.tradeCount(stack) + 1;
        if (id < 1 || id > maxInsertPos) {
            throw createException("index_out_of_bounds", maxInsertPos);
        }
        UndoUtil.pushSnapshot(player, stack);

        VillagerDataUtil.createTrade(stack, id, getRegistries());
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_trade_created", id);
        return 1;
    }

    private static int removeVillagerTrade(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));
        UndoUtil.pushSnapshot(player, stack);

        VillagerDataUtil.removeTrade(stack, id);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_trade_removed", id);
        return 1;
    }

    /** trades get <id> — полный SNBT сделки (компонент+настройки), не привязан к отдельным лору-полям. */
    private static int getVillagerTrade(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));

        NbtCompound trade = VillagerDataUtil.getTrade(stack, id - 1);
        StringNbtWriter writer = new StringNbtWriter();
        trade.accept(writer);
        sendCopyableRaw(ctx.getSource(), writer.toString());
        return 1;
    }

    // -- trades: items (buy/buyB/sell) --

    private static LiteralArgumentBuilder<FabricClientCommandSource> villagerTradeItemsNode() {
        return ClientCommandManager.literal("items")
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearTradeItems))
                .then(tradeItemSlotNode("buy"))
                .then(tradeItemSlotNode("buyB"))
                .then(tradeItemSlotNode("sell"));
    }

    private static int clearTradeItems(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));
        UndoUtil.pushSnapshot(player, stack);

        VillagerDataUtil.clearTradeItems(stack, id, getRegistries());
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_trade_items_cleared", id);
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> tradeItemSlotNode(String field) {
        return ClientCommandManager.literal(field)
                .then(ClientCommandManager.literal("get")
                        .executes(ctx -> getTradeItem(ctx, field)))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.literal("fromStorage")
                                .executes(ctx -> setTradeItemFromStorage(ctx, field)))
                        .then(ClientCommandManager.literal("fromOffHand")
                                .executes(ctx -> setTradeItemFromOffHand(ctx, field, -1))
                                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> setTradeItemFromOffHand(ctx, field, IntegerArgumentType.getInteger(ctx, "count")))))
                        .then(ClientCommandManager.literal("new")
                                .then(ClientCommandManager.argument("item", IdentifierArgumentType.identifier())
                                        .suggests(ITEM_SUGGESTIONS)
                                        .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> setTradeItemNew(ctx, field, ""))
                                                .then(ClientCommandManager.argument("components", StringArgumentType.greedyString())
                                                        .executes(ctx -> setTradeItemNew(ctx, field,
                                                                StringArgumentType.getString(ctx, "components"))))))))
                .then(ClientCommandManager.literal("remove").executes(ctx -> removeTradeItem(ctx, field)));
    }

    private static int getTradeItem(CommandContext<FabricClientCommandSource> ctx, String field) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));

        ItemStack item = VillagerDataUtil.getTradeItem(stack, id, field, getRegistries());
        if (item.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "villager_trade_item_empty", field, id);
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "villager_trade_item_status", field, id,
                Registries.ITEM.getId(item.getItem()).toString(), item.getCount());
        return 1;
    }

    /**
     * Клик-пикер — тот же паттерн, что и setEquipmentFromStorage: слот руки
     * фиксируется заранее (selectedSlot), само присвоение выполняется
     * асинхронно в момент клика по предмету в /cie storage.
     */
    private static int setTradeItemFromStorage(CommandContext<FabricClientCommandSource> ctx, String field) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));
        UndoUtil.pushSnapshot(player, stack);

        RegistryWrapper.WrapperLookup registries = getRegistries();
        int selectedSlot = player.getInventory().selectedSlot;

        StoragePicker.requestPick(picked -> {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p == null) {
                return;
            }
            ItemStack egg = p.getInventory().getStack(selectedSlot);
            if (egg.isEmpty()) {
                return;
            }
            VillagerDataUtil.setTradeItem(egg, id, field, picked, registries);
            syncSlot(p, selectedSlot, egg);
        });

        openStoragePage(ctx.getSource(), StoragePageUtil.getCurrentPageIndex());
        sendLangFeedback(ctx.getSource(), "villager_trade_item_pick_prompt", field, id);
        return 1;
    }

    private static int setTradeItemFromOffHand(CommandContext<FabricClientCommandSource> ctx, String field, int countOverride) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));

        ItemStack offHand = player.getOffHandStack();
        if (offHand.isEmpty()) {
            throw createException("entity_equipment_offhand_empty");
        }
        UndoUtil.pushSnapshot(player, stack);
        ItemStack toSet = offHand.copy();
        if (countOverride > 0) {
            toSet.setCount(countOverride);
        }
        VillagerDataUtil.setTradeItem(stack, id, field, toSet, getRegistries());
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_trade_item_set_offhand", field, id);
        return 1;
    }

    private static int setTradeItemNew(CommandContext<FabricClientCommandSource> ctx, String field, String componentsSnbt) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));

        Identifier itemId = ctx.getArgument("item", Identifier.class);
        Item newItem = Registries.ITEM.get(itemId);
        int count = IntegerArgumentType.getInteger(ctx, "count");

        ItemStack toSet;
        try {
            toSet = UseRemainderUtil.buildRemainderStack(newItem, count, componentsSnbt, getRegistries());
        } catch (Exception e) {
            sendLangFeedback(ctx.getSource(), "component_parse_error", e.getMessage());
            return 0;
        }

        UndoUtil.pushSnapshot(player, stack);
        VillagerDataUtil.setTradeItem(stack, id, field, toSet, getRegistries());
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_trade_item_set", field, id, itemId.toString());
        return 1;
    }

    private static int removeTradeItem(CommandContext<FabricClientCommandSource> ctx, String field) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));
        UndoUtil.pushSnapshot(player, stack);

        VillagerDataUtil.removeTradeItem(stack, id, field, getRegistries());
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_trade_item_removed", field, id);
        return 1;
    }

    // -- rewardExp (единственное bool-поле сделки) --

    private static int getTradeRewardExp(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));
        sendLangFeedback(ctx.getSource(), "villager_trade_field_status", "rewardExp", id, VillagerDataUtil.getRewardExp(stack, id));
        return 1;
    }

    private static int setTradeRewardExp(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        int id = IntegerArgumentType.getInteger(ctx, "id");
        checkIndex(id, VillagerDataUtil.tradeCount(stack));
        UndoUtil.pushSnapshot(player, stack);

        boolean value = BoolArgumentType.getBool(ctx, "value");
        VillagerDataUtil.setRewardExp(stack, id, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "villager_trade_field_set", "rewardExp", id, value);
        return 1;
    }

    // ================================================================    //  /cie edit villagerData ...  (ENTITY_DATA — тот же механизм, что и    //  entitySettingsNode, только villager-специфичные поля, см.    //  VillagerDataUtil)    // ================================================================     private static final SuggestionProvider<FabricClientCommandSource> VILLAGER_PROFESSION_SUGGESTIONS =            (ctx, builder) -> {                for (Identifier id : Registries.VILLAGER_PROFESSION.getIds()) {                    builder.suggest(id.toString());                }                return builder.buildFuture();            };     private static final SuggestionProvider<FabricClientCommandSource> VILLAGER_BIOME_SUGGESTIONS =            (ctx, builder) -> {                for (Identifier id : Registries.VILLAGER_TYPE.getIds()) {                    builder.suggest(id.toString());                }                return builder.buildFuture();            };     private static LiteralArgumentBuilder<FabricClientCommandSource> villagerDataNode() {        return ClientCommandManager.literal("villagerData")                .then(ClientCommandManager.literal("profession")                        .then(ClientCommandManager.literal("get").executes(CIECommand::getVillagerProfession))                        .then(ClientCommandManager.literal("set")                                .then(ClientCommandManager.argument("prof", StringArgumentType.word())                                        .suggests(VILLAGER_PROFESSION_SUGGESTIONS)                                        .executes(CIECommand::setVillagerProfession)))                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetVillagerProfession)))                .then(ClientCommandManager.literal("biome")                        .then(ClientCommandManager.literal("get").executes(CIECommand::getVillagerBiome))                        .then(ClientCommandManager.literal("set")                                .then(ClientCommandManager.argument("biome", StringArgumentType.word())                                        .suggests(VILLAGER_BIOME_SUGGESTIONS)                                        .executes(CIECommand::setVillagerBiome)))                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetVillagerBiome)))                .then(ClientCommandManager.literal("level")                        .then(ClientCommandManager.literal("get").executes(CIECommand::getVillagerLevel))                        .then(ClientCommandManager.literal("set")                                .then(ClientCommandManager.argument("level", IntegerArgumentType.integer(1, 5))                                        .executes(CIECommand::setVillagerLevel)))                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetVillagerLevel)))                .then(ClientCommandManager.literal("willing")                        .then(ClientCommandManager.literal("get").executes(CIECommand::getVillagerWilling))                        .then(ClientCommandManager.literal("set")                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())                                        .executes(CIECommand::setVillagerWilling))))                .then(ClientCommandManager.literal("lastRestock")                        .then(ClientCommandManager.literal("get").executes(CIECommand::getVillagerLastRestock))                        .then(ClientCommandManager.literal("set")                                .then(ClientCommandManager.argument("last", LongArgumentType.longArg())                                        .executes(CIECommand::setVillagerLastRestock)))                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetVillagerLastRestock)))

    // ================================================================
    //  /cie edit armorStand ...  (ENTITY_DATA — тот же механизм, что и
    //  entitySettingsNode/villagerDataNode, только armor_stand-специфичные
    //  поля: флаги, поза шести частей и пресеты поз. См. ArmorStandDataUtil.
    //  Экипировка (голова/грудь/ноги/ботинки/руки) НЕ дублируется тут
    //  отдельными командами — она уже полностью доступна через
    //  /cie edit EntitySettings equipment <slot> ..., та же ENTITY_DATA,
    //  и используется напрямую из /cie edit armorStand menu.
    // ================================================================

    private static final SuggestionProvider<FabricClientCommandSource> ARMOR_STAND_PRESET_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(ArmorStandDataUtil.presetNames(), builder);

    private static LiteralArgumentBuilder<FabricClientCommandSource> armorStandNode() {
        return ClientCommandManager.literal("armorStand")
                .then(ClientCommandManager.literal("menu").executes(CIECommand::openArmorStandMenu))
                .then(armorStandFlagNode("noBasePlate", "NoBasePlate"))
                .then(armorStandFlagNode("small", "Small"))
                .then(armorStandFlagNode("showArms", "ShowArms"))
                .then(armorStandFlagNode("invisible", "Invisible"))
                .then(armorStandFlagNode("marker", "Marker"))
                .then(ClientCommandManager.literal("pose")
                        .then(armorStandPosePartNode("head", ArmorStandDataUtil.Part.HEAD))
                        .then(armorStandPosePartNode("body", ArmorStandDataUtil.Part.BODY))
                        .then(armorStandPosePartNode("leftArm", ArmorStandDataUtil.Part.LEFT_ARM))
                        .then(armorStandPosePartNode("rightArm", ArmorStandDataUtil.Part.RIGHT_ARM))
                        .then(armorStandPosePartNode("leftLeg", ArmorStandDataUtil.Part.LEFT_LEG))
                        .then(armorStandPosePartNode("rightLeg", ArmorStandDataUtil.Part.RIGHT_LEG))
                        .then(ClientCommandManager.literal("preset")
                                .then(ClientCommandManager.literal("list").executes(CIECommand::listArmorStandPresets))
                                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearArmorStandPresets))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("preset", StringArgumentType.word())
                                                .suggests(ARMOR_STAND_PRESET_SUGGESTIONS)
                                                .executes(CIECommand::removeArmorStandPreset)))
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager.argument("preset", StringArgumentType.word())
                                                .executes(CIECommand::addArmorStandPreset)))
                                .then(ClientCommandManager.literal("apply")
                                        .then(ClientCommandManager.argument("preset", StringArgumentType.word())
                                                .suggests(ARMOR_STAND_PRESET_SUGGESTIONS)
                                                .executes(CIECommand::applyArmorStandPreset)))));
    }

    // -- flags (noBasePlate/small/showArms/invisible/marker) --

    private static LiteralArgumentBuilder<FabricClientCommandSource> armorStandFlagNode(String literalName, String nbtKey) {
        return ClientCommandManager.literal(literalName)
                .then(ClientCommandManager.literal("get").executes(ctx -> getArmorStandFlag(ctx, literalName, nbtKey)))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setArmorStandFlag(ctx, literalName, nbtKey))))
                .then(ClientCommandManager.literal("reset").executes(ctx -> resetArmorStandFlag(ctx, literalName, nbtKey)));
    }

    private static int getArmorStandFlag(CommandContext<FabricClientCommandSource> ctx, String literalName, String nbtKey) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "entity_flag_status", literalName, ArmorStandDataUtil.getFlag(stack, nbtKey));
        return 1;
    }

    private static int setArmorStandFlag(CommandContext<FabricClientCommandSource> ctx, String literalName, String nbtKey) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        boolean value = BoolArgumentType.getBool(ctx, "value");
        ArmorStandDataUtil.setFlag(stack, nbtKey, value);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_flag_set", literalName, value);
        return 1;
    }

    private static int resetArmorStandFlag(CommandContext<FabricClientCommandSource> ctx, String literalName, String nbtKey) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        ArmorStandDataUtil.setFlag(stack, nbtKey, false);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "entity_flag_reset", literalName);
        return 1;
    }

    // -- pose: <part> get | <part> <axis> get|set|reset --

    private static LiteralArgumentBuilder<FabricClientCommandSource> armorStandPosePartNode(String literalName, ArmorStandDataUtil.Part part) {
        return ClientCommandManager.literal(literalName)
                .then(ClientCommandManager.literal("get").executes(ctx -> getArmorStandPoseAll(ctx, literalName, part)))
                .then(armorStandPoseAxisNode("x", literalName, part, ArmorStandDataUtil.Axis.X))
                .then(armorStandPoseAxisNode("y", literalName, part, ArmorStandDataUtil.Axis.Y))
                .then(armorStandPoseAxisNode("z", literalName, part, ArmorStandDataUtil.Axis.Z));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> armorStandPoseAxisNode(
            String axisLiteral, String partLiteral, ArmorStandDataUtil.Part part, ArmorStandDataUtil.Axis axis) {
        return ClientCommandManager.literal(axisLiteral)
                .then(ClientCommandManager.literal("get").executes(ctx -> getArmorStandPoseAxis(ctx, partLiteral, axisLiteral, part, axis)))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("amt", FloatArgumentType.floatArg())
                                .executes(ctx -> setArmorStandPoseAxis(ctx, partLiteral, axisLiteral, part, axis))))
                .then(ClientCommandManager.literal("reset").executes(ctx -> resetArmorStandPoseAxis(ctx, partLiteral, axisLiteral, part, axis)));
    }

    private static int getArmorStandPoseAll(CommandContext<FabricClientCommandSource> ctx, String partLiteral, ArmorStandDataUtil.Part part) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        float[] v = ArmorStandDataUtil.getPoseAll(stack, part);
        sendLangFeedback(ctx.getSource(), "armorstand_pose_all_status", partLiteral, v[0], v[1], v[2]);
        return 1;
    }

    private static int getArmorStandPoseAxis(CommandContext<FabricClientCommandSource> ctx, String partLiteral, String axisLiteral,
                                             ArmorStandDataUtil.Part part, ArmorStandDataUtil.Axis axis) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "armorstand_pose_status", partLiteral, axisLiteral, ArmorStandDataUtil.getPoseAxis(stack, part, axis));
        return 1;
    }

    private static int setArmorStandPoseAxis(CommandContext<FabricClientCommandSource> ctx, String partLiteral, String axisLiteral,
                                             ArmorStandDataUtil.Part part, ArmorStandDataUtil.Axis axis) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        float amt = FloatArgumentType.getFloat(ctx, "amt");
        ArmorStandDataUtil.setPoseAxis(stack, part, axis, amt);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "armorstand_pose_set", partLiteral, axisLiteral, amt);
        return 1;
    }

    private static int resetArmorStandPoseAxis(CommandContext<FabricClientCommandSource> ctx, String partLiteral, String axisLiteral,
                                               ArmorStandDataUtil.Part part, ArmorStandDataUtil.Axis axis) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        ArmorStandDataUtil.resetPoseAxis(stack, part, axis);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "armorstand_pose_reset", partLiteral, axisLiteral);
        return 1;
    }

    // -- pose presets --

    private static int listArmorStandPresets(CommandContext<FabricClientCommandSource> ctx) {
        List<String> names = ArmorStandDataUtil.presetNames();
        if (names.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "armorstand_preset_list_empty");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "armorstand_preset_list", String.join(", ", names));
        return 1;
    }

    private static int clearArmorStandPresets(CommandContext<FabricClientCommandSource> ctx) {
        int count = ArmorStandDataUtil.presetNames().size();
        ArmorStandDataUtil.clearPresets();
        sendLangFeedback(ctx.getSource(), "armorstand_preset_cleared", count);
        return 1;
    }

    private static int removeArmorStandPreset(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "preset");
        if (!ArmorStandDataUtil.removePreset(name)) {
            throw createException("armorstand_preset_unknown", name);
        }
        sendLangFeedback(ctx.getSource(), "armorstand_preset_removed", name);
        return 1;
    }

    private static int addArmorStandPreset(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        String name = StringArgumentType.getString(ctx, "preset");
        ArmorStandDataUtil.addPreset(name, stack);
        sendLangFeedback(ctx.getSource(), "armorstand_preset_created", name);
        return 1;
    }

    private static int applyArmorStandPreset(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        String name = StringArgumentType.getString(ctx, "preset");
        UndoUtil.pushSnapshot(player, stack);

        if (!ArmorStandDataUtil.applyPreset(stack, name)) {
            throw createException("armorstand_preset_unknown", name);
        }
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "armorstand_preset_applied", name);
        return 1;
    }

    // -- menu (кастомный неванильный экран, см. com.cie.screen.ArmorStandEditScreen) --

    private static int openArmorStandMenu(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        // В отличие от остальных команд armorStand (которые, как и
        // entitySettingsNode/villagerDataNode, работают с ENTITY_DATA любого
        // предмета в руке), меню жёстко требует именно armor_stand — превью
        // в нём рендерит реальную ArmorStandEntity и рисует paperdoll её
        // слотов, так что для любого другого предмета оно просто не имеет смысла.
        if (stack.isEmpty() || stack.getItem() != net.minecraft.item.Items.ARMOR_STAND) {
            throw createException("armorstand_not_held");
        }

        RegistryWrapper.WrapperLookup registries = getRegistries();
        int selectedSlot = player.getInventory().selectedSlot;
        ItemStack stackCopy = stack.copy();
        openScreenNextTick(() -> new com.cie.screen.armorStandEditScreen(stackCopy, selectedSlot, registries));
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> spawnerNode() {
        return ClientCommandManager.literal("spawner")
                .then(spawnerShortField("spawnCount", SpawnerBlockEntityUtil.DEFAULT_SPAWN_COUNT,
                        SpawnerBlockEntityUtil::getSpawnCount, SpawnerBlockEntityUtil::setSpawnCount, SpawnerBlockEntityUtil::resetSpawnCount))
                .then(spawnerShortField("spawnRange", SpawnerBlockEntityUtil.DEFAULT_SPAWN_RANGE,
                        SpawnerBlockEntityUtil::getSpawnRange, SpawnerBlockEntityUtil::setSpawnRange, SpawnerBlockEntityUtil::resetSpawnRange))
                .then(spawnerShortField("delay", SpawnerBlockEntityUtil.DEFAULT_DELAY,
                        SpawnerBlockEntityUtil::getDelay, SpawnerBlockEntityUtil::setDelay, SpawnerBlockEntityUtil::resetDelay))
                .then(spawnerShortField("minSpawnDelay", SpawnerBlockEntityUtil.DEFAULT_MIN_SPAWN_DELAY,
                        SpawnerBlockEntityUtil::getMinSpawnDelay, SpawnerBlockEntityUtil::setMinSpawnDelay, SpawnerBlockEntityUtil::resetMinSpawnDelay))
                .then(spawnerShortField("maxSpawnDelay", SpawnerBlockEntityUtil.DEFAULT_MAX_SPAWN_DELAY,
                        SpawnerBlockEntityUtil::getMaxSpawnDelay, SpawnerBlockEntityUtil::setMaxSpawnDelay, SpawnerBlockEntityUtil::resetMaxSpawnDelay))
                .then(spawnerShortField("maxNearbyEntities", SpawnerBlockEntityUtil.DEFAULT_MAX_NEARBY_ENTITIES,
                        SpawnerBlockEntityUtil::getMaxNearbyEntities, SpawnerBlockEntityUtil::setMaxNearbyEntities, SpawnerBlockEntityUtil::resetMaxNearbyEntities))
                .then(spawnerShortField("requiredPlayerRange", SpawnerBlockEntityUtil.DEFAULT_REQUIRED_PLAYER_RANGE,
                        SpawnerBlockEntityUtil::getRequiredPlayerRange, SpawnerBlockEntityUtil::setRequiredPlayerRange, SpawnerBlockEntityUtil::resetRequiredPlayerRange))
                .then(ClientCommandManager.literal("spawnPotential")
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("entity", StringArgumentType.word())
                                        .suggests(ENTITY_TYPE_SUGGESTIONS)
                                        .executes(CIECommand::addSpawnPotentialNoData)
                                        .then(ClientCommandManager.argument("entity_data", StringArgumentType.greedyString())
                                                .executes(CIECommand::addSpawnPotentialWithData))
                                        .then(ClientCommandManager.literal("weight")
                                                .then(ClientCommandManager.argument("weight", IntegerArgumentType.integer(1))
                                                        .executes(CIECommand::addSpawnPotentialWeightOnly)
                                                        .then(ClientCommandManager.argument("entity_data", StringArgumentType.greedyString())
                                                                .executes(CIECommand::addSpawnPotentialWeightAndData))))))
                        .then(ClientCommandManager.literal("list").executes(CIECommand::listSpawnPotentials))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("entity", StringArgumentType.word())
                                        .suggests(ENTITY_TYPE_SUGGESTIONS)
                                        .executes(CIECommand::removeSpawnPotential)))
                        .then(ClientCommandManager.literal("clear").executes(CIECommand::clearSpawnPotentials)))
                .then(ClientCommandManager.literal("spawnData")
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("entity", StringArgumentType.word())
                                        .suggests(ENTITY_TYPE_SUGGESTIONS)
                                        .executes(CIECommand::setSpawnDataNoExtra)
                                        .then(ClientCommandManager.argument("entity_data", StringArgumentType.greedyString())
                                                .executes(CIECommand::setSpawnDataWithExtra))))
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getSpawnData))
                        .then(ClientCommandManager.literal("reset").executes(CIECommand::resetSpawnData)));
    }

    /** Общий under-node get/set/reset для короткого числового поля спавнера (все хранятся как short). */
    private static LiteralArgumentBuilder<FabricClientCommandSource> spawnerShortField(
            String literalName, int defaultValue,
            java.util.function.Function<ItemStack, Integer> getter,
            java.util.function.BiConsumer<ItemStack, Integer> setter,
            java.util.function.Consumer<ItemStack> resetter) {
        return ClientCommandManager.literal(literalName)
                .then(ClientCommandManager.literal("get").executes(ctx -> getSpawnerField(ctx, literalName, getter)))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(0, Short.MAX_VALUE))
                                .executes(ctx -> setSpawnerField(ctx, literalName, setter))))
                .then(ClientCommandManager.literal("reset").executes(ctx -> resetSpawnerField(ctx, literalName, resetter)));
    }

    private static int getSpawnerField(CommandContext<FabricClientCommandSource> ctx, String literalName,
                                       java.util.function.Function<ItemStack, Integer> getter) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        sendLangFeedback(ctx.getSource(), "spawner_field_status", literalName, getter.apply(stack));
        return 1;
    }

    private static int setSpawnerField(CommandContext<FabricClientCommandSource> ctx, String literalName,
                                       java.util.function.BiConsumer<ItemStack, Integer> setter) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int value = IntegerArgumentType.getInteger(ctx, "value");
        setter.accept(stack, value);

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "spawner_field_set", literalName, value);
        return 1;
    }

    private static int resetSpawnerField(CommandContext<FabricClientCommandSource> ctx, String literalName,
                                         java.util.function.Consumer<ItemStack> resetter) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        resetter.accept(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "spawner_field_reset", literalName);
        return 1;
    }

    private static Identifier requireEntityId(CommandContext<FabricClientCommandSource> ctx, String argName) throws CommandSyntaxException {
        String idStr = StringArgumentType.getString(ctx, argName);
        Identifier id = Identifier.tryParse(idStr);
        if (id == null || !Registries.ENTITY_TYPE.containsId(id)) {
            throw createException("entity_unknown_type", idStr);
        }
        return id;
    }

    // -- spawnPotential --

    private static int addSpawnPotentialNoData(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return addSpawnPotential(ctx, null, 1);
    }

    private static int addSpawnPotentialWithData(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return addSpawnPotential(ctx, StringArgumentType.getString(ctx, "entity_data"), 1);
    }

    private static int addSpawnPotentialWeightOnly(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return addSpawnPotential(ctx, null, IntegerArgumentType.getInteger(ctx, "weight"));
    }

    private static int addSpawnPotentialWeightAndData(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return addSpawnPotential(ctx, StringArgumentType.getString(ctx, "entity_data"), IntegerArgumentType.getInteger(ctx, "weight"));
    }

    private static int addSpawnPotential(CommandContext<FabricClientCommandSource> ctx, String entityDataSnbt, int weight) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier entityId = requireEntityId(ctx, "entity");
        try {
            SpawnerBlockEntityUtil.addSpawnPotential(stack, entityId, entityDataSnbt, weight);
        } catch (Exception e) {
            throw createException("bad_nbt", e.getMessage());
        }

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "spawner_potential_added", entityId.toString(), weight);
        return 1;
    }

    private static int listSpawnPotentials(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        List<String> entries = SpawnerBlockEntityUtil.listSpawnPotentials(stack);
        if (entries.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "spawner_potential_empty");
        } else {
            sendLangFeedback(ctx.getSource(), "spawner_potential_list", String.join(", ", entries));
        }
        return 1;
    }

    private static int removeSpawnPotential(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier entityId = requireEntityId(ctx, "entity");
        int removed = SpawnerBlockEntityUtil.removeSpawnPotential(stack, entityId);

        syncHandItem(player, stack);
        if (removed == 0) {
            sendLangFeedback(ctx.getSource(), "spawner_potential_not_found", entityId.toString());
        } else {
            sendLangFeedback(ctx.getSource(), "spawner_potential_removed", entityId.toString(), removed);
        }
        return 1;
    }

    private static int clearSpawnPotentials(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        SpawnerBlockEntityUtil.clearSpawnPotentials(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "spawner_potential_cleared");
        return 1;
    }

    // -- spawnData --

    private static int setSpawnDataNoExtra(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return setSpawnData(ctx, null);
    }

    private static int setSpawnDataWithExtra(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return setSpawnData(ctx, StringArgumentType.getString(ctx, "entity_data"));
    }

    private static int setSpawnData(CommandContext<FabricClientCommandSource> ctx, String entityDataSnbt) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        Identifier entityId = requireEntityId(ctx, "entity");
        try {
            SpawnerBlockEntityUtil.setSpawnData(stack, entityId, entityDataSnbt);
        } catch (Exception e) {
            throw createException("bad_nbt", e.getMessage());
        }

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "spawner_data_set", entityId.toString());
        return 1;
    }

    private static int getSpawnData(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        String snbt = SpawnerBlockEntityUtil.getSpawnDataSnbt(stack);
        if (snbt == null) {
            sendLangFeedback(ctx.getSource(), "spawner_data_empty");
        } else {
            sendLangFeedback(ctx.getSource(), "spawner_data_status", snbt);
        }
        return 1;
    }

    private static int resetSpawnData(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        SpawnerBlockEntityUtil.resetSpawnData(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "spawner_data_reset");
        return 1;
    }

    // ================================================================
    //  /cie edit enchantable ...  (ENCHANTABLE — "стоимость" применения зачарований)
    //  ВАЖНО: EnchantableComponent(int value) и component.value() написаны по
    //  общей схожести с record-компонентами мода (см. RepairCost — просто int).
    //  Если у EnchantableComponent другое имя accessor'а/конструктора в вашей
    //  версии — пришлите ошибку компиляции этого блока, поправим точечно.
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> enchantableNode() {
        return ClientCommandManager.literal("enchantable")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getEnchantable))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(1))
                                .executes(CIECommand::setEnchantable)))
                .then(ClientCommandManager.literal("reset").executes(CIECommand::resetEnchantable));
    }

    private static int getEnchantable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ItemStack stack = requireItem(ctx);
        EnchantableComponent component = stack.get(DataComponentTypes.ENCHANTABLE);
        if (component == null) {
            sendLangFeedback(ctx.getSource(), "enchantable_empty");
        } else {
            sendLangFeedback(ctx.getSource(), "enchantable_status", component.value());
        }
        return 1;
    }

    private static int setEnchantable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int value = IntegerArgumentType.getInteger(ctx, "value");
        stack.set(DataComponentTypes.ENCHANTABLE, new EnchantableComponent(value));

        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "enchantable_set", value);
        return 1;
    }

    private static int resetEnchantable(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        stack.remove(DataComponentTypes.ENCHANTABLE);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "enchantable_reset");
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> livePreviewNode() {
        return ClientCommandManager.literal("livePreview")
                .then(ClientCommandManager.literal("get").executes(CIECommand::getLivePreview))
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setLivePreview(ctx, BoolArgumentType.getBool(ctx, "value")))))
                .then(ClientCommandManager.literal("size")
                        .then(ClientCommandManager.literal("get").executes(CIECommand::getLivePreviewSize))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("value",
                                                IntegerArgumentType.integer(LivePreviewUtil.MIN_SLOT_SIZE, LivePreviewUtil.MAX_SLOT_SIZE))
                                        .executes(CIECommand::setLivePreviewSize))));
    }

    private static int getLivePreviewSize(CommandContext<FabricClientCommandSource> ctx) {
        sendLangFeedback(ctx.getSource(), "live_preview_size_status", LivePreviewUtil.getSlotSize());
        return 1;
    }

    private static int setLivePreviewSize(CommandContext<FabricClientCommandSource> ctx) {
        int value = IntegerArgumentType.getInteger(ctx, "value");
        LivePreviewUtil.setSlotSize(value);
        sendLangFeedback(ctx.getSource(), "live_preview_size_set", value);
        return 1;
    }

    private static int getLivePreview(CommandContext<FabricClientCommandSource> ctx) {
        boolean value = LivePreviewUtil.isEnabled();
        sendLangFeedback(ctx.getSource(), "live_preview_status", value);
        return 1;
    }

    private static int setLivePreview(CommandContext<FabricClientCommandSource> ctx, boolean value) {
        LivePreviewUtil.setEnabled(value);
        sendLangFeedback(ctx.getSource(), value ? "live_preview_enabled" : "live_preview_disabled");
        return 1;
    }

    // ================================================================
    //  /cie edit container / /cie edit bundle
    //  (minecraft:container и minecraft:bundle_contents — см.
    //  ContainerUtil/BundleUtil). Экран открывается через общий
    //  ItemContentsScreenHandler/Screen (тот же паттерн, что и
    //  /cie storage), сохранение содержимого — при закрытии экрана.
    //
    //  НЕ РЕАЛИЗОВАНО в этом заходе (сознательный вырез по объёму задачи,
    //  см. чат): авто-детект специального ванильного меню для
    //  контейнеров с реальным GUI (воронка/дозатор/раздатчик и т.п. —
    //  сейчас ВСЕГДА открывается общий 27-слотовый редактор, без
    //  воронкового меню 3x1) и текстурные оверлеи-иконки на
    //  ограниченных слотах в духе smithing_table. Контейнер редактируется
    //  всегда как обычный 27-слотовый (3 ряда), без привязки к
    //  реальному лимиту слотов конкретного блока.
    // ================================================================

    private static LiteralArgumentBuilder<FabricClientCommandSource> containerNode() {
        return ClientCommandManager.literal("container")
                .then(ClientCommandManager.literal("open").executes(CIECommand::openContainer))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearContainer))
                .then(ClientCommandManager.literal("get")
                        .then(ClientCommandManager.argument("slot", IntegerArgumentType.integer(0))
                                .executes(CIECommand::getContainerSlot)))
                .then(ClientCommandManager.literal("item")
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.literal("fromOffHand").executes(CIECommand::addContainerItemFromOffHand))
                                .then(ClientCommandManager.literal("fromStorage").executes(CIECommand::addContainerItemFromStorage))
                                .then(ClientCommandManager.literal("new")
                                        .then(ClientCommandManager.argument("item", IdentifierArgumentType.identifier())
                                                .suggests(ITEM_SUGGESTIONS)
                                                .executes(ctx -> addContainerItemNew(ctx, 1, ""))
                                                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> addContainerItemNew(ctx, IntegerArgumentType.getInteger(ctx, "count"), ""))
                                                        .then(ClientCommandManager.argument("components", StringArgumentType.greedyString())
                                                                .executes(ctx -> addContainerItemNew(ctx,
                                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                                        StringArgumentType.getString(ctx, "components")))))))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> bundleNode() {
        return ClientCommandManager.literal("bundle")
                .then(ClientCommandManager.literal("open").executes(CIECommand::openBundle))
                .then(ClientCommandManager.literal("clear").executes(CIECommand::clearBundle))
                .then(ClientCommandManager.literal("get")
                        .then(ClientCommandManager.argument("slot", IntegerArgumentType.integer(0))
                                .executes(CIECommand::getBundleSlot)))
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.literal("fromOffHand").executes(CIECommand::addBundleItemFromOffHand))
                        .then(ClientCommandManager.literal("fromStorage").executes(CIECommand::addBundleItemFromStorage))
                        .then(ClientCommandManager.literal("new")
                                .then(ClientCommandManager.argument("item", IdentifierArgumentType.identifier())
                                        .suggests(ITEM_SUGGESTIONS)
                                        .executes(ctx -> addBundleItemNew(ctx, 1, ""))
                                        .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> addBundleItemNew(ctx, IntegerArgumentType.getInteger(ctx, "count"), ""))
                                                .then(ClientCommandManager.argument("components", StringArgumentType.greedyString())
                                                        .executes(ctx -> addBundleItemNew(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                                StringArgumentType.getString(ctx, "components"))))))));
    }

    /**
     * Общий деферред-опенер редактора содержимого (та же гочу-схема, что и
     * у openStoragePage: openScreenNextTick, иначе ChatScreen сам закроется
     * после выполнения команды и затрёт наш только что открытый экран.
     * selectedSlot фиксируется В МОМЕНТ открытия — на него же и
     * сохраняется результат при закрытии, даже если игрок за это время
     * успел покрутить колёсико (тот же трюк, что и в fromStorage-пикерах).
     */
    private static void openItemContentsEditor(FabricClientCommandSource source, int rows, List<ItemStack> initial, String title,
                                               java.util.function.BiConsumer<ItemStack, List<ItemStack>> saveFn) {
        MinecraftClient client = MinecraftClient.getInstance();
        openScreenNextTick(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) {
                return null;
            }
            int selectedSlot = player.getInventory().selectedSlot;

            com.cie.screen.ItemContentsScreenHandler handler =
                    new com.cie.screen.ItemContentsScreenHandler(player.getInventory(), initial, rows);
            return new com.cie.screen.ItemContentsScreen(handler, player.getInventory(), Text.literal(title), contents -> {
                ClientPlayerEntity p = MinecraftClient.getInstance().player;
                if (p == null) {
                    return;
                }
                ItemStack current = p.getInventory().getStack(selectedSlot);
                if (current.isEmpty()) {
                    return;
                }
                UndoUtil.pushSnapshot(p, current);
                saveFn.accept(current, contents);
                syncSlot(p, selectedSlot, current);
            });
        });
    }

    /** Общий "дать предмет копией в инвентарь" — тот же паттерн, что и в giveItem. Возвращает false, если инвентарь полон. */
    private static boolean giveCopyToPlayer(ClientPlayerEntity player, ItemStack item) {
        int emptySlot = player.getInventory().getEmptySlot();
        if (emptySlot < 0) {
            return false;
        }
        int packetSlot = emptySlot < 9 ? 36 + emptySlot : emptySlot;
        ItemStack copy = item.copy();
        player.getInventory().setStack(emptySlot, copy);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(packetSlot, copy));
        }
        return true;
    }

    // -- container --

    private static int openContainer(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        openItemContentsEditor(ctx.getSource(), ContainerUtil.SLOT_COUNT / 9, ContainerUtil.getStacks(stack),
                "CIE Container", ContainerUtil::setStacks);
        return 1;
    }

    private static int clearContainer(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        ContainerUtil.clear(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "container_cleared");
        return 1;
    }

    /** get <slot> — по ТЗ выдаёт КОПИЮ предмета игроку, ничего не забирая из контейнера (сам компонент не трогается). */
    private static int getContainerSlot(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        ItemStack item = ContainerUtil.getSlot(stack, slot);
        if (item.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "container_slot_empty", slot);
            return 0;
        }
        if (!giveCopyToPlayer(player, item)) {
            sendLangFeedback(ctx.getSource(), "storage_inventory_full");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "container_slot_given", slot,
                Registries.ITEM.getId(item.getItem()).toString(), item.getCount());
        return 1;
    }

    private static int addContainerItemFromOffHand(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        ItemStack offHand = player.getOffHandStack();
        if (offHand.isEmpty()) throw createException("entity_equipment_offhand_empty");
        UndoUtil.pushSnapshot(player, stack);

        if (!ContainerUtil.addItem(stack, offHand)) {
            sendLangFeedback(ctx.getSource(), "container_full");
            return 0;
        }
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "container_item_added_offhand");
        return 1;
    }

    private static int addContainerItemFromStorage(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int selectedSlot = player.getInventory().selectedSlot;
        StoragePicker.requestPick(picked -> {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p == null) return;
            ItemStack egg = p.getInventory().getStack(selectedSlot);
            if (egg.isEmpty()) return;
            if (ContainerUtil.addItem(egg, picked)) {
                syncSlot(p, selectedSlot, egg);
            }
        });

        openStoragePage(ctx.getSource(), StoragePageUtil.getCurrentPageIndex());
        sendLangFeedback(ctx.getSource(), "container_item_pick_prompt");
        return 1;
    }

    private static int addContainerItemNew(CommandContext<FabricClientCommandSource> ctx, int count, String componentsSnbt) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        Identifier itemId = ctx.getArgument("item", Identifier.class);
        Item newItem = Registries.ITEM.get(itemId);

        ItemStack toAdd;
        try {
            toAdd = UseRemainderUtil.buildRemainderStack(newItem, count, componentsSnbt, getRegistries());
        } catch (Exception e) {
            sendLangFeedback(ctx.getSource(), "component_parse_error", e.getMessage());
            return 0;
        }

        UndoUtil.pushSnapshot(player, stack);
        if (!ContainerUtil.addItem(stack, toAdd)) {
            sendLangFeedback(ctx.getSource(), "container_full");
            return 0;
        }
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "container_item_added", itemId.toString());
        return 1;
    }

    // -- bundle (аналогично container, но 54 слота и без "item"-обёртки в дереве команд) --

    private static int openBundle(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        openItemContentsEditor(ctx.getSource(), BundleUtil.SLOT_COUNT / 9, BundleUtil.getStacks(stack, getRegistries()),
                "CIE Bundle", (s, contents) -> BundleUtil.setStacks(s, contents, getRegistries()));
        return 1;
    }

    private static int clearBundle(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        BundleUtil.clear(stack);
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "bundle_cleared");
        return 1;
    }

    private static int getBundleSlot(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        ItemStack item = BundleUtil.getSlot(stack, slot, getRegistries());
        if (item.isEmpty()) {
            sendLangFeedback(ctx.getSource(), "bundle_slot_empty", slot);
            return 0;
        }
        if (!giveCopyToPlayer(player, item)) {
            sendLangFeedback(ctx.getSource(), "storage_inventory_full");
            return 0;
        }
        sendLangFeedback(ctx.getSource(), "bundle_slot_given", slot,
                Registries.ITEM.getId(item.getItem()).toString(), item.getCount());
        return 1;
    }

    private static int addBundleItemFromOffHand(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        ItemStack offHand = player.getOffHandStack();
        if (offHand.isEmpty()) throw createException("entity_equipment_offhand_empty");
        UndoUtil.pushSnapshot(player, stack);

        if (!BundleUtil.addItem(stack, offHand, getRegistries())) {
            sendLangFeedback(ctx.getSource(), "bundle_full");
            return 0;
        }
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "bundle_item_added_offhand");
        return 1;
    }

    private static int addBundleItemFromStorage(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");
        UndoUtil.pushSnapshot(player, stack);

        int selectedSlot = player.getInventory().selectedSlot;
        StoragePicker.requestPick(picked -> {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p == null) return;
            ItemStack egg = p.getInventory().getStack(selectedSlot);
            if (egg.isEmpty()) return;
            if (BundleUtil.addItem(egg, picked, getRegistries())) {
                syncSlot(p, selectedSlot, egg);
            }
        });

        openStoragePage(ctx.getSource(), StoragePageUtil.getCurrentPageIndex());
        sendLangFeedback(ctx.getSource(), "bundle_item_pick_prompt");
        return 1;
    }

    private static int addBundleItemNew(CommandContext<FabricClientCommandSource> ctx, int count, String componentsSnbt) throws CommandSyntaxException {
        ClientPlayerEntity player = requireCreativePlayer(ctx);
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) throw createException("no_item");

        Identifier itemId = ctx.getArgument("item", Identifier.class);
        Item newItem = Registries.ITEM.get(itemId);

        ItemStack toAdd;
        try {
            toAdd = UseRemainderUtil.buildRemainderStack(newItem, count, componentsSnbt, getRegistries());
        } catch (Exception e) {
            sendLangFeedback(ctx.getSource(), "component_parse_error", e.getMessage());
            return 0;
        }

        UndoUtil.pushSnapshot(player, stack);
        if (!BundleUtil.addItem(stack, toAdd, getRegistries())) {
            sendLangFeedback(ctx.getSource(), "bundle_full");
            return 0;
        }
        syncHandItem(player, stack);
        sendLangFeedback(ctx.getSource(), "bundle_item_added", itemId.toString());
        return 1;
    }
}