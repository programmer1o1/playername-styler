package sierra.thing.playernamestyler.util;

import java.lang.reflect.Method;
import net.minecraft.commands.CommandSourceStack;

public final class CommandSourceCompat {
    private static volatile Method hasPermission;
    private static volatile Method hasPermissionLevel;

    private CommandSourceCompat() {
    }

    public static boolean hasPermission(CommandSourceStack src, int level) {
        if (src == null) {
            return false;
        }

        try {
            Method m = hasPermission;
            if (m == null) {
                m = CommandSourceStack.class.getMethod("hasPermission", Integer.TYPE);
                hasPermission = m;
            }
            return (boolean) m.invoke(src, level);
        } catch (NoSuchMethodException ignored) {
            // fall back
        } catch (Throwable t) {
            // fall back
        }

        try {
            Method m = hasPermissionLevel;
            if (m == null) {
                m = CommandSourceStack.class.getMethod("hasPermissionLevel", Integer.TYPE);
                hasPermissionLevel = m;
            }
            return (boolean) m.invoke(src, level);
        } catch (Throwable t) {
            // fall back
        }

        // If we can't detect permission methods, only allow non-entity sources (console/RCON).
        try {
            return src.getEntity() == null;
        } catch (Throwable t) {
            return false;
        }
    }
}

