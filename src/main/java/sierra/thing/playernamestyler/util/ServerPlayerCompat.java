package sierra.thing.playernamestyler.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPlayerCompat {
    private static volatile Method serverPlayerGetServer;
    private static volatile Method serverPlayerServer;
    private static volatile Field serverPlayerServerField;

    private static volatile Method gameProfileGetName;
    private static volatile Method gameProfileName;
    private static volatile Field gameProfileNameField;

    private ServerPlayerCompat() {
    }

    public static MinecraftServer getServer(ServerPlayer player) {
        if (player == null) {
            return null;
        }

        try {
            Method m = serverPlayerGetServer;
            if (m == null) {
                m = ServerPlayer.class.getMethod("getServer");
                serverPlayerGetServer = m;
            }
            Object out = m.invoke(player);
            return (MinecraftServer) out;
        } catch (NoSuchMethodException ignored) {
            // fall back
        } catch (Throwable t) {
            // fall back
        }

        try {
            Method m = serverPlayerServer;
            if (m == null) {
                m = ServerPlayer.class.getMethod("server");
                serverPlayerServer = m;
            }
            Object out = m.invoke(player);
            return (MinecraftServer) out;
        } catch (NoSuchMethodException ignored) {
            // fall back
        } catch (Throwable t) {
            // fall back
        }

        try {
            Field f = serverPlayerServerField;
            if (f == null) {
                f = ServerPlayer.class.getDeclaredField("server");
                f.setAccessible(true);
                serverPlayerServerField = f;
            }
            Object out = f.get(player);
            return (MinecraftServer) out;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String getGameProfileName(ServerPlayer player) {
        if (player == null) {
            return "";
        }

        Object profile;
        try {
            profile = player.getGameProfile();
        } catch (Throwable t) {
            profile = null;
        }

        if (profile != null) {
            try {
                Method m = gameProfileGetName;
                if (m == null) {
                    m = profile.getClass().getMethod("getName");
                    gameProfileGetName = m;
                }
                Object out = m.invoke(profile);
                if (out instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (NoSuchMethodException ignored) {
                // fall back
            } catch (Throwable t) {
                // fall back
            }

            try {
                Method m = gameProfileName;
                if (m == null) {
                    m = profile.getClass().getMethod("name");
                    gameProfileName = m;
                }
                Object out = m.invoke(profile);
                if (out instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (NoSuchMethodException ignored) {
                // fall back
            } catch (Throwable t) {
                // fall back
            }

            try {
                Field f = gameProfileNameField;
                if (f == null) {
                    f = profile.getClass().getDeclaredField("name");
                    f.setAccessible(true);
                    gameProfileNameField = f;
                }
                Object out = f.get(profile);
                if (out instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (Throwable t) {
                // fall back
            }
        }

        try {
            return player.getName().getString();
        } catch (Throwable t) {
            return "";
        }
    }
}

