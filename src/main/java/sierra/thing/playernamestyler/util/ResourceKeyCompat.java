package sierra.thing.playernamestyler.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ResourceKeyCompat {
    private static volatile Method resourceKeyLocationMethod;
    private static volatile Field resourceKeyLocationField;

    private ResourceKeyCompat() {
    }

    public static String locationToString(Object resourceKey) {
        if (resourceKey == null) {
            return "unknown";
        }

        try {
            Method m = resourceKeyLocationMethod;
            if (m == null) {
                m = resourceKey.getClass().getMethod("location");
                resourceKeyLocationMethod = m;
            }
            Object out = m.invoke(resourceKey);
            if (out != null) {
                return out.toString();
            }
        } catch (NoSuchMethodException ignored) {
            // fall back
        } catch (Throwable t) {
            // fall back
        }

        try {
            Field f = resourceKeyLocationField;
            if (f == null) {
                f = resourceKey.getClass().getDeclaredField("location");
                f.setAccessible(true);
                resourceKeyLocationField = f;
            }
            Object out = f.get(resourceKey);
            if (out != null) {
                return out.toString();
            }
        } catch (Throwable t) {
            // fall back
        }

        // Last resort: try to extract a usable id from toString().
        String s = String.valueOf(resourceKey);
        int slash = s.lastIndexOf(" / ");
        if (slash >= 0) {
            int start = slash + 3;
            int end = s.indexOf(']', start);
            if (end > start) {
                return s.substring(start, end);
            }
        }
        return s;
    }
}

