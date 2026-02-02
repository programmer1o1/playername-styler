package sierra.thing.playernamestyler.integrations;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import sierra.thing.playernamestyler.PlayerNameStyler;

public final class NeoForgeCompat {
    private static final String LEGACY_EVENT_CLASS = "net.neoforged.neoforge.event.AddReloadListenerEvent";
    private static final String LEGACY_EVENT_BYTES_RESOURCE = "playernamestyler/compat/AddReloadListenerEvent.class";
    private static volatile boolean attempted;
    private static volatile boolean bridgeRegistered;

    private NeoForgeCompat() {
    }

    public static void ensureLegacyAddReloadListenerEventPresent() {
        if (attempted) {
            return;
        }
        attempted = true;

        try {
            Class.forName(LEGACY_EVENT_CLASS, false, NeoForge.class.getClassLoader());
            return;
        } catch (ClassNotFoundException ignored) {
            // continue
        } catch (Throwable t) {
            PlayerNameStyler.LOGGER.warn("LuckPerms compat: failed checking legacy event class: {}", t.toString());
        }

        try (InputStream in = NeoForgeCompat.class.getClassLoader().getResourceAsStream(LEGACY_EVENT_BYTES_RESOURCE)) {
            if (in == null) {
                PlayerNameStyler.LOGGER.warn("LuckPerms compat: missing resource {}", LEGACY_EVENT_BYTES_RESOURCE);
                return;
            }

            byte[] bytes = in.readAllBytes();
            defineClassInNeoForgeEventPackage(bytes);
        } catch (Throwable t) {
            PlayerNameStyler.LOGGER.warn("LuckPerms compat: failed to define {}: {}", LEGACY_EVENT_CLASS, t.toString());
        }
    }

    private static void defineClassInNeoForgeEventPackage(byte[] bytes) throws Throwable {
        ClassLoader classLoader = NeoForge.class.getClassLoader();
        Class<?> anchor = Class.forName("net.neoforged.neoforge.event.RegisterCommandsEvent", false, classLoader);

        try {
            MethodHandles.privateLookupIn(anchor, MethodHandles.lookup()).defineClass(bytes);
            PlayerNameStyler.LOGGER.info("LuckPerms compat: defined {}", LEGACY_EVENT_CLASS);
            return;
        } catch (IllegalAccessException ignored) {
            // fall back to a trusted lookup (dev environment is typically permissive)
        }

        Lookup trusted = NeoForgeCompat.getTrustedLookup();
        trusted.in(anchor).defineClass(bytes);
        PlayerNameStyler.LOGGER.info("LuckPerms compat: defined {} (trusted lookup)", LEGACY_EVENT_CLASS);
    }

    private static Lookup getTrustedLookup() throws ReflectiveOperationException {
        Field f = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        f.setAccessible(true);
        return (Lookup) f.get(null);
    }

    public static void registerAddReloadListenerBridgeIfPresent() {
        if (bridgeRegistered) {
            return;
        }
        bridgeRegistered = true;

        ClassLoader classLoader = NeoForge.class.getClassLoader();
        Class<?> addServerReloadListenersEventClass;
        try {
            addServerReloadListenersEventClass = Class.forName("net.neoforged.neoforge.event.AddServerReloadListenersEvent", false, classLoader);
        } catch (ClassNotFoundException ignored) {
            return;
        }

        try {
            Method getServerResources = addServerReloadListenersEventClass.getMethod("getServerResources");
            Method getRegistryAccess = addServerReloadListenersEventClass.getMethod("getRegistryAccess");

            NeoForge.EVENT_BUS.addListener((Class) addServerReloadListenersEventClass, (Consumer) (Object event) -> {
                ensureLegacyAddReloadListenerEventPresent();

                try {
                    Class<?> legacyEventClass = Class.forName(LEGACY_EVENT_CLASS, false, classLoader);
                    Object serverResources = getServerResources.invoke(event);
                    Object registryAccess = getRegistryAccess.invoke(event);

                    Object legacyEvent = legacyEventClass
                            .getConstructors()[0]
                            .newInstance(serverResources, registryAccess);
                    NeoForge.EVENT_BUS.post((Event) legacyEvent);
                } catch (Throwable ignored) {
                    // best-effort compat
                }
            });

            PlayerNameStyler.LOGGER.info("LuckPerms compat: registered legacy reload listener bridge");
        } catch (Throwable t) {
            PlayerNameStyler.LOGGER.warn("LuckPerms compat: failed registering reload listener bridge: {}", t.toString());
        }
    }
}
