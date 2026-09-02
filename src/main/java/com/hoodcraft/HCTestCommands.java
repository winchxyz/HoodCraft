package com.hoodcraft;

import com.hoodcraft.item.HoodBrushItem;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Test-only commands. Registered exclusively in a development environment, so nothing here reaches
 * a player's game.
 *
 * <p>{@code /hcbrush <pos>} brushes one block through to completion in a single call. It exists
 * because the brush is otherwise untestable: brushing is a use-item-over-time action driven by
 * holding right-click, there is no vanilla command equivalent, and synthetic mouse input does not
 * reach the game reliably. Without this hook the one mechanic the whole mod is built around could
 * only ever be checked by hand - which is exactly how it shipped broken once already.
 */
@EventBusSubscriber(modid = HoodCraft.MODID)
public final class HCTestCommands {

    /** Ten successful strokes complete a dig; the spare few absorb any that the cooldown eats. */
    private static final int MAX_STROKES = 20;
    private static final int BRUSH_COOLDOWN_TICKS = 10;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("hcbrush")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> {
                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Level level = player.level();

                            if (!(level.getBlockEntity(pos) instanceof BrushableBlockEntity brushable)) {
                                ctx.getSource().sendFailure(Component.literal("not a brushable block"));
                                return 0;
                            }

                            // brush() ignores anything inside its own ten-tick cooldown, so the tick
                            // handed to it has to advance even though no real time passes here.
                            long tick = level.getGameTime();
                            int strokes = 0;
                            boolean completed = false;
                            while (strokes < MAX_STROKES && !completed) {
                                completed = HoodBrushItem.brushStroke(brushable, level, player,
                                        Direction.UP, tick);
                                tick += BRUSH_COOLDOWN_TICKS;
                                strokes++;
                            }

                            final int s = strokes;
                            final boolean c = completed;
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("strokes=" + s + " completed=" + c), false);
                            return 1;
                        }));
        event.getDispatcher().register(node);
    }

    private HCTestCommands() {
    }
}
