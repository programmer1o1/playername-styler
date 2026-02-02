package sierra.thing.playernamestyler.integrations;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.platform.PlayerAdapter;
import net.minecraft.server.level.ServerPlayer;

public class LuckPermsBridge {
    public static String getPrefix(ServerPlayer player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            PlayerAdapter adapter = lp.getPlayerAdapter(ServerPlayer.class);
            CachedMetaData meta = adapter.getMetaData((Object)player);
            String prefix = meta.getPrefix();
            return prefix != null ? prefix : "";
        }
        catch (Throwable t) {
            return "";
        }
    }

    public static String getSuffix(ServerPlayer player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            PlayerAdapter adapter = lp.getPlayerAdapter(ServerPlayer.class);
            CachedMetaData meta = adapter.getMetaData((Object)player);
            String suffix = meta.getSuffix();
            return suffix != null ? suffix : "";
        }
        catch (Throwable t) {
            return "";
        }
    }
}
