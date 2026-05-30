package sierra.thing.playernamestyler.events;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.StyledFormat;
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
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Team;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.server.level.ServerLevel;
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
    private static final Map<UUID, Entity> belowNameEntities;
    private static final Map<UUID, Component> cachedBelowNameText;
    private static final String PREFIX = "nick_";
    private static final String NAMEPLATE_NBT_TAG = "playernamestyler_nameplate";
    // Vertical offsets, added to the player's eye-top, that stack lines above the head.
    private static final double NAMEPLATE_TEXT_OFFSET = 0.25;
    private static final double NAMEPLATE_STAND_OFFSET = 0.0;
    // When a below-name line is also shown, the nameplate moves up one row so the two
    // stack cleanly and the lower line clears the player model.
    private static final double NAMEPLATE_TEXT_OFFSET_STACKED = 0.50;
    private static final double NAMEPLATE_STAND_OFFSET_STACKED = 0.25;
    // The below-name line takes the nameplate's normal single-line height.
    private static final double BELOW_NAME_TEXT_OFFSET = 0.25;
    private static final double BELOW_NAME_STAND_OFFSET = 0.0;
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
            NicknameEvents.removeBelowName(player2);
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
        for (Entity entity : new ArrayList<Entity>(belowNameEntities.values())) {
            if (entity == null || entity.isRemoved()) continue;
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
        nameplateEntities.clear();
        belowNameEntities.clear();
        cachedBelowNameText.clear();
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
        Iterator<Map.Entry<UUID, Entity>> belowIterator = belowNameEntities.entrySet().iterator();
        while (belowIterator.hasNext()) {
            Map.Entry<UUID, Entity> entry = belowIterator.next();
            Entity entity = entry.getValue();
            if (entity == null || entity.level() != event.getLevel() || entity.isRemoved()) continue;
            entity.remove(Entity.RemovalReason.DISCARDED);
            cachedBelowNameText.remove(entry.getKey());
            belowIterator.remove();
        }
    }

    // Remove nameplate entities from the world before the level saves so they are
    // never written to disk. syncArmorStand detects the removal and re-creates them
    // on the next tick, so players see no interruption.
    @SubscribeEvent
    public void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        for (Entity entity : new ArrayList<Entity>(nameplateEntities.values())) {
            if (entity != null && entity.level() == event.getLevel() && !entity.isRemoved()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        for (Entity entity : new ArrayList<Entity>(belowNameEntities.values())) {
            if (entity != null && entity.level() == event.getLevel() && !entity.isRemoved()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
    }

    // Block any nameplate entities that survived a server crash from re-loading into
    // the world. They are identifiable by the NBT tag we write on every spawn.
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        // Only block entities restored from disk (e.g. crash orphans). Entities the
        // mod spawns this session also fire this event via addFreshEntity, but with
        // loadedFromDisk() == false; blocking those would kill every nameplate the
        // instant it is created.
        if (!event.loadedFromDisk()) {
            return;
        }
        Entity entity = event.getEntity();
        if ((entity instanceof ArmorStand || entity instanceof Display.TextDisplay)
                && entity.getPersistentData().contains(NAMEPLATE_NBT_TAG)) {
            event.setCanceled(true);
            PlayerNameStyler.LOGGER.info("Blocked orphaned nameplate entity from loading: {} at {}",
                    entity.getType(), entity.blockPosition());
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
        if (mode != null && mode.equalsIgnoreCase("text_display") && NicknameEvents.spawnTextDisplay(player, nickname, nameplateEntities, NAMEPLATE_TEXT_OFFSET)) {
            return;
        }
        NicknameEvents.spawnArmorStand(player, nickname, nameplateEntities, NAMEPLATE_STAND_OFFSET);
    }

    private static void spawnBelowNameEntity(ServerPlayer player, Component text) {
        String mode = (String)PlayerNameStylerConfig.NAMEPLATE_RENDERER.get();
        if (mode != null && mode.equalsIgnoreCase("text_display") && NicknameEvents.spawnTextDisplay(player, text, belowNameEntities, BELOW_NAME_TEXT_OFFSET)) {
            return;
        }
        NicknameEvents.spawnArmorStand(player, text, belowNameEntities, BELOW_NAME_STAND_OFFSET);
    }

	    private static boolean spawnTextDisplay(ServerPlayer player, Component nickname, Map<UUID, Entity> target, double yOffset) {
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
	            display.setPos(player.getX(), player.getY() + (double)player.getBbHeight() + yOffset, player.getZ());
	            display.getPersistentData().putBoolean(NAMEPLATE_NBT_TAG, true);
	            player.level().addFreshEntity((Entity)display);
	            target.put(player.getUUID(), display);
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

	    private static void spawnArmorStand(ServerPlayer player, Component nickname, Map<UUID, Entity> target, double yOffset) {
	        if (player == null || player.level() == null) {
	            return;
	        }
	        ArmorStand stand = new ArmorStand(player.level(), player.getX(), player.getY() + (double)player.getBbHeight() + yOffset, player.getZ());
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
        stand.getPersistentData().putBoolean(NAMEPLATE_NBT_TAG, true);
        player.level().addFreshEntity((Entity)stand);
        target.put(player.getUUID(), stand);
        player.connection.send((Packet)new ClientboundRemoveEntitiesPacket(new int[]{stand.getId()}));
    }

    private static void syncArmorStand(ServerPlayer player) {
        String nick;
        if (player == null || player.level() == null) {
            return;
        }
        if (player.isSpectator() || player.isCrouching()) {
            NicknameEvents.removeArmorStand(player);
            NicknameEvents.removeBelowName(player);
            return;
        }
        Entity entity = nameplateEntities.get(player.getUUID());
        if (entity != null && !entity.isRemoved() && player.level() == entity.level()) {
            boolean stacked = NicknameEvents.hasBelowNameEntity(player);
            NicknameEvents.positionEntity(player, entity,
                    stacked ? NAMEPLATE_TEXT_OFFSET_STACKED : NAMEPLATE_TEXT_OFFSET,
                    stacked ? NAMEPLATE_STAND_OFFSET_STACKED : NAMEPLATE_STAND_OFFSET);
            player.connection.send((Packet)new ClientboundRemoveEntitiesPacket(new int[]{entity.getId()}));
        } else if (entity != null && entity.isRemoved()) {
            // Entity was removed from the world (e.g. pre-save cleanup) but is still
            // tracked in our map. Clear the stale reference and re-create immediately.
            nameplateEntities.remove(player.getUUID());
            NicknameEvents.updateNicknameFor(player);
        } else if (player.isAlive() && (nick = PlayerNameStyler.nicknameManager.getNickname(player.getUUID())) != null && !nick.isEmpty()) {
            NicknameEvents.removeArmorStand(player);
            NicknameEvents.updateNicknameFor(player);
        }
        NicknameEvents.syncBelowName(player);
    }

    // Glue a tracked entity to the player. The offset added to the player's eye-top
    // depends on the entity kind so multiple lines (nameplate, below-name) can stack.
    private static void positionEntity(ServerPlayer player, Entity entity, double textOffset, double standOffset) {
        double x = player.getX();
        double z = player.getZ();
        double y = player.getY() + (double)player.getBbHeight() + (entity instanceof ArmorStand ? standOffset : textOffset);
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
    }

    // Mirrors the vanilla "below_name" scoreboard display slot as a second floating
    // line. Vanilla anchors that text to the real name tag, which this mod hides, so it
    // has to be re-created. Only shown while the mod is managing the player's nameplate
    // and the player actually has a score in the displayed objective.
    private static void syncBelowName(ServerPlayer player) {
        if (player == null || player.level() == null) {
            return;
        }
        if (!Boolean.TRUE.equals(PlayerNameStylerConfig.SHOW_BELOW_NAME.get())) {
            NicknameEvents.removeBelowName(player);
            return;
        }
        Entity nameplate = nameplateEntities.get(player.getUUID());
        if (nameplate == null || nameplate.isRemoved()) {
            NicknameEvents.removeBelowName(player);
            return;
        }
        Component desired = NicknameEvents.computeBelowNameText(player);
        if (desired == null) {
            NicknameEvents.removeBelowName(player);
            return;
        }
        UUID id = player.getUUID();
        Entity entity = belowNameEntities.get(id);
        if (entity == null || entity.isRemoved() || entity.level() != player.level()) {
            if (entity != null) {
                NicknameEvents.removeBelowName(player);
            }
            NicknameEvents.spawnBelowNameEntity(player, desired);
            cachedBelowNameText.put(id, desired);
            return;
        }
        NicknameEvents.positionEntity(player, entity, BELOW_NAME_TEXT_OFFSET, BELOW_NAME_STAND_OFFSET);
        if (!Objects.equals(cachedBelowNameText.get(id), desired)) {
            NicknameEvents.applyBelowNameText(entity, desired);
            cachedBelowNameText.put(id, desired);
        }
        if (player.connection != null) {
            player.connection.send((Packet)new ClientboundRemoveEntitiesPacket(new int[]{entity.getId()}));
        }
    }

    // Builds the below-name line exactly as vanilla's PlayerRenderer does:
    // "<score> <objective display name>". Returns null when no objective is shown in
    // the below_name slot or the player has no score entry for it.
    private static Component computeBelowNameText(ServerPlayer player) {
        MinecraftServer server = ServerPlayerCompat.getServer(player);
        if (server == null) {
            return null;
        }
        ServerScoreboard board = server.getScoreboard();
        Objective objective = board.getDisplayObjective(DisplaySlot.BELOW_NAME);
        if (objective == null) {
            return null;
        }
        // Match vanilla PlayerRenderer: render whenever an objective occupies the slot,
        // defaulting a missing score to 0 (a just-spawned player may have no score row yet).
        ReadOnlyScoreInfo info = board.getPlayerScoreInfo(player, objective);
        MutableComponent value = ReadOnlyScoreInfo.safeFormatValue(info, objective.numberFormatOrDefault(StyledFormat.NO_STYLE));
        return Component.empty().append((Component)value).append((Component)CommonComponents.SPACE).append(objective.getDisplayName());
    }

    private static void applyBelowNameText(Entity entity, Component text) {
        if (entity instanceof Display.TextDisplay) {
            NicknameEvents.tryInvoke(textDisplaySetText, entity, text);
        } else if (entity instanceof ArmorStand stand) {
            stand.setCustomName(text);
            stand.setCustomNameVisible(true);
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

    private static boolean hasBelowNameEntity(ServerPlayer player) {
        Entity entity = belowNameEntities.get(player.getUUID());
        return entity != null && !entity.isRemoved();
    }

    private static void removeBelowName(ServerPlayer player) {
        if (player == null) {
            return;
        }
        cachedBelowNameText.remove(player.getUUID());
        Entity entity = belowNameEntities.remove(player.getUUID());
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
            for (Entity entity : belowNameEntities.values()) {
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

    // Forcibly removes all tracked nameplate entities from the world and scans every
    // loaded level for any orphaned ones (e.g. left over from a previous crash, or
    // from a pre-fix version of the mod that did not write the identification tag).
    // Returns the total number of entities removed.
    public static int cleanupAllNameplates(MinecraftServer server) {
        int count = 0;
        Set<UUID> trackedUuids = new HashSet<>(nameplateEntities.keySet());
        trackedUuids.addAll(belowNameEntities.keySet());
        for (Entity entity : new ArrayList<Entity>(nameplateEntities.values())) {
            if (entity != null && !entity.isRemoved()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
                count++;
            }
        }
        for (Entity entity : new ArrayList<Entity>(belowNameEntities.values())) {
            if (entity != null && !entity.isRemoved()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
                count++;
            }
        }
        nameplateEntities.clear();
        belowNameEntities.clear();
        cachedBelowNameText.clear();
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> orphans = new ArrayList<>();
            for (Entity entity : level.getEntities().getAll()) {
                if (entity.isRemoved() || trackedUuids.contains(entity.getUUID())) continue;
                if (!(entity instanceof ArmorStand) && !(entity instanceof Display.TextDisplay)) continue;
                if (entity.getPersistentData().contains(NAMEPLATE_NBT_TAG) || looksLikeLegacyNameplate(entity)) {
                    orphans.add(entity);
                }
            }
            for (Entity orphan : orphans) {
                orphan.remove(Entity.RemovalReason.DISCARDED);
                count++;
            }
        }
        return count;
    }

    // Heuristic for entities that the mod spawned before the NBT identification tag
    // was added. We look for the specific combination of flags this mod sets on every
    // nameplate entity, which is unlikely to match unrelated armor stands or displays.
    private static boolean looksLikeLegacyNameplate(Entity entity) {
        if (entity instanceof ArmorStand stand) {
            return stand.isInvisible()
                    && stand.isNoGravity()
                    && stand.isInvulnerable()
                    && stand.isSilent()
                    && stand.getCustomName() != null;
        }
        if (entity instanceof Display.TextDisplay) {
            return entity.isNoGravity() && entity.isInvulnerable();
        }
        return false;
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
        belowNameEntities = new HashMap<UUID, Entity>();
        cachedBelowNameText = new HashMap<UUID, Component>();
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
