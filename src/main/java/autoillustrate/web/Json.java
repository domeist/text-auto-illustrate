package autoillustrate.web;

import autoillustrate.retrieve.Result;

import java.util.List;

/**
 * Writes the small amount of JSON the web interface needs.
 *
 * <p>The project has no JSON dependency and needs one response shape, so the
 * few hundred bytes of encoding live here rather than pulling in a library.
 * Captions are arbitrary Wikipedia text, so escaping is the part that matters.
 */
final class Json {

    private Json() {
    }

    /** Serialises a result list along with the query terms that produced it. */
    static String searchResponse(List<String> queryTerms, List<Result> results) {
        StringBuilder out = new StringBuilder(results.size() * 200 + 64);
        out.append("{\"queryTerms\":");
        array(out, queryTerms);
        out.append(",\"results\":[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            Result r = results.get(i);
            out.append("{\"rank\":").append(i + 1)
                    .append(",\"id\":");
            string(out, r.id());
            out.append(",\"caption\":");
            string(out, r.caption());
            out.append(",\"imageUrl\":");
            string(out, r.imageUrl());
            out.append(",\"pageUrl\":");
            string(out, r.pageUrl());
            out.append(",\"score\":").append(round(r.score()))
                    .append('}');
        }
        return out.append("]}").toString();
    }

    /** Serialises an error as {"error": "..."}. */
    static String error(String message) {
        StringBuilder out = new StringBuilder(message.length() + 16);
        out.append("{\"error\":");
        string(out, message);
        return out.append('}').toString();
    }

    private static void array(StringBuilder out, List<String> values) {
        out.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            string(out, values.get(i));
        }
        out.append(']');
    }

    /**
     * Appends a JSON string literal.
     *
     * <p>Escapes the two characters that would end the literal, the control
     * characters JSON forbids raw, and {@code <} so a caption can never close
     * the script element the response might be read into.
     */
    private static void string(StringBuilder out, String value) {
        if (value == null) {
            out.append("null");
            return;
        }
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /** Scores carry more precision than anyone reads; four places is plenty. */
    private static String round(double score) {
        return String.format("%.4f", score);
    }
}
