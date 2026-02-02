package sierra.thing.playernamestyler.util;

import java.lang.reflect.Field;
import net.minecraft.world.entity.Entity;

public final class EntityCompat {
    private static volatile boolean checked;
    private static volatile Field hasImpulseField;

    private EntityCompat() {
    }

    public static void markImpulse(Entity entity) {
        if (entity == null) {
            return;
        }
        Field f = EntityCompat.getHasImpulseField();
        if (f == null) {
            return;
        }
        try {
            f.setBoolean(entity, true);
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private static Field getHasImpulseField() {
        if (checked) {
            return hasImpulseField;
        }
        checked = true;

        try {
            Field f = Entity.class.getDeclaredField("hasImpulse");
            f.setAccessible(true);
            hasImpulseField = f;
        } catch (Throwable ignored) {
            hasImpulseField = null;
        }

        return hasImpulseField;
    }
}

