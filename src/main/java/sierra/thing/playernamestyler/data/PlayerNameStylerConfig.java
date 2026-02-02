package sierra.thing.playernamestyler.data;

import net.neoforged.neoforge.common.ModConfigSpec;

public class PlayerNameStylerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> NAMEPLATE_RENDERER;
    public static final ModConfigSpec.ConfigValue<String> CHAT_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> TABLIST_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> NAMEPLATE_FORMAT;

    static {
        BUILDER.comment(new String[]{
                "-------------------------------------",
                "Placeholders you can use:",
                "%prefix%        LuckPerms prefix (e.g. [VIP])",
                "%displayname%   Nickname (from PlayerName Styler) or username",
                "%suffix%        LuckPerms suffix",
                "%username%      Player's real MC username",
                "%world%         World/dimension id (e.g. minecraft:overworld)",
                "%dimension%     Alias for %world%",
                "%x%/%y%/%z%     Player block position",
                "%health%        Player health",
                "%maxhealth%     Player max health",
                "%food%          Player hunger level",
                "%biome%         Biome id (e.g. minecraft:plains)",
                "%ping%          Player ping (ms)",
                "%level%         Experience level",
                "%xp%            Total experience",
                "%time%          Day time (0-23999)",
                "%day%           Day number",
                "%chatmessage%   The chat message",
                "%colon%         The colon separator (colorable)",
                "",
                "Color Codes:",
                "&0 - Black      &1 - Dark Blue     &2 - Dark Green  &3 - Dark Aqua",
                "&4 - Dark Red   &5 - Dark Purple   &6 - Gold        &7 - Gray",
                "&8 - Dark Gray  &9 - Blue          &a - Green       &b - Aqua",
                "&c - Red        &d - Light Purple  &e - Yellow      &f - White",
                "&k - Magic      &l - Bold          &m - Strikethrough",
                "&n - Underline  &o - Italic        &r - Reset",
                "&#RRGGBB - Hex color (e.g. &#FFAA00)",
                "",
                "Angle bracket tags:",
                "<#RRGGBB> - Hex color (e.g. <#FFAA00>)",
                "<gradient:#RRGGBB:#RRGGBB>text</gradient> - Gradient text",
                "<rainbow>text</rainbow> - Rainbow text",
                "",
                "Example format:",
                "  &f<%prefix%&r%displayname%%suffix%&f>%colon% &r%chatmessage%",
                "This will show: <prefixDisplaynameSuffix>: message",
                "",
                "If LuckPerms is not present, %prefix% and %suffix% will be empty.",
                "-------------------------------------"
        });
        NAMEPLATE_RENDERER = BUILDER.comment("Nameplate renderer. 'text_display' is default; switch to 'armor_stand' if you prefer legacy behavior.").define("nameplateRenderer", "text_display");
        CHAT_FORMAT = BUILDER.comment("Chat format string with placeholders.").define("chatFormat", "&f<%prefix%&r%displayname%%suffix%&f>%colon%&r%chatmessage%");
        TABLIST_FORMAT = BUILDER.comment("Tab-list format string with placeholders. This is the nickname (and prefix/suffix) shown in the player list (tab). Recommended: '&f<%prefix%&r%displayname%%suffix%&f>'").define("tabListFormat", "%prefix%&r%displayname%%suffix%");
        NAMEPLATE_FORMAT = BUILDER.comment("Nameplate format string with placeholders. This controls how a player's display name (above their head) appears. Placeholders: %prefix%, %displayname%, %suffix%, %username%, %world%").define("nameplateFormat", "%prefix%&r%displayname%%suffix%");
        SPEC = BUILDER.build();
    }
}
