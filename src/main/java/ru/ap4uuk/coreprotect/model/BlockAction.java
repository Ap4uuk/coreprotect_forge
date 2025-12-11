package ru.ap4uuk.coreprotect.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.time.Instant;
import java.util.UUID;

public class BlockAction {

    public enum Type {
        BREAK,
        PLACE
    }

    public final Type type;
    public final UUID playerUuid;
    public final String playerName;
    public final ResourceKey<Level> dimension;
    public final BlockPos pos;
    public final BlockState oldState; // null если не нужно
    public final BlockState newState; // null если не нужно
    public final Instant time;

    public BlockAction(Type type,
                       UUID playerUuid,
                       String playerName,
                       ResourceKey<Level> dimension,
                       BlockPos pos,
                       BlockState oldState,
                       BlockState newState,
                       Instant time) {
        this.type = type;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.dimension = dimension;
        this.pos = pos;
        this.oldState = oldState;
        this.newState = newState;
        this.time = time;
    }
}
