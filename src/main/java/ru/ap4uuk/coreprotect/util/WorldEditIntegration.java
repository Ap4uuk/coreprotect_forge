package ru.ap4uuk.coreprotect.util;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.forge.ForgeWorld;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockStateHolder;
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
        } catch (ClassNotFoundException e) {
            return;
        }

        try {
            WorldEdit.getInstance().getEventBus().register(new EditSessionListener());
            Coreprotect.LOGGER.info("[Coreprotect] WorldEdit integration enabled.");
        } catch (Throwable t) {
            Coreprotect.LOGGER.warn("[Coreprotect] Failed to hook into WorldEdit", t);
        }
    }

    private static class EditSessionListener {
        @Subscribe
        public void onEditSession(EditSessionEvent event) {
            if (ActionContext.isRollbackInProgress()) {
                return;
            }

            event.setExtent(new LoggingExtent(event.getWorld(), event.getExtent(), event.getActor()));
        }
    }

    private static class LoggingExtent extends AbstractDelegateExtent {
        private final World weWorld;
        private final com.sk89q.worldedit.extension.platform.Actor actor;

        protected LoggingExtent(World world, Extent extent, com.sk89q.worldedit.extension.platform.Actor actor) {
            super(extent);
            this.weWorld = world;
            this.actor = actor;
        }

        @Override
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 location, T block) throws WorldEditException {
            ServerLevel level = unwrapLevel();
            if (level == null) {
                return super.setBlock(location, block);
            }

            BlockPos pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            BlockState oldState = level.getBlockState(pos);
            BlockState newState = ForgeAdapter.adapt(block.toImmutableState());

            boolean result = super.setBlock(location, block);
            if (result && !ActionContext.isRollbackInProgress()) {
                BlockLogging.log(level, pos, oldState, newState, BlockAction.Type.WORLDEDIT, resolvePlayer(level));
            }
            return result;
        }

        private ServerLevel unwrapLevel() {
            if (weWorld instanceof ForgeWorld forgeWorld) {
                return forgeWorld.getWorld();
            }
            return null;
        }

        private ServerPlayer resolvePlayer(ServerLevel level) {
            if (actor instanceof Player wePlayer) {
                return level.getServer().getPlayerList().getPlayer(wePlayer.getUniqueId());
            }
            return null;
        }
    }
}
