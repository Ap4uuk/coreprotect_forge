package ru.ap4uuk.coreprotect.command;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class PurgeParams {

    public Integer seconds;
    public ResourceKey<Level> dimension;
    public final List<String> includeBlocks = new ArrayList<>();
    public boolean optimize;
}

