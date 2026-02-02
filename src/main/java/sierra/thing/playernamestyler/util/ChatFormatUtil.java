package sierra.thing.playernamestyler.util;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import sierra.thing.playernamestyler.api.PlaceholderContext;
import sierra.thing.playernamestyler.api.PlaceholderRegistry;
import sierra.thing.playernamestyler.data.NicknameManager;
import sierra.thing.playernamestyler.data.PlayerNameStylerConfig;
import sierra.thing.playernamestyler.integrations.LuckPermsBridge;
import sierra.thing.playernamestyler.util.ColorUtil;

public class ChatFormatUtil {
    private static boolean isLuckPermsLoaded() {
        return ModList.get().isLoaded("luckperms");
    }

    private static Map<String, String> getReplacements(ServerPlayer player, NicknameManager nickMgr, String rawMessage, boolean withMessage, boolean withColon) {
        LinkedHashMap<String, String> vals = new LinkedHashMap<String, String>();
        String nickname = nickMgr.getNickname(player.getUUID());
        String username = ServerPlayerCompat.getGameProfileName(player);
        String display = nickname != null && !nickname.isBlank() ? nickname : username;
        vals.put("%displayname%", display);
        vals.put("%username%", username);
        String dimension = ResourceKeyCompat.locationToString(player.level().dimension());
        vals.put("%world%", dimension);
        vals.put("%dimension%", dimension);
        vals.put("%x%", Integer.toString(player.getBlockX()));
        vals.put("%y%", Integer.toString(player.getBlockY()));
        vals.put("%z%", Integer.toString(player.getBlockZ()));
        vals.put("%health%", ChatFormatUtil.format1dp(player.getHealth()));
        vals.put("%maxhealth%", ChatFormatUtil.format1dp(player.getMaxHealth()));
        vals.put("%food%", Integer.toString(player.getFoodData().getFoodLevel()));
        vals.put("%biome%", ChatFormatUtil.getBiomeId(player));
        vals.put("%ping%", Integer.toString(ChatFormatUtil.getPing(player)));
        vals.put("%level%", Integer.toString(player.experienceLevel));
        vals.put("%xp%", Integer.toString(player.totalExperience));
        long dayTime = player.level().getDayTime();
        vals.put("%time%", Long.toString(dayTime % 24000L));
        vals.put("%day%", Long.toString(dayTime / 24000L));
        String prefix = "";
        String suffix = "";
        if (ChatFormatUtil.isLuckPermsLoaded()) {
            prefix = LuckPermsBridge.getPrefix(player);
            suffix = LuckPermsBridge.getSuffix(player);
        }
        vals.put("%prefix%", prefix);
        vals.put("%suffix%", suffix);
        if (withMessage) {
            vals.put("%chatmessage%", rawMessage);
        }
        if (withColon) {
            vals.put("%colon%", "&f: &r");
        }
        PlaceholderRegistry.fillPlaceholders(vals, new PlaceholderContext(player, nickMgr, rawMessage, withMessage, withColon));
        return vals;
    }

    private static String getBiomeId(ServerPlayer player) {
        try {
            Object key = player.level().getBiome(player.blockPosition()).unwrapKey().orElse(null);
            if (key == null) {
                return "unknown";
            }
            return ResourceKeyCompat.locationToString(key);
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static int getPing(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return 0;
        }
        try {
            return player.connection.latency();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String format1dp(float value) {
        if (value == (float)((int)value)) {
            return Integer.toString((int)value);
        }
        return String.format("%.1f", Float.valueOf(value));
    }

    private static String applyReplacements(String format, Map<String, String> vals) {
        String output = format;
        for (Map.Entry<String, String> e : vals.entrySet()) {
            output = output.replace(e.getKey(), e.getValue());
        }
        return output;
    }

    public static MutableComponent formatChat(ServerPlayer player, String message, NicknameManager nickMgr) {
        Map<String, String> vals = ChatFormatUtil.getReplacements(player, nickMgr, message, true, true);
        String format = (String)PlayerNameStylerConfig.CHAT_FORMAT.get();
        String out = ChatFormatUtil.applyReplacements(format, vals);
        return (MutableComponent)ColorUtil.parseColoredText(out);
    }

    public static MutableComponent formatTablist(ServerPlayer player, NicknameManager nickMgr) {
        Map<String, String> vals = ChatFormatUtil.getReplacements(player, nickMgr, null, false, false);
        String format = (String)PlayerNameStylerConfig.TABLIST_FORMAT.get();
        String out = ChatFormatUtil.applyReplacements(format, vals);
        out = ColorUtil.stripFiguraTags(out);
        return (MutableComponent)ColorUtil.parseColoredText(out);
    }

    public static MutableComponent formatNameplate(ServerPlayer player, NicknameManager nickMgr) {
        Map<String, String> vals = ChatFormatUtil.getReplacements(player, nickMgr, null, false, false);
        String format = (String)PlayerNameStylerConfig.NAMEPLATE_FORMAT.get();
        String out = ChatFormatUtil.applyReplacements(format, vals);
        out = ColorUtil.stripFiguraTags(out);
        return (MutableComponent)ColorUtil.parseColoredText(out);
    }
}
