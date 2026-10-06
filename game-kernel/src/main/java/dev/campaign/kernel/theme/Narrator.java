package dev.campaign.kernel.theme;

import dev.campaign.kernel.api.Event;
import dev.campaign.kernel.event.ViewEvent;
import dev.campaign.kernel.id.Id;
import dev.campaign.kernel.model.Reason;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns events into prose using a Theme. The same event log reads as a fantasy chronicle or a freight manifest
 * depending on the theme. Template key = "event." + event class simple name; placeholders = record component names.
 * Any value that has a "term.&lt;value&gt;" entry is translated, so ids and kinds are themed too.
 */
public final class Narrator {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final Theme theme;

    public Narrator(Theme theme) {
        this.theme = theme;
    }

    public String line(Event event) {
        Map<String, String> args = args(event);
        String template = theme.lookup("event." + event.getClass().getSimpleName())
                .orElse(event.getClass().getSimpleName() + " " + args);
        return fill(template, args);
    }

    public String reason(Reason reason) {
        Map<String, String> args = new LinkedHashMap<>();
        reason.args().forEach((k, v) -> args.put(k, theme.lookup("term." + v).orElse(v)));
        return fill(theme.lookup("reason." + reason.code()).orElse(reason.code() + " " + args), args);
    }

    /** Markdown transcript grouped by turn. */
    public String transcript(List<ViewEvent> events) {
        StringBuilder sb = new StringBuilder();
        int turn = -1;
        for (ViewEvent e : events) {
            if (e.turn() != turn) {
                turn = e.turn();
                sb.append(turn == 0 ? "\n## Setup\n\n" : "\n## Turn " + turn + "\n\n");
            }
            sb.append("- ").append(line(e.event())).append('\n');
        }
        return sb.toString().strip() + "\n";
    }

    private Map<String, String> args(Event event) {
        Map<String, String> out = new LinkedHashMap<>();
        Class<?> type = event.getClass();
        if (!type.isRecord()) return out;
        for (RecordComponent rc : type.getRecordComponents()) {
            try {
                Method accessor = rc.getAccessor();
                accessor.setAccessible(true);
                out.put(rc.getName(), display(accessor.invoke(event)));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return out;
    }

    private String display(Object value) {
        if (value == null) return "";
        if (value instanceof Reason r) return reason(r);
        if (value instanceof Collection<?> c) return c.stream().map(this::display).collect(Collectors.joining(", "));
        if (value instanceof Map<?, ?> m) {
            return m.entrySet().stream().map(e -> display(e.getKey()) + "=" + display(e.getValue()))
                    .collect(Collectors.joining(", "));
        }
        String raw = value instanceof Id id ? id.value() : String.valueOf(value);
        return theme.lookup("term." + raw).orElse(raw);
    }

    private static String fill(String template, Map<String, String> args) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String replacement = args.getOrDefault(m.group(1), m.group());
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
