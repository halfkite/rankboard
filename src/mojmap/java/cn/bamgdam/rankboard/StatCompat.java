package cn.bamgdam.rankboard;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reads custom statistics whose identifier type changed from ResourceLocation to Identifier. */
final class StatCompat {
    private StatCompat() {}

    static long custom(ServerPlayer player, String statName) {
        try {
            Field statField = Stats.class.getField(statName);
            Object identifier = statField.get(null);
            Object customType = Stats.class.getField("CUSTOM").get(null);
            Object stat = null;
            for (Method method : customType.getClass().getMethods()) {
                if (!method.getName().equals("get") || method.getParameterCount() != 1
                        || !method.getParameterTypes()[0].isInstance(identifier)) continue;
                stat = method.invoke(customType, identifier);
                break;
            }
            if (stat == null) throw new NoSuchMethodException("Stats.CUSTOM.get(identifier)");

            Object stats = player.getStats();
            for (Method method : stats.getClass().getMethods()) {
                if (!method.getName().equals("getValue") || method.getParameterCount() != 1
                        || !method.getParameterTypes()[0].isInstance(stat)) continue;
                Object value = method.invoke(stats, stat);
                if (value instanceof Number number) return number.longValue();
            }
            throw new NoSuchMethodException("ServerStatsCounter.getValue(Stat)");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read custom statistic " + statName, exception);
        }
    }
}
