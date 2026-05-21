package io.github.baekchangjoon.hotspotanalysis.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@code ${VAR}} placeholder substitution used while loading
 * YAML configuration files.
 */
class EnvironmentVariableResolverTest {

    @Test
    @DisplayName("substitutes a single placeholder with the environment value")
    void shouldSubstituteSinglePlaceholder() {
        EnvironmentVariableResolver resolver = withEnv(Map.of("GITHUB_TOKEN", "secret-abc"));

        String result = resolver.resolve("token: ${GITHUB_TOKEN}");

        assertThat(result).isEqualTo("token: secret-abc");
    }

    @Test
    @DisplayName("substitutes multiple placeholders preserving surrounding text")
    void shouldSubstituteMultiplePlaceholders() {
        EnvironmentVariableResolver resolver = withEnv(
                Map.of("OWNER", "acme", "REPO", "widget", "TOKEN", "xyz"));

        String result = resolver.resolve("""
                owner: ${OWNER}
                repo: ${REPO}
                token: ${TOKEN}
                """);

        assertThat(result).contains("owner: acme", "repo: widget", "token: xyz");
    }

    @Test
    @DisplayName("returns the input unchanged when no placeholder is present")
    void shouldReturnInputUnchangedWithoutPlaceholders() {
        EnvironmentVariableResolver resolver = withEnv(Map.of());

        String input = "key: value\nlist: [a, b, c]";

        assertThat(resolver.resolve(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("throws ConfigLoadException when referenced variable is missing")
    void shouldFailWhenVariableMissing() {
        EnvironmentVariableResolver resolver = withEnv(Map.of());

        assertThatThrownBy(() -> resolver.resolve("token: ${MISSING_VAR}"))
                .isInstanceOf(ConfigLoadException.class)
                .hasMessageContaining("MISSING_VAR");
    }

    @Test
    @DisplayName("ignores placeholders with invalid variable names (e.g. lowercase, digits-first)")
    void shouldIgnoreInvalidPlaceholderNames() {
        EnvironmentVariableResolver resolver = withEnv(Map.of());

        // Only [A-Z_][A-Z0-9_]* is treated as a variable reference.
        String input = "amount: ${100} description: ${not-a-var}";

        assertThat(resolver.resolve(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("leaves placeholders inside YAML comment lines untouched")
    void shouldIgnoreCommentLines() {
        EnvironmentVariableResolver resolver = withEnv(Map.of("REAL_VAR", "actual"));

        String input = """
                # token: ${MISSING_VAR}
                owner: ${REAL_VAR}
                  # nested-comment: ${ANOTHER_MISSING}
                """;

        String result = resolver.resolve(input);

        assertThat(result)
                .contains("# token: ${MISSING_VAR}")
                .contains("owner: actual")
                .contains("# nested-comment: ${ANOTHER_MISSING}");
    }

    private static EnvironmentVariableResolver withEnv(Map<String, String> env) {
        return new EnvironmentVariableResolver(env::get);
    }
}
