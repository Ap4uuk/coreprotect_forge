package ru.ap4uuk.coreprotect.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import ru.ap4uuk.coreprotect.Coreprotect;
import ru.ap4uuk.coreprotect.model.BlockAction;
import ru.ap4uuk.coreprotect.util.ActionContext;

public final class WorldEditIntegration {

    private WorldEditIntegration() {
    }

    public static void tryRegister() {
        try {
            Class.forName("com.sk89q.worldedit.WorldEdit");
            Class.forName("com.sk89q.worldedit.extension.platform.Platform");
        } catch (ClassNotFoundException e) {
            return;
        }

        try {
            Hook.register();
            Coreprotect.LOGGER.info("[Coreprotect] WorldEdit integration enabled.");
        } catch (Throwable t) {
            Coreprotect.LOGGER.warn("[Coreprotect] Failed to hook into WorldEdit", t);
        }
    }

    private static class Hook {
        private Hook() {
        }

        private static void register() {
            com.sk89q.worldedit.WorldEdit.getInstance().getEventBus().register(new EditSessionListener());
        }

        private static class EditSessionListener {
            @com.sk89q.worldedit.util.eventbus.Subscribe
            public void onEditSession(com.sk89q.worldedit.event.extent.EditSessionEvent event) {
                if (ActionContext.isRollbackInProgress()) {
                    return;
                }

                event.setExtent(new LoggingExtent(event.getWorld(), event.getExtent(), event.getActor()));
            }
        }

        private static class LoggingExtent extends com.sk89q.worldedit.extent.AbstractDelegateExtent {
            private final com.sk89q.worldedit.world.World weWorld;
            private final com.sk89q.worldedit.extension.platform.Actor actor;

            protected LoggingExtent(com.sk89q.worldedit.world.World world, com.sk89q.worldedit.extent.Extent extent, com.sk89q.worldedit.extension.platform.Actor actor) {
                super(extent);
                this.weWorld = world;
                this.actor = actor;
            }

            @Override
            public <T extends com.sk89q.worldedit.world.block.BlockStateHolder<T>> boolean setBlock(com.sk89q.worldedit.math.BlockVector3 location, T block) throws com.sk89q.worldedit.WorldEditException {
                ServerLevel level = unwrapLevel();
                if (level == null) {
                    return super.setBlock(location, block);
                }

                BlockPos pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
                BlockState oldState = level.getBlockState(pos);
                BlockState newState = com.sk89q.worldedit.forge.ForgeAdapter.adapt(block.toImmutableState());

                boolean result = super.setBlock(location, block);
                if (result && !ActionContext.isRollbackInProgress()) {
                    BlockLogging.log(level, pos, oldState, newState, BlockAction.Type.WORLDEDIT, resolvePlayer(level));
                }
                return result;
            }

            private ServerLevel unwrapLevel() {
                if (weWorld instanceof com.sk89q.worldedit.forge.ForgeWorld forgeWorld) {
                    return forgeWorld.getWorld();
                }
                return null;
            }

            private ServerPlayer resolvePlayer(ServerLevel level) {
                if (actor instanceof com.sk89q.worldedit.entity.Player wePlayer) {
                    return level.getServer().getPlayerList().getPlayer(wePlayer.getUniqueId());
                }
                return null;
            }
        }
    }
}
