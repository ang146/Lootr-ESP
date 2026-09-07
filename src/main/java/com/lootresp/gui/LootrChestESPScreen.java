package com.lootresp.gui;

import com.lootresp.config.LootrChestESPConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LootrChestESPScreen extends Screen {
    private Button enabledButton;
    private Button outlineButton;
    private Button facesButton;
    private EditBox radiusInput;
    private EditBox intervalInput;
    private EditBox opacityInput;
    private EditBox lineWidthInput;
    private EditBox colorInput;

    public LootrChestESPScreen() {
        super(Component.literal("Lootr Chest ESP"));
    }

    @Override
    protected void init() {
        int center = width / 2;
        int y = 38;

        enabledButton = addRenderableWidget(Button.builder(enabledLabel(), button -> {
            LootrChestESPConfig.setEnabled(!LootrChestESPConfig.isEnabled());
            button.setMessage(enabledLabel());
        }).bounds(center - 100, y, 200, 20).build());

        y += 28;
        radiusInput = addNumberField(center + 15, y, Integer.toString(LootrChestESPConfig.getScanRadius()));
        radiusInput.setResponder(value -> updateInt(value, LootrChestESPConfig::setScanRadius));

        y += 24;
        intervalInput = addNumberField(center + 15, y, Integer.toString(LootrChestESPConfig.getScanInterval()));
        intervalInput.setResponder(value -> updateInt(value, LootrChestESPConfig::setScanInterval));

        y += 28;
        outlineButton = addRenderableWidget(Button.builder(outlineLabel(), button -> {
            LootrChestESPConfig.setOutlineEnabled(!LootrChestESPConfig.isOutlineEnabled());
            button.setMessage(outlineLabel());
        }).bounds(center - 100, y, 96, 20).build());
        facesButton = addRenderableWidget(Button.builder(facesLabel(), button -> {
            LootrChestESPConfig.setFilledFaces(!LootrChestESPConfig.isFilledFaces());
            button.setMessage(facesLabel());
        }).bounds(center + 4, y, 96, 20).build());

        y += 28;
        opacityInput = addNumberField(center + 15, y, formatFloat(LootrChestESPConfig.getFaceOpacity()));
        opacityInput.setResponder(value -> updateFloat(value, LootrChestESPConfig::setFaceOpacity));

        y += 24;
        lineWidthInput = addNumberField(center + 15, y, formatFloat(LootrChestESPConfig.getLineWidth()));
        lineWidthInput.setResponder(value -> updateFloat(value, LootrChestESPConfig::setLineWidth));

        y += 24;
        colorInput = new EditBox(font, center + 15, y, 85, 20, Component.literal("ESP Color"));
        colorInput.setMaxLength(7);
        colorInput.setValue(String.format("#%06X", LootrChestESPConfig.getEspColor() & 0xFFFFFF));
        addRenderableWidget(colorInput);
        colorInput.setResponder(this::updateColor);

        y += 34;
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(center - 100, y, 200, 20).build());
    }

    private EditBox addNumberField(int x, int y, String value) {
        EditBox field = new EditBox(font, x, y, 85, 20, Component.empty());
        field.setMaxLength(8);
        field.setValue(value);
        addRenderableWidget(field);
        return field;
    }

    private void updateInt(String text, java.util.function.IntConsumer setter) {
        try {
            setter.accept(Integer.parseInt(text.trim()));
        } catch (NumberFormatException ignored) {}
    }

    private void updateFloat(String text, java.util.function.Consumer<Float> setter) {
        try {
            float value = Float.parseFloat(text.trim());
            if (Float.isFinite(value)) setter.accept(value);
        } catch (NumberFormatException ignored) {}
    }

    private void updateColor(String text) {
        String value = text.trim();
        if (value.startsWith("#")) value = value.substring(1);
        try {
            if (value.length() == 6) LootrChestESPConfig.setEspColor(Integer.parseInt(value, 16));
        } catch (NumberFormatException ignored) {}
    }

    private Component enabledLabel() {
        return Component.literal("Enabled: " + (LootrChestESPConfig.isEnabled() ? "ON" : "OFF"));
    }

    private Component outlineLabel() {
        return Component.literal("Outline: " + (LootrChestESPConfig.isOutlineEnabled() ? "ON" : "OFF"));
    }

    private Component facesLabel() {
        return Component.literal("Filled Faces: " + (LootrChestESPConfig.isFilledFaces() ? "ON" : "OFF"));
    }

    private static String formatFloat(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        int center = width / 2;
        graphics.drawString(font, "Radius (4-128)", center - 100, 72, 0xFFFFFF);
        graphics.drawString(font, "Scan Interval (1-100)", center - 100, 96, 0xFFFFFF);
        graphics.drawString(font, "Face Opacity (0-1)", center - 100, 152, 0xFFFFFF);
        graphics.drawString(font, "Line Width (0.5-10)", center - 100, 176, 0xFFFFFF);
        graphics.drawString(font, "Color (#RRGGBB)", center - 100, 200, 0xFFFFFF);
    }

    @Override
    public void tick() {
        super.tick();
        LootrChestESPConfig.flushIfDue();
    }

    @Override
    public void onClose() {
        LootrChestESPConfig.save();
        super.onClose();
    }

    @Override
    public void removed() {
        LootrChestESPConfig.save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
