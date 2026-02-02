package sierra.thing.playernamestyler.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlaceholderRegistry {
    private static final ConcurrentHashMap<String, PlaceholderResolver> PLACEHOLDERS = new ConcurrentHashMap<String, PlaceholderResolver>();

    private PlaceholderRegistry() {
    }

    public static void register(String placeholder, PlaceholderResolver resolver) {
        if (placeholder == null || placeholder.isBlank() || resolver == null) {
            return;
        }
        PLACEHOLDERS.put(placeholder, resolver);
    }

    public static void unregister(String placeholder) {
        if (placeholder == null) {
            return;
        }
        PLACEHOLDERS.remove(placeholder);
    }

    public static Map<String, PlaceholderResolver> getAll() {
        return Map.copyOf(PLACEHOLDERS);
    }

    public static void fillPlaceholders(Map<String, String> into, PlaceholderContext ctx) {
        for (Map.Entry<String, PlaceholderResolver> entry : PLACEHOLDERS.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }
            try {
                String value = entry.getValue().resolve(ctx);
                if (value != null) {
                    into.put(key, value);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}

