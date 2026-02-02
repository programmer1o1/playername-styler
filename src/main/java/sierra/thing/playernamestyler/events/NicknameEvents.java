package sierra.thing.playernamestyler.events;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.ModList;
import sierra.thing.playernamestyler.PlayerNameStyler;
import sierra.thing.playernamestyler.data.PlayerNameStylerConfig;
import sierra.thing.playernamestyler.integrations.LuckPermsBridge;
import sierra.thing.playernamestyler.util.ChatFormatUtil;
import sierra.thing.playernamestyler.util.ColorUtil;
import sierra.thing.playernamestyler.util.EntityCompat;
import sierra.thing.playernamestyler.util.ServerPlayerCompat;

public class NicknameEvents {
    private static Method armorStandSetMarker;
    private static Method displaySetBillboardConstraints;
    private static Method displaySetTransformationInterpolationDuration;
    private static Method displaySetPosRotInterpolationDuration;
    private static Method displaySetViewRange;
    private static Method textDisplaySetText;
    private static Method textDisplaySetLineWidth;
    private static Method textDisplaySetBackgroundColor;
    private static Method textDisplaySetTextOpacity;
    private static Field playerDisplayNameField;
    private static final Map<UUID, String> cachedPrefixes;
    private static final Map<UUID, String> cachedSuffixes;
    private static final Map<UUID, Entity> nameplateEntities;
    private static final String PREFIX = "nick_";
    private static final Set<UUID> spectators;

    @SubscribeEvent
    public void handleChat(ServerChatEvent ev) {
        ServerPlayer player = ev.getPlayer();
        String msg = ev.getMessage().getString();
        ev.setCanceled(true);
        MutableComponent formatted = ChatFormatUtil.formatChat(player, msg, PlayerNameStyler.nicknameManager);
        MinecraftServer server = ServerPlayerCompat.getServer(player);
        if (server == null) {
            return;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            target.sendSystemMessage((Component)formatted);
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent ev) {
        Player player = ev.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer player2 = (ServerPlayer)player;
            NicknameEvents.updateNicknameFor(player2);
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent ev) {
        Player player = ev.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer player2 = (ServerPlayer)player;
            NicknameEvents.removeArmorStand(player2);
            NicknameEvents.removeFromTeam(player2);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent ev) {
        Player player = ev.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer player2 = (ServerPlayer)player;
            NicknameEvents.updateNicknameFor(player2);
        }
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent ev) {
        Player player = ev.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer player2 = (ServerPlayer)player;
            NicknameEvents.updateNicknameFor(player2);
        }
    }

    @SubscribeEvent
    public void afterPlayerTick(PlayerTickEvent.Post ev) {
        Player player = ev.getEntity();
        if (player instanceof ServerPlayer) {
            boolean changed;
            ServerPlayer player2 = (ServerPlayer)player;
            NicknameEvents.syncArmorStand(player2);
            MinecraftServer server = ServerPlayerCompat.getServer(player2);
            if (server != null && NicknameEvents.isFirstPlayerInList(player2)) {
                NicknameEvents.hideArmorStandsFromSpectators(server);
                NicknameEvents.updateSpectatorState(server);
            }
            UUID id = player2.getUUID();
            String prefixNow = "";
            String suffixNow = "";
            if (ModList.get().isLoaded("luckperms")) {
                prefixNow = LuckPermsBridge.getPrefix(player2);
                suffixNow = LuckPermsBridge.getSuffix(player2);
            }
            boolean bl = changed = !Objects.equals(prefixNow, cachedPrefixes.get(id)) || !Objects.equals(suffixNow, cachedSuffixes.get(id));
            if (changed) {
                cachedPrefixes.put(id, prefixNow);
                cachedSuffixes.put(id, suffixNow);
                NicknameEvents.updateNicknameFor(player2);
                player2.refreshTabListName();
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        PlayerNameStyler.LOGGER.info("Removing {} nameplate entities during server shutdown", nameplateEntities.size());
        for (Entity entity : new ArrayList<Entity>(nameplateEntities.values())) {
            if (entity == null || entity.isRemoved()) continue;
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
        nameplateEntities.clear();
        cachedPrefixes.clear();
        cachedSuffixes.clear();
        spectators.clear();
    }

    @SubscribeEvent
    public void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Iterator<Map.Entry<UUID, Entity>> iterator = nameplateEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Entity> entry = iterator.next();
            Entity entity = entry.getValue();
            if (entity == null || entity.level() != event.getLevel() || entity.isRemoved()) continue;
            entity.remove(Entity.RemovalReason.DISCARDED);
            iterator.remove();
        }
    }

    @SubscribeEvent
    public void onNameFormat(PlayerEvent.NameFormat ev) {
        ServerPlayer player;
        String nick;
        Player player2 = ev.getEntity();
        if (player2 instanceof ServerPlayer && (nick = PlayerNameStyler.nicknameManager.getNickname((player = (ServerPlayer)player2).getUUID())) != null && !nick.isEmpty()) {
            ev.setDisplayname(ColorUtil.parseColoredText(nick));
        }
    }

    @SubscribeEvent
    public void onTablistFormat(PlayerEvent.TabListNameFormat ev) {
        Player player = ev.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer player2 = (ServerPlayer)player;
            ev.setDisplayName((Component)ChatFormatUtil.formatTablist(player2, PlayerNameStyler.nicknameManager));
        }
    }

    public static void updateNicknameFor(ServerPlayer player) {
        if (player == null || ServerPlayerCompat.getServer(player) == null) {
            return;
        }
        if (player.isSpectator()) {
            NicknameEvents.removeArmorStand(player);
            NicknameEvents.removeFromTeam(player);
            NicknameEvents.setDisplayName(player, null);
            return;
        }
        String nick = PlayerNameStyler.nicknameManager.getNickname(player.getUUID());
        String prefixNow = "";
        String suffixNow = "";
        if (ModList.get().isLoaded("luckperms")) {
            prefixNow = LuckPermsBridge.getPrefix(player);
            suffixNow = LuckPermsBridge.getSuffix(player);
        }
        boolean hasNickname = nick != null && !nick.isBlank();
        boolean hasPrefixSuffix = prefixNow != null && !prefixNow.isBlank() || suffixNow != null && !suffixNow.isBlank();
        boolean shouldCustomNameplate = hasNickname || hasPrefixSuffix;
        NicknameEvents.removeArmorStand(player);
        NicknameEvents.removeFromTeam(player);
        if (shouldCustomNameplate) {
            MutableComponent comp = ChatFormatUtil.formatNameplate(player, PlayerNameStyler.nicknameManager);
            NicknameEvents.setDisplayName(player, (Component)comp);
            NicknameEvents.addToNicknameTeam(player, hasNickname ? nick : ServerPlayerCompat.getGameProfileName(player));
            NicknameEvents.spawnNameplateEntity(player, (Component)comp);
            player.setCustomNameVisible(false);
        } else {
            NicknameEvents.setDisplayName(player, null);
            player.setCustomNameVisible(true);
        }
        NicknameEvents.refreshNameDisplay(player);
    }

    public static void refreshNameDisplay(ServerPlayer player) {
        if (player == null || ServerPlayerCompat.getServer(player) == null) {
            return;
        }
        try {
            Field hasTab = ServerPlayer.class.getDeclaredField("hasTabListName");
            hasTab.setAccessible(true);
            hasTab.set(player, false);
            Field tab = ServerPlayer.class.getDeclaredField("tabListDisplayName");
            tab.setAccessible(true);
            tab.set(player, null);
        }
        catch (Exception exception) {
            // empty catch block
        }
        player.refreshTabListName();
        player.refreshDisplayName();
        player.setCustomName(player.getDisplayName());
        player.setCustomNameVisible(PlayerNameStyler.nicknameManager.getNickname(player.getUUID()) != null);
    }

    public static void clearNickname(ServerPlayer player) {
        if (player == null || ServerPlayerCompat.getServer(player) == null) {
            return;
        }
        PlayerNameStyler.nicknameManager.removeNickname(player.getUUID());
        NicknameEvents.setDisplayName(player, null);
        NicknameEvents.removeFromTeam(player);
        NicknameEvents.removeArmorStand(player);
        player.setCustomNameVisible(true);
        NicknameEvents.refreshNameDisplay(player);
    }

    private static void setDisplayName(ServerPlayer player, Component displayName) {
        if (playerDisplayNameField == null) {
            return;
        }
        try {
            playerDisplayNameField.set(player, displayName);
            player.refreshDisplayName();
        }
        catch (Exception e) {
            PlayerNameStyler.LOGGER.error("couldn't set display name: " + e.getMessage());
        }
    }

    private static void addToNicknameTeam(ServerPlayer player, String nick) {
        String teamName;
        if (player == null) {
            return;
        }
        MinecraftServer server = ServerPlayerCompat.getServer(player);
        if (server == null) {
            return;
        }
        ServerScoreboard board = server.getScoreboard();
        PlayerTeam team = board.getPlayerTeam(teamName = PREFIX + player.getUUID().toString().substring(0, 8));
        if (team == null) {
            team = board.addPlayerTeam(teamName);
            team.setNameTagVisibility(Team.Visibility.NEVER);
            team.setCollisionRule(Team.CollisionRule.ALWAYS);
            team.setDeathMessageVisibility(Team.Visibility.ALWAYS);
        }
        team.setDisplayName(ColorUtil.parseColoredText(nick));
        board.addPlayerToTeam(ServerPlayerCompat.getGameProfileName(player), team);
    }

    private static void removeFromTeam(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = ServerPlayerCompat.getServer(player);
        if (server == null) {
            return;
        }
        ServerScoreboard board = server.getScoreboard();
        String teamName = PREFIX + player.getUUID().toString().substring(0, 8);
        board.removePlayerFromTeam(ServerPlayerCompat.getGameProfileName(player));
        PlayerTeam team = board.getPlayerTeam(teamName);
        if (team != null) {
            board.removePlayerTeam(team);
        }
    }

    private static void spawnNameplateEntity(ServerPlayer player, Component nickname) {
        String mode = (String)PlayerNameStylerConfig.NAMEPLATE_RENDERER.get();
        if (mode != null && mode.equalsIgnoreCase("text_display") && NicknameEvents.spawnTextDisplay(player, nickname)) {
            return;
        }
        NicknameEvents.spawnArmorStand(player, nickname);
    }

	    private static boolean spawnTextDisplay(ServerPlayer player, Component nickname) {
	        if (player == null || player.level() == null || player.connection == null) {
	            return false;
	        }
	        try {
	            Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, player.level());
	            display.setNoGravity(true);
	            display.setInvulnerable(true);
	            if (!NicknameEvents.configureTextDisplay(display, nickname)) {
	                return false;
	            }
	            display.setPos(player.getX(), player.getY() + (double)player.getBbHeight() + 0.25, player.getZ());
	            player.level().addFreshEntity((Entity)display);
	            nameplateEntities.put(player.getUUID(), display);
	            player.connection.send((Packet)new ClientboundRemoveEntitiesPacket(new int[]{display.getId()}));
	            return true;
        }
        catch (Throwable t) {
            PlayerNameStyler.LOGGER.warn("Text display nameplate failed; falling back to armor stand: {}", t.getMessage());
            return false;
	        }
	    }

    private static boolean configureTextDisplay(Display.TextDisplay display, Component nickname) {
        if (display == null || textDisplaySetText == null) {
            return false;
        }
        try {
            textDisplaySetText.invoke(display, nickname);
        }
        catch (Throwable t) {
            return false;
        }
        NicknameEvents.tryInvoke(displaySetBillboardConstraints, display, Display.BillboardConstraints.CENTER);
        NicknameEvents.tryInvoke(displaySetTransformationInterpolationDuration, display, 2);
        NicknameEvents.tryInvoke(displaySetPosRotInterpolationDuration, display, 2);
        NicknameEvents.tryInvoke(displaySetViewRange, display, 64.0f);
        NicknameEvents.tryInvoke(textDisplaySetLineWidth, display, 200);
        NicknameEvents.tryInvoke(textDisplaySetBackgroundColor, display, 0x40000000);
        NicknameEvents.tryInvoke(textDisplaySetTextOpacity, display, (byte)-1);
        return true;
    }

	    private static void tryInvoke(Method method, Object instance, Object ... args) {
	        if (method == null || instance == null) {
	            return;
	        }
	        try {
	            method.invoke(instance, args);
	        }
	        catch (Throwable t) {
	            // ignore
	        }
	    }

	    private static void spawnArmorStand(ServerPlayer player, Component nickname) {
	        if (player == null || player.level() == null) {
	            return;
	        }
	        ArmorStand stand = new ArmorStand(player.level(), player.getX(), player.getY() + (double)player.getBbHeight(), player.getZ());
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        try {
            if (armorStandSetMarker != null) {
                armorStandSetMarker.invoke((Object)stand, true);
            }
        }
        catch (Exception e) {
            PlayerNameStyler.LOGGER.error("couldn't set marker for stand: " + e.getMessage());
        }
        stand.setCustomName(nickname);
        stand.setCustomNameVisible(true);
        player.level().addFreshEntity((Entity)stand);
        nameplateEntities.put(player.getUUID(), stand);
        player.connection.send((Packet)new ClientboundRemoveEntitiesPacket(new int[]{stand.getId()}));
    }

    private static void syncArmorStand(ServerPlayer player) {
        String nick;
        if (player == null || player.level() == null) {
            return;
        }
        if (player.isSpectator() || player.isCrouching()) {
            NicknameEvents.removeArmorStand(player);
            return;
        }
        Entity entity = nameplateEntities.get(player.getUUID());
        if (entity != null && !entity.isRemoved() && player.level() == entity.level()) {
            double x = player.getX();
            double y = player.getY() + (double)player.getBbHeight();
            double z = player.getZ();
            if (!(entity instanceof ArmorStand)) {
                y += 0.25;
            }
            entity.teleportTo(x, y, z);
            if (entity instanceof ArmorStand stand) {
                stand.xo = x;
                stand.yo = y;
                stand.zo = z;
                stand.xOld = x;
                stand.yOld = y;
                stand.zOld = z;
                stand.setDeltaMovement(Vec3.ZERO);
                stand.setYRot(player.getYRot());
                stand.setXRot(0.0f);
                stand.yRotO = stand.getYRot();
                stand.xRotO = stand.getXRot();
                EntityCompat.markImpulse(stand);
            }
            player.connection.send((Packet)new ClientboundRemoveEntitiesPacket(new int[]{entity.getId()}));
        } else if (player.isAlive() && (nick = PlayerNameStyler.nicknameManager.getNickname(player.getUUID())) != null && !nick.isEmpty()) {
            NicknameEvents.removeArmorStand(player);
            NicknameEvents.updateNicknameFor(player);
        }
    }

    private static void removeArmorStand(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Entity entity = nameplateEntities.remove(player.getUUID());
        if (entity != null && entity.level() != null && !entity.isRemoved()) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    public static void hideArmorStandsFromSpectators(MinecraftServer server) {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (!viewer.isSpectator()) continue;
            for (Entity entity : nameplateEntities.values()) {
                if (entity == null || entity.isRemoved()) continue;
                viewer.connection.send((Packet)new ClientboundRemoveEntitiesPacket(new int[]{entity.getId()}));
            }
        }
    }

    public static String getNameplateEntityInfo(ServerPlayer player) {
        if (player == null) {
            return "none";
        }
        Entity entity = nameplateEntities.get(player.getUUID());
        if (entity == null) {
            return "none";
        }
        if (entity.isRemoved()) {
            return "removed";
        }
        return entity.getType() + " (" + entity.getClass().getSimpleName() + ", id=" + entity.getId() + ")";
    }

    private static void updateSpectatorState(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean wasSpec = spectators.contains(player.getUUID());
            boolean isSpec = player.isSpectator();
            if (wasSpec && !isSpec) {
                for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                    String nick = PlayerNameStyler.nicknameManager.getNickname(other.getUUID());
                    if (nick == null || nick.isEmpty() || other.isSpectator() || other.isCrouching()) continue;
                    NicknameEvents.removeArmorStand(other);
                    NicknameEvents.updateNicknameFor(other);
                }
            }
            if (isSpec) {
                spectators.add(player.getUUID());
                continue;
            }
            spectators.remove(player.getUUID());
        }
    }

    private static boolean isFirstPlayerInList(ServerPlayer player) {
        MinecraftServer server = ServerPlayerCompat.getServer(player);
        if (server == null) {
            return false;
        }
        return ((ServerPlayer)server.getPlayerList().getPlayers().get(0)).getId() == player.getId();
    }

    static {
        cachedPrefixes = new HashMap<UUID, String>();
        cachedSuffixes = new HashMap<UUID, String>();
        nameplateEntities = new HashMap<UUID, Entity>();
        spectators = new HashSet<UUID>();
        try {
            armorStandSetMarker = ArmorStand.class.getDeclaredMethod("setMarker", Boolean.TYPE);
            armorStandSetMarker.setAccessible(true);
            try {
                playerDisplayNameField = ServerPlayer.class.getSuperclass().getDeclaredField("displayname");
            } catch (NoSuchFieldException ignored) {
                playerDisplayNameField = ServerPlayer.class.getSuperclass().getDeclaredField("displayName");
            }
            playerDisplayNameField.setAccessible(true);
        }
        catch (NoSuchFieldException | NoSuchMethodException e) {
            PlayerNameStyler.LOGGER.error("couldn't init reflection: {}", (Object)e.getMessage());
        }
        try {
            displaySetBillboardConstraints = Display.class.getDeclaredMethod("setBillboardConstraints", Display.BillboardConstraints.class);
            displaySetBillboardConstraints.setAccessible(true);
        }
        catch (NoSuchMethodException ignored) {
            // ignore
        }
        try {
            displaySetTransformationInterpolationDuration = Display.class.getDeclaredMethod("setTransformationInterpolationDuration", Integer.TYPE);
            displaySetTransformationInterpolationDuration.setAccessible(true);
        }
        catch (NoSuchMethodException ignored) {
            // ignore
        }
        try {
            displaySetPosRotInterpolationDuration = Display.class.getDeclaredMethod("setPosRotInterpolationDuration", Integer.TYPE);
            displaySetPosRotInterpolationDuration.setAccessible(true);
        }
        catch (NoSuchMethodException ignored) {
            // ignore
        }
        try {
            displaySetViewRange = Display.class.getDeclaredMethod("setViewRange", Float.TYPE);
            displaySetViewRange.setAccessible(true);
        }
        catch (NoSuchMethodException ignored) {
            // ignore
        }
        try {
            textDisplaySetText = Display.TextDisplay.class.getDeclaredMethod("setText", Component.class);
            textDisplaySetText.setAccessible(true);
        }
        catch (NoSuchMethodException ignored) {
            // ignore
        }
        try {
            textDisplaySetLineWidth = Display.TextDisplay.class.getDeclaredMethod("setLineWidth", Integer.TYPE);
            textDisplaySetLineWidth.setAccessible(true);
        }
        catch (NoSuchMethodException ignored) {
            // ignore
        }
        try {
            textDisplaySetBackgroundColor = Display.TextDisplay.class.getDeclaredMethod("setBackgroundColor", Integer.TYPE);
            textDisplaySetBackgroundColor.setAccessible(true);
        }
        catch (NoSuchMethodException ignored) {
            // ignore
        }
        try {
            textDisplaySetTextOpacity = Display.TextDisplay.class.getDeclaredMethod("setTextOpacity", Byte.TYPE);
            textDisplaySetTextOpacity.setAccessible(true);
        }
        catch (NoSuchMethodException ignored) {
            // ignore
        }
    }
}
