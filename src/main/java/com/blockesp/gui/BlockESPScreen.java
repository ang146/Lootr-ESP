package com.blockesp.gui;

import com.blockesp.config.BlockESPConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * In-game GUI for managing BlockESP settings and tracked blocks.
 * Press J (default) to open.
 */
public class BlockESPScreen extends Screen {

    private EditBox blockInput;
    private EditBox colorInput;
    private EditBox radiusInput;
    private Button toggleButton;
    private Button addButton;

    // Scrollable block list
    private int scrollOffset = 0;
    private static final int LIST_ENTRY_HEIGHT = 16;
    private static final int LIST_VISIBLE_ENTRIES = 10;

    public BlockESPScreen() {
        super(Component.literal("BlockESP Settings"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 30;

        // Title is rendered in render()

        // Toggle button
        toggleButton = Button.builder(
                Component.literal(BlockESPConfig.isEnabled() ? "ESP: ON" : "ESP: OFF"),
                btn -> {
                    BlockESPConfig.toggle();
                    btn.setMessage(Component.literal(
                            BlockESPConfig.isEnabled() ? "ESP: ON" : "ESP: OFF"));
                }
        ).bounds(centerX - 100, startY, 200, 20).build();
        addRenderableWidget(toggleButton);

        startY += 28;

        // Block ID input
        blockInput = new EditBox(this.font, centerX - 100, startY, 140, 18,
                Component.literal("Block ID"));
        blockInput.setMaxLength(100);
        blockInput.setHint(Component.literal("e.g. minecraft:diamond_ore"));
        addRenderableWidget(blockInput);

        // Color input
        colorInput = new EditBox(this.font, centerX + 46, startY, 54, 18,
                Component.literal("Color"));
        colorInput.setMaxLength(7);
        colorInput.setHint(Component.literal("#RRGGBB"));
        addRenderableWidget(colorInput);

        startY += 24;

        // Add button
        addButton = Button.builder(
                Component.literal("Add Block"),
                btn -> addBlockFromInput()
        ).bounds(centerX - 100, startY, 96, 20).build();
        addRenderableWidget(addButton);

        // Clear all button
        addRenderableWidget(Button.builder(
                Component.literal("Clear All"),
                btn -> {
                    BlockESPConfig.clearAll();
                }
        ).bounds(centerX + 4, startY, 96, 20).build());

        startY += 28;

        // Radius control
        radiusInput = new EditBox(this.font, centerX - 30, startY, 40, 18,
                Component.literal("Radius"));
        radiusInput.setMaxLength(3);
        radiusInput.setValue(String.valueOf(BlockESPConfig.getScanRadius()));
        addRenderableWidget(radiusInput);

        addRenderableWidget(Button.builder(
                Component.literal("Set Radius"),
                btn -> {
                    try {
                        int r = Integer.parseInt(radiusInput.getValue());
                        BlockESPConfig.setScanRadius(r);
                        radiusInput.setValue(String.valueOf(BlockESPConfig.getScanRadius()));
                    } catch (NumberFormatException ignored) {}
                }
        ).bounds(centerX + 16, startY, 84, 20).build());

        startY += 28;

        // Toggle filled faces
        addRenderableWidget(Button.builder(
                Component.literal("Faces: " + (BlockESPConfig.isFilledFaces() ? "ON" : "OFF")),
                btn -> {
                    BlockESPConfig.setFilledFaces(!BlockESPConfig.isFilledFaces());
                    btn.setMessage(Component.literal(
                            "Faces: " + (BlockESPConfig.isFilledFaces() ? "ON" : "OFF")));
                }
        ).bounds(centerX - 100, startY, 96, 20).build());

        // Close button
        addRenderableWidget(Button.builder(
                Component.literal("Close"),
                btn -> onClose()
        ).bounds(centerX + 4, startY, 96, 20).build());
    }

    private void addBlockFromInput() {
        String blockId = blockInput.getValue().trim();
        if (blockId.isEmpty()) return;

        if (!blockId.contains(":")) {
            blockId = "minecraft:" + blockId;
        }

        int color;
        String colorStr = colorInput.getValue().trim();
        if (!colorStr.isEmpty()) {
            try {
                String clean = colorStr.replace("#", "").replace("0x", "");
                color = 0xFF000000 | Integer.parseInt(clean, 16);
            } catch (NumberFormatException e) {
                color = BlockESPConfig.getDefaultColor(blockId);
            }
        } else {
            color = BlockESPConfig.getDefaultColor(blockId);
        }

        BlockESPConfig.addBlock(blockId, color);
        blockInput.setValue("");
        colorInput.setValue("");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;

        // Title
        graphics.drawCenteredString(this.font, "§6§lBlockESP Settings", centerX, 14, 0xFFFFFF);

        // Label for radius
        graphics.drawString(this.font, "Radius:", centerX - 100, 114, 0xAAAAAA);

        // Block list header
        int listY = 170;
        graphics.drawString(this.font, "§e§lTracked Blocks:", centerX - 100, listY - 14, 0xFFFFFF);

        // Render block list
        Map<String, Integer> tracked = BlockESPConfig.getTrackedBlocks();
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(tracked.entrySet());

        int maxScroll = Math.max(0, entries.size() - LIST_VISIBLE_ENTRIES);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        for (int i = 0; i < LIST_VISIBLE_ENTRIES && (i + scrollOffset) < entries.size(); i++) {
            Map.Entry<String, Integer> entry = entries.get(i + scrollOffset);
            int y = listY + i * LIST_ENTRY_HEIGHT;

            int color = entry.getValue();
            int displayColor = color | 0xFF000000;

            // Color swatch
            graphics.fill(centerX - 100, y, centerX - 88, y + 12, displayColor);

            // Block name
            graphics.drawString(this.font, entry.getKey(), centerX - 84, y + 2, 0xFFFFFF);

            // Remove button area
            String removeText = "§c[X]";
            int removeWidth = this.font.width("[X]");
            graphics.drawString(this.font, removeText, centerX + 100 - removeWidth, y + 2, 0xFF5555);
        }

        // Scroll indicator
        if (entries.size() > LIST_VISIBLE_ENTRIES) {
            String scrollText = "Scroll: " + (scrollOffset + 1) + "-" +
                    Math.min(scrollOffset + LIST_VISIBLE_ENTRIES, entries.size()) +
                    " / " + entries.size();
            graphics.drawCenteredString(this.font, scrollText, centerX,
                    listY + LIST_VISIBLE_ENTRIES * LIST_ENTRY_HEIGHT + 4, 0x888888);
        }

        // Status bar
        String status = "ESP: " + (BlockESPConfig.isEnabled() ? "§aON" : "§cOFF") +
                " §7| Tracking: §f" + tracked.size() + " blocks" +
                " §7| Radius: §f" + BlockESPConfig.getScanRadius();
        graphics.drawCenteredString(this.font, status, centerX, this.height - 14, 0xAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if clicking a remove [X] button
        int centerX = this.width / 2;
        int listY = 170;
        Map<String, Integer> tracked = BlockESPConfig.getTrackedBlocks();
        List<String> keys = new ArrayList<>(tracked.keySet());

        for (int i = 0; i < LIST_VISIBLE_ENTRIES && (i + scrollOffset) < keys.size(); i++) {
            int y = listY + i * LIST_ENTRY_HEIGHT;
            int removeWidth = this.font.width("[X]");
            int removeX = centerX + 100 - removeWidth;

            if (mouseX >= removeX && mouseX <= centerX + 100 && mouseY >= y && mouseY <= y + 12) {
                String blockId = keys.get(i + scrollOffset);
                BlockESPConfig.removeBlock(blockId);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (int) delta;
        scrollOffset = Math.max(0, scrollOffset);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter key adds block
        if (keyCode == 257 && (blockInput.isFocused())) {
            addBlockFromInput();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
