package dev.nightbeam.corerpg.quests.util;

import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;

/** Maps the classic 0-4 operator levels onto the permission sets used by Minecraft 26. */
public final class Perms {
    private Perms() {
    }

    public static boolean has(PermissionSet permissions, int level) {
        if (level <= 0) {
            return true;
        }
        Permission permission = switch (level) {
            case 1 -> Permissions.COMMANDS_MODERATOR;
            case 2 -> Permissions.COMMANDS_GAMEMASTER;
            case 3 -> Permissions.COMMANDS_ADMIN;
            default -> Permissions.COMMANDS_OWNER;
        };
        return permissions.hasPermission(permission);
    }

    public static net.minecraft.server.permissions.LevelBasedPermissionSet set(int level) {
        return switch (Math.max(0, Math.min(4, level))) {
            case 0 -> net.minecraft.server.permissions.LevelBasedPermissionSet.ALL;
            case 1 -> net.minecraft.server.permissions.LevelBasedPermissionSet.MODERATOR;
            case 2 -> net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER;
            case 3 -> net.minecraft.server.permissions.LevelBasedPermissionSet.ADMIN;
            default -> net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER;
        };
    }
}
