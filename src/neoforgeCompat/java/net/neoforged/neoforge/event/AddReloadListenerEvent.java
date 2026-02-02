package net.neoforged.neoforge.event;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Compat shim for mods compiled against early NeoForge 1.21.4 betas.
 *
 * <p>Modern NeoForge replaced this with {@link AddServerReloadListenersEvent}. Some mods (notably
 * LuckPerms 5.4.150) still reference this class, which will crash classloading on newer versions
 * unless a compatible type is present on the classpath.</p>
 *
 * <p>This class is compiled and its bytecode is embedded as a resource in PlayerName Styler, then
 * defined at runtime into the NeoForge module before LuckPerms initializes.</p>
 */
public class AddReloadListenerEvent extends Event {
    private final ReloadableServerResources serverResources;
    private final RegistryAccess registryAccess;

    public AddReloadListenerEvent(ReloadableServerResources serverResources, RegistryAccess registryAccess) {
        this.serverResources = serverResources;
        this.registryAccess = registryAccess;
    }

    public ReloadableServerResources getServerResources() {
        return this.serverResources;
    }

    public ICondition.IContext getConditionContext() {
        return this.serverResources.getConditionContext();
    }

    public RegistryAccess getRegistryAccess() {
        return this.registryAccess;
    }
}
