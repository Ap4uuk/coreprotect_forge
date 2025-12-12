package ru.ap4uuk.coreprotect.permissions;

import net.minecraft.commands.CommandSourceStack;

/**
 * Simple permission nodes for CoreProtect commands.
 */
public enum EnumPermissions {
    INSPECT("coreprotect.command.inspect", 0),
    TOOL("coreprotect.command.tool", 0),
    ROLLBACK("coreprotect.command.rollback", 2),
    RESTORE("coreprotect.command.restore", 2);

    private final String node;
    private final int requiredLevel;

    EnumPermissions(String node, int requiredLevel) {
        this.node = node;
        this.requiredLevel = requiredLevel;
    }

    public String getNode() {
        return node;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public boolean hasPermission(CommandSourceStack source) {
        return source.hasPermission(requiredLevel);
    }
}
