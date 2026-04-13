package com.blockesp.command;

import com.blockesp.config.BlockESPConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Client-side commands for managing tracked blocks.
 * 
 * Usage:
 *   /besp add <block_id> [color_hex]    - Add a block to track
 *   /besp remove <block_id>             - Remove a block
 *   /besp list                          - List tracked blocks
 *   /besp clear                         - Clear all tracked blocks
 *   /besp toggle                        - Toggle ESP on/off
 *   /besp radius <value>                - Set scan radius
 *   /besp addlooking [color_hex]        - Add the block you're looking at
 */
public class BlockESPCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("besp")
                // /besp add <block_id> [color]
                .then(Commands.literal("add")
                        .then(Commands.argument("block", StringArgumentType.string())
                                .executes(ctx -> {
                                    String blockId = StringArgumentType.getString(ctx, "block");
                                    return addBlock(ctx.getSource(), blockId, null);
                                })
                                .then(Commands.argument("color", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String blockId = StringArgumentType.getString(ctx, "block");
                                            String color = StringArgumentType.getString(ctx, "color");
                                            return addBlock(ctx.getSource(), blockId, color);
                                        })
                                )
                        )
                )
                // /besp remove <block_id>
                .then(Commands.literal("remove")
                        .then(Commands.argument("block", StringArgumentType.string())
                                .executes(ctx -> {
                                    String blockId = StringArgumentType.getString(ctx, "block");
                                    return removeBlock(ctx.getSource(), blockId);
                                })
                        )
                )
                // /besp list
                .then(Commands.literal("list")
                        .executes(ctx -> listBlocks(ctx.getSource()))
                )
                // /besp clear
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            BlockESPConfig.clearAll();
                            ctx.getSource().sendSystemMessage(
                                    Component.literal("§7[BlockESP] §aCleared all tracked blocks"));
                            return 1;
                        })
                )
                // /besp toggle
                .then(Commands.literal("toggle")
                        .executes(ctx -> {
                            BlockESPConfig.toggle();
                            String state = BlockESPConfig.isEnabled() ? "§aON" : "§cOFF";
                            ctx.getSource().sendSystemMessage(
                                    Component.literal("§7[BlockESP] ESP is now " + state));
                            return 1;
                        })
                )
                // /besp radius <value>
                .then(Commands.literal("radius")
                        .then(Commands.argument("value", IntegerArgumentType.integer(4, 128))
                                .executes(ctx -> {
                                    int radius = IntegerArgumentType.getInteger(ctx, "value");
                                    BlockESPConfig.setScanRadius(radius);
                                    ctx.getSource().sendSystemMessage(
                                            Component.literal("§7[BlockESP] §aScan radius set to " + radius));
                                    return 1;
                                })
                        )
                )
                // /besp help
                .then(Commands.literal("help")
                        .executes(ctx -> showHelp(ctx.getSource()))
                )
                // Default: show help
                .executes(ctx -> showHelp(ctx.getSource()))
        );
    }

    private static int addBlock(CommandSourceStack source, String blockId, String colorHex) {
        // Auto-prefix minecraft: if no namespace
        if (!blockId.contains(":")) {
            blockId = "minecraft:" + blockId;
        }

        // Validate the block exists
        ResourceLocation resLoc = new ResourceLocation(blockId);
        if (!BuiltInRegistries.BLOCK.containsKey(resLoc)) {
            // Still allow it (for mod blocks that might load later)
            source.sendSystemMessage(
                    Component.literal("§7[BlockESP] §eWarning: Block '" + blockId +
                            "' not found in registry. Added anyway (may be a mod block)."));
        }

        // Parse color
        int color;
        if (colorHex != null) {
            try {
                // Remove # or 0x prefix
                String clean = colorHex.replace("#", "").replace("0x", "");
                if (clean.length() == 6) {
                    color = 0xFF000000 | Integer.parseInt(clean, 16);
                } else if (clean.length() == 8) {
                    color = (int) Long.parseLong(clean, 16);
                } else {
                    source.sendSystemMessage(
                            Component.literal("§7[BlockESP] §cInvalid color. Use hex format: #RRGGBB or #AARRGGBB"));
                    return 0;
                }
            } catch (NumberFormatException e) {
                source.sendSystemMessage(
                        Component.literal("§7[BlockESP] §cInvalid color format. Example: #00FFFF"));
                return 0;
            }
        } else {
            color = BlockESPConfig.getDefaultColor(blockId);
        }

        BlockESPConfig.addBlock(blockId, color);
        String colorStr = String.format("#%06X", color & 0xFFFFFF);
        source.sendSystemMessage(
                Component.literal("§7[BlockESP] §aAdded §f" + blockId + " §awith color §f" + colorStr));
        return 1;
    }

    private static int removeBlock(CommandSourceStack source, String blockId) {
        if (!blockId.contains(":")) {
            blockId = "minecraft:" + blockId;
        }

        if (BlockESPConfig.getTrackedBlocks().containsKey(blockId)) {
            BlockESPConfig.removeBlock(blockId);
            source.sendSystemMessage(
                    Component.literal("§7[BlockESP] §aRemoved §f" + blockId));
        } else {
            source.sendSystemMessage(
                    Component.literal("§7[BlockESP] §cBlock §f" + blockId + " §cwas not tracked"));
        }
        return 1;
    }

    private static int listBlocks(CommandSourceStack source) {
        Map<String, Integer> tracked = BlockESPConfig.getTrackedBlocks();

        if (tracked.isEmpty()) {
            source.sendSystemMessage(
                    Component.literal("§7[BlockESP] §eNo blocks are being tracked. Use /besp add <block_id>"));
            return 1;
        }

        source.sendSystemMessage(Component.literal("§7[BlockESP] §aTracked blocks:"));

        for (Map.Entry<String, Integer> entry : tracked.entrySet()) {
            String colorHex = String.format("#%06X", entry.getValue() & 0xFFFFFF);
            source.sendSystemMessage(
                    Component.literal("  §f" + entry.getKey() + " §7- §f" + colorHex));
        }

        source.sendSystemMessage(
                Component.literal("§7Total: §f" + tracked.size() + " blocks §7| ESP: " +
                        (BlockESPConfig.isEnabled() ? "§aON" : "§cOFF") +
                        " §7| Radius: §f" + BlockESPConfig.getScanRadius()));
        return 1;
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("§6=== BlockESP Commands ==="));
        source.sendSystemMessage(Component.literal("§e/besp add <block_id> [#color] §7- Add block to track"));
        source.sendSystemMessage(Component.literal("§e/besp remove <block_id> §7- Remove tracked block"));
        source.sendSystemMessage(Component.literal("§e/besp list §7- Show all tracked blocks"));
        source.sendSystemMessage(Component.literal("§e/besp clear §7- Remove all tracked blocks"));
        source.sendSystemMessage(Component.literal("§e/besp toggle §7- Toggle ESP on/off"));
        source.sendSystemMessage(Component.literal("§e/besp radius <4-128> §7- Set scan radius"));
        source.sendSystemMessage(Component.literal("§7Keybinds: §fH §7= Toggle | §fJ §7= Open GUI"));
        source.sendSystemMessage(Component.literal("§7Examples:"));
        source.sendSystemMessage(Component.literal("  §f/besp add diamond_ore"));
        source.sendSystemMessage(Component.literal("  §f/besp add modname:custom_ore #FF00FF"));
        return 1;
    }
}
