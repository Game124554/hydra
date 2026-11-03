package com.realcraft.realism.client.gui;

import com.realcraft.realism.config.Opt;
import com.realcraft.realism.config.PerformancePreset;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Settings for every RealCraft feature: one-click performance presets, then a toggle or slider for
 * each option, grouped by category. Opened from the Mods list or with the settings key (K).
 */
public class RealismSettingsScreen extends Screen {
    private static final Map<String, String> CATEGORY_TITLES = new LinkedHashMap<>();

    static {
        CATEGORY_TITLES.put("terrain", "Smooth Terrain");
        CATEGORY_TITLES.put("camera", "Camera Shake");
        CATEGORY_TITLES.put("ragdolls", "Ragdolls");
        CATEGORY_TITLES.put("items", "Item Physics");
        CATEGORY_TITLES.put("atmosphere", "Hell Atmosphere & Effects");
        CATEGORY_TITLES.put("physics", "Structural Physics & Explosions");
        CATEGORY_TITLES.put("glass", "Breakable Glass");
        CATEGORY_TITLES.put("fluids", "Water & Lava");
        CATEGORY_TITLES.put("world", "World Generation (new chunks)");
        CATEGORY_TITLES.put("mobs", "Realistic Mobs");
    }

    private final Screen parent;
    private final Map<Opt<?>, Object> terrainSnapshot = new LinkedHashMap<>();
    private OptionList list;

    public RealismSettingsScreen(Screen parent) {
        super(Component.translatableWithFallback("realcraft.settings.title", "RealCraft Realism Settings"));
        this.parent = parent;
        terrainSnapshot.put(RealConfig.SMOOTH_TERRAIN, RealConfig.SMOOTH_TERRAIN.get());
        terrainSnapshot.put(RealConfig.SMOOTHNESS, RealConfig.SMOOTHNESS.get());
    }

    @Override
    protected void init() {
        double scroll = list != null ? list.getScrollAmount() : 0.0;
        list = new OptionList(minecraft, width, height, 32, height - 32, 24);
        list.add(new PresetEntry());

        // Visual (client) options first, then gameplay (common) options.
        for (boolean client : new boolean[]{true, false}) {
            String category = null;
            for (Opt<?> opt : RealConfig.OPTIONS) {
                if (opt.client != client) {
                    continue;
                }
                if (!opt.category.equals(category)) {
                    category = opt.category;
                    list.add(new HeaderEntry(categoryTitle(category, client)));
                }
                list.add(new WidgetEntry(createWidget(opt)));
            }
        }
        addWidget(list);
        list.setScrollAmount(scroll);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 100, height - 26, 200, 20)
                .build());
    }

    private static Component categoryTitle(String category, boolean client) {
        MutableComponent title = Component.translatableWithFallback("realcraft.category." + category,
                CATEGORY_TITLES.getOrDefault(category, category));
        return client ? title.withStyle(ChatFormatting.YELLOW) : title.withStyle(ChatFormatting.GOLD);
    }

    private AbstractWidget createWidget(Opt<?> opt) {
        AbstractWidget widget;
        if (opt instanceof Opt.Bool bool) {
            widget = CycleButton.onOffBuilder(bool.on()).create(0, 0, 310, 20, bool.title(), (button, value) -> bool.set(value));
        } else if (opt instanceof Opt.Int intOpt) {
            widget = new OptionSlider(intOpt.title(), (intOpt.val() - intOpt.min) / (double) (intOpt.max - intOpt.min)) {
                @Override
                protected Component valueText() {
                    return Component.literal(Integer.toString(current()));
                }

                private int current() {
                    return (int) Math.round(intOpt.min + value * (intOpt.max - intOpt.min));
                }

                @Override
                protected void applyValue() {
                    intOpt.set(current());
                }
            };
        } else {
            Opt.Dbl dbl = (Opt.Dbl) opt;
            widget = new OptionSlider(dbl.title(), (dbl.val() - dbl.min) / (dbl.max - dbl.min)) {
                @Override
                protected Component valueText() {
                    double v = current();
                    String text = dbl.max <= 0.1 ? String.format("%.4f", v) : dbl.max <= 1.0 ? String.format("%.2f", v) : String.format("%.1f", v);
                    return Component.literal(text);
                }

                private double current() {
                    return dbl.min + value * (dbl.max - dbl.min);
                }

                @Override
                protected void applyValue() {
                    dbl.set(current());
                }
            };
        }
        MutableComponent tooltip = opt.description().copy();
        if (!opt.client) {
            tooltip.append(Component.literal("\n"))
                    .append(Component.translatableWithFallback("realcraft.settings.common_note",
                            "Gameplay setting: applies in singleplayer and on servers that use this config.")
                            .withStyle(ChatFormatting.GRAY));
        }
        widget.setTooltip(Tooltip.create(tooltip));
        return widget;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        list.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        RealConfig.CLIENT_SPEC.save();
        RealConfig.COMMON_SPEC.save();
        RealConfig.markChanged();
        boolean terrainChanged = terrainSnapshot.entrySet().stream()
                .anyMatch(e -> !Objects.equals(e.getKey().get(), e.getValue()));
        if (terrainChanged && minecraft.level != null) {
            minecraft.levelRenderer.allChanged();
        }
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------------------------------

    private abstract static class OptionSlider extends AbstractSliderButton {
        private final Component label;

        OptionSlider(Component label, double value) {
            super(0, 0, 310, 20, Component.empty(), Mth.clamp(value, 0.0, 1.0));
            this.label = label;
            updateMessage();
        }

        protected abstract Component valueText();

        @Override
        protected void updateMessage() {
            setMessage(Component.empty().append(label).append(": ").append(valueText()));
        }
    }

    private static class OptionList extends ContainerObjectSelectionList<Row> {
        OptionList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
            super(minecraft, width, height, top, bottom, itemHeight);
        }

        void add(Row row) {
            addEntry(row);
        }

        @Override
        public int getRowWidth() {
            return 320;
        }

        @Override
        protected int getScrollbarPosition() {
            return width / 2 + 168;
        }
    }

    private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
    }

    private class HeaderEntry extends Row {
        private final Component text;

        HeaderEntry(Component text) {
            this.text = text;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.drawCenteredString(font, text, left + width / 2, top + height - font.lineHeight - 3, 0xFFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    private static class WidgetEntry extends Row {
        private final AbstractWidget widget;

        WidgetEntry(AbstractWidget widget) {
            this.widget = widget;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            widget.setX(left + (width - widget.getWidth()) / 2);
            widget.setY(top);
            widget.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(widget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(widget);
        }
    }

    private class PresetEntry extends Row {
        private final List<Button> buttons = new ArrayList<>();

        PresetEntry() {
            for (PerformancePreset preset : PerformancePreset.values()) {
                Component name = Component.translatableWithFallback(preset.translationKey(), preset.label());
                Button button = Button.builder(name, b -> {
                    preset.apply();
                    rebuildWidgets();
                }).bounds(0, 0, 76, 20).build();
                button.setTooltip(Tooltip.create(Component.translatableWithFallback("realcraft.settings.preset_tooltip",
                        "Apply the %s performance preset (sets the limits below).", name)));
                if (preset == RealConfig.PRESET.get()) {
                    button.setMessage(name.copy().withStyle(ChatFormatting.GREEN));
                }
                buttons.add(button);
            }
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int x = left + (width - (buttons.size() * 78 - 2)) / 2;
            for (Button button : buttons) {
                button.setX(x);
                button.setY(top);
                button.render(graphics, mouseX, mouseY, partialTick);
                x += 78;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return buttons;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return buttons;
        }
    }
}
