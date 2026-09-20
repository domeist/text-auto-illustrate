package autoillustrate.cli;

import java.util.LinkedHashMap;
import java.util.Map;

/** Parses {@code --name value} and {@code --flag} arguments. */
final class Options {

    private Options() {
    }

    /** Parses arguments after the command name. Unknown names are left to the caller. */
    static Map<String, String> parse(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("expected an option, found: " + arg);
            }
            String name = arg.substring(2);
            boolean hasValue = i + 1 < args.length && !args[i + 1].startsWith("--");
            options.put(name, hasValue ? args[++i] : "");
        }
        return options;
    }

    static int intValue(Map<String, String> options, String name, int fallback) {
        String value = options.get(name);
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--%s must be a whole number, got: %s"
                    .formatted(name, value));
        }
    }

    static float floatValue(Map<String, String> options, String name, float fallback) {
        String value = options.get(name);
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--%s must be a number, got: %s"
                    .formatted(name, value));
        }
    }
}
