package sierra.thing.playernamestyler.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import sierra.thing.playernamestyler.PlayerNameStyler;
import sierra.thing.playernamestyler.events.NicknameEvents;
import sierra.thing.playernamestyler.util.CommandSourceCompat;
import sierra.thing.playernamestyler.util.ColorUtil;
import sierra.thing.playernamestyler.util.ServerPlayerCompat;

public class NicknameCommand {
    public static void register() {
        NeoForge.EVENT_BUS.addListener(NicknameCommand::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("nickname")
                .requires(src -> true)
                .then(Commands.literal("clear")
                        .executes(ctx -> NicknameCommand.clearNickname(ctx.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> CommandSourceCompat.hasPermission(src, 2))
                                .executes(ctx -> NicknameCommand.clearNicknameFor(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .requires(src -> CommandSourceCompat.hasPermission(src, 2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                                        .executes(ctx -> NicknameCommand.setNicknameFor(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "nickname"))))))
                .then(Commands.literal("reload")
                        .requires(src -> CommandSourceCompat.hasPermission(src, 2))
                        .executes(ctx -> NicknameCommand.reloadNicknames(ctx.getSource())))
                .then(Commands.literal("cleartags")
                        .requires(src -> CommandSourceCompat.hasPermission(src, 2))
                        .executes(ctx -> NicknameCommand.clearTags(ctx.getSource())))
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(ctx -> NicknameCommand.setNickname(ctx.getSource(), StringArgumentType.getString(ctx, "nickname"))))
                .executes(ctx -> NicknameCommand.showCurrentNickname(ctx.getSource())));

        dispatcher.register(Commands.literal("realname")
                .requires(src -> true)
                .then(Commands.argument("search", StringArgumentType.greedyString())
                        .suggests((context, builder) -> NicknameCommand.suggestNicknames(context.getSource(), builder))
                        .executes(ctx -> NicknameCommand.findRealName(ctx.getSource(), StringArgumentType.getString(ctx, "search"))))
                .executes(ctx -> NicknameCommand.listAllNicknames(ctx.getSource())));
    }

    private static int setNickname(CommandSourceStack src, String nickname) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        PlayerNameStyler.nicknameManager.setNickname(player.getUUID(), nickname);
        NicknameEvents.updateNicknameFor(player);
        NicknameCommand.sendMsg(src, "Your nickname has been set to: ", nickname);
        return 1;
    }

    private static int setNicknameFor(CommandSourceStack src, ServerPlayer target, String nickname) {
        PlayerNameStyler.nicknameManager.setNickname(target.getUUID(), nickname);
        NicknameEvents.updateNicknameFor(target);
        target.refreshTabListName();
        String targetName = ServerPlayerCompat.getGameProfileName(target);
        src.sendSuccess(() -> Component.literal("Set nickname for " + targetName + " to: ").append(ColorUtil.parseColoredText(nickname)), false);
        target.sendSystemMessage(Component.literal("Your nickname has been set to: ").append(ColorUtil.parseColoredText(nickname)));
        return 1;
    }

    private static int clearNickname(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        PlayerNameStyler.nicknameManager.removeNickname(player.getUUID());
        NicknameEvents.clearNickname(player);
        src.sendSuccess(() -> Component.literal((String)"Your nickname has been cleared."), false);
        return 1;
    }

    private static int clearNicknameFor(CommandSourceStack src, ServerPlayer target) {
        PlayerNameStyler.nicknameManager.removeNickname(target.getUUID());
        NicknameEvents.clearNickname(target);
        target.refreshTabListName();
        String targetName = ServerPlayerCompat.getGameProfileName(target);
        src.sendSuccess(() -> Component.literal("Cleared nickname for " + targetName + "."), false);
        target.sendSystemMessage(Component.literal("Your nickname has been cleared."));
        return 1;
    }

    private static int reloadNicknames(CommandSourceStack src) {
        PlayerNameStyler.nicknameManager.loadNicknames();
        int refreshed = 0;
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            NicknameEvents.updateNicknameFor(player);
            player.refreshTabListName();
            refreshed++;
        }
        String message = "Reloaded nicknames.json and refreshed " + refreshed + " player(s).";
        src.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int clearTags(CommandSourceStack src) {
        int removed = NicknameEvents.cleanupAllNameplates(src.getServer());
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            NicknameEvents.updateNicknameFor(player);
        }
        int finalRemoved = removed;
        src.sendSuccess(() -> Component.literal("Cleared " + finalRemoved + " stuck nameplate(s) and refreshed all online players."), true);
        return removed;
    }

    private static int showCurrentNickname(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        String nick = PlayerNameStyler.nicknameManager.getNickname(player.getUUID());
        if (nick == null || nick.isEmpty()) {
            src.sendSuccess(() -> Component.literal((String)"You don't have a nickname set."), false);
        } else {
            NicknameCommand.sendMsg(src, "Your current nickname is: ", nick);
        }
        return 1;
    }

    private static void sendMsg(CommandSourceStack src, String prefix, String coloredValue) {
        MutableComponent msg = Component.literal((String)prefix).append(ColorUtil.parseColoredText(coloredValue));
        src.sendSuccess(() -> msg, false);
    }

    private static int findRealName(CommandSourceStack src, String searchTerm) {
        ArrayList<ServerPlayer> matchingPlayers = new ArrayList<ServerPlayer>();
        String plainSearch = ColorUtil.stripColorTags(searchTerm).toLowerCase();
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            String plainNick;
            String nickname = PlayerNameStyler.nicknameManager.getNickname(player.getUUID());
            if (nickname == null || nickname.isEmpty() || !(plainNick = ColorUtil.stripColorTags(nickname).toLowerCase()).contains(plainSearch)) continue;
            matchingPlayers.add(player);
        }
        return NicknameCommand.showNicknameResults(src, matchingPlayers, searchTerm);
    }

    private static int listAllNicknames(CommandSourceStack src) {
        ArrayList<ServerPlayer> nicknameUsers = new ArrayList<ServerPlayer>();
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            String nickname = PlayerNameStyler.nicknameManager.getNickname(player.getUUID());
            if (nickname == null || nickname.isEmpty()) continue;
            nicknameUsers.add(player);
        }
        return NicknameCommand.showNicknameResults(src, nicknameUsers, null);
    }

    private static int showNicknameResults(CommandSourceStack src, List<ServerPlayer> players, String searchTerm) {
        if (players.isEmpty()) {
            if (searchTerm != null) {
                src.sendFailure((Component)Component.literal((String)("No players found with nickname matching '" + searchTerm + "'")));
            } else {
                src.sendFailure((Component)Component.literal((String)"No players are currently using nicknames."));
            }
            return 0;
        }
        MutableComponent message = searchTerm != null ? Component.literal((String)("Found " + players.size() + " player(s) matching '" + searchTerm + "':\n")) : Component.literal((String)("Players using nicknames (" + players.size() + "):\n"));
        players.sort(Comparator.comparing(ServerPlayerCompat::getGameProfileName));
        for (ServerPlayer player : players) {
            String nick = PlayerNameStyler.nicknameManager.getNickname(player.getUUID());
            message.append((Component)Component.literal((String)"\u2022 "))
                    .append(ColorUtil.parseColoredText(nick))
                    .append((Component)Component.literal((String)" is "))
                    .append((Component)Component.literal((String)ServerPlayerCompat.getGameProfileName(player)))
                    .append((Component)Component.literal((String)"\n"));
        }
        src.sendSuccess(() -> message, false);
        return players.size();
    }

    private static CompletableFuture<Suggestions> suggestNicknames(CommandSourceStack source, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            String plainNick;
            String nickname = PlayerNameStyler.nicknameManager.getNickname(player.getUUID());
            if (nickname == null || nickname.isEmpty() || !(plainNick = ColorUtil.stripColorTags(nickname)).toLowerCase().contains(remaining)) continue;
            builder.suggest(plainNick);
        }
        return builder.buildFuture();
    }
}
