package dev.campaign.kernel.engine;

import dev.campaign.kernel.api.CommandSpec;
import dev.campaign.kernel.api.RequiredTag;
import dev.campaign.kernel.id.EntityId;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.LocationId;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Describes a command's parameters by reflecting over its record components, so a generic UI can build the
 * prompt flow (pick a unit, pick a path, enter a number) without any per-command UI code or schema file.
 */
public final class CommandSchema {
    private CommandSchema() {}

    public enum ParamType { ENTITY, LOCATION, FACTION, TEXT, INTEGER, DECIMAL, FLAG, OTHER }

    public record Param(String name, ParamType type, boolean list, String requires) {}

    public record Descriptor(String command, CommandSpec.Mode mode, CommandSpec.Who who, Set<String> phases, List<Param> params) {}

    public static Descriptor describe(CommandSpec<?> spec) {
        return new Descriptor(spec.id(), spec.mode(), spec.who(), spec.phases(), params(spec.type()));
    }

    public static List<Param> params(Class<?> commandType) {
        List<Param> out = new ArrayList<>();
        if (!commandType.isRecord()) return out;
        for (RecordComponent rc : commandType.getRecordComponents()) {
            Type generic = rc.getGenericType();
            boolean list = false;
            Class<?> raw = rc.getType();
            if (generic instanceof ParameterizedType pt && pt.getRawType() == List.class
                    && pt.getActualTypeArguments()[0] instanceof Class<?> element) {
                list = true;
                raw = element;
            }
            RequiredTag requires = rc.getAnnotation(RequiredTag.class);
            out.add(new Param(rc.getName(), typeOf(raw), list, requires == null ? null : requires.value()));
        }
        return out;
    }

    private static ParamType typeOf(Class<?> type) {
        if (type == EntityId.class) return ParamType.ENTITY;
        if (type == LocationId.class) return ParamType.LOCATION;
        if (type == FactionId.class) return ParamType.FACTION;
        if (type == String.class) return ParamType.TEXT;
        if (type == int.class || type == long.class || type == Integer.class || type == Long.class) return ParamType.INTEGER;
        if (type == double.class || type == Double.class) return ParamType.DECIMAL;
        if (type == boolean.class || type == Boolean.class) return ParamType.FLAG;
        return ParamType.OTHER;
    }
}
