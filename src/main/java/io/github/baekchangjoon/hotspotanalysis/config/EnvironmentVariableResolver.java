package io.github.baekchangjoon.hotspotanalysis.config;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes {@code ${VAR_NAME}} placeholders in a YAML payload with values
 * pulled from a caller-supplied environment lookup.
 *
 * <p>The pattern intentionally matches only upper-case shell-style identifiers
 * (regex {@code [A-Z_][A-Z0-9_]*}) so that arbitrary substrings such as
 * {@code ${100}} or {@code ${non-shell}} are left untouched. Missing variables
 * fail fast with a {@link ConfigLoadException} so the caller never proceeds
 * with an unresolved configuration.</p>
 */
@Component
public class EnvironmentVariableResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z_][A-Z0-9_]*)}");

    private final Function<String, String> environmentLookup;

    public EnvironmentVariableResolver() {
        this(System::getenv);
    }

    public EnvironmentVariableResolver(Function<String, String> environmentLookup) {
        this.environmentLookup = Objects.requireNonNull(environmentLookup, "environmentLookup");
    }

    public String resolve(String content) {
        Objects.requireNonNull(content, "content");
        String[] lines = content.split("\n", -1);
        StringBuilder out = new StringBuilder(content.length());
        for (int i = 0; i < lines.length; i++) {
            out.append(resolveLine(lines[i]));
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private String resolveLine(String line) {
        if (isCommentLine(line)) {
            return line;
        }
        Matcher matcher = PLACEHOLDER.matcher(line);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String variable = matcher.group(1);
            String value = environmentLookup.apply(variable);
            if (value == null) {
                throw new ConfigLoadException(
                        "Environment variable not found while resolving config: " + variable);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static boolean isCommentLine(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '#') {
                return true;
            }
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return false;
    }
}
