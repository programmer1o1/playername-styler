package sierra.thing.playernamestyler.api;

import net.minecraft.server.level.ServerPlayer;
import sierra.thing.playernamestyler.data.NicknameManager;

public final class PlaceholderContext {
    private final ServerPlayer player;
    private final NicknameManager nicknameManager;
    private final String rawMessage;
    private final boolean withMessage;
    private final boolean withColon;

    public PlaceholderContext(ServerPlayer player, NicknameManager nicknameManager, String rawMessage, boolean withMessage, boolean withColon) {
        this.player = player;
        this.nicknameManager = nicknameManager;
        this.rawMessage = rawMessage;
        this.withMessage = withMessage;
        this.withColon = withColon;
    }

    public ServerPlayer getPlayer() {
        return this.player;
    }

    public NicknameManager getNicknameManager() {
        return this.nicknameManager;
    }

    public String getRawMessage() {
        return this.rawMessage;
    }

    public boolean isWithMessage() {
        return this.withMessage;
    }

    public boolean isWithColon() {
        return this.withColon;
    }
}

