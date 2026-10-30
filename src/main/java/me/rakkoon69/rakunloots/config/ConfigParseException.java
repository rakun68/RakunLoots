package me.rakkoon69.rakunloots.config;

import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.OptionalInt;

/**
 * A config file that is not valid YAML and has no warning to carry that: the globals, the worlds, or a language file.
 * The message names the file and the line, and when that line holds {@code {} it carries the hint to quote the value.
 */
final class ConfigParseException extends YAMLException {

    private final String file;
    private final OptionalInt quoteLine;

    private ConfigParseException(String message, YAMLException cause, String file, OptionalInt quoteLine) {
        super(message, cause);
        this.file = file;
        this.quoteLine = quoteLine;
    }

    static ConfigParseException of(String file, String content, YAMLException cause) {
        String problem = cause instanceof MarkedYAMLException marked && marked.getProblem() != null
                ? marked.getProblem() : String.valueOf(cause.getMessage());
        OptionalInt line = OptionalInt.empty();
        if (cause instanceof MarkedYAMLException marked) {
            if (marked.getProblemMark() != null) {
                line = OptionalInt.of(marked.getProblemMark().getLine() + 1);
            } else if (marked.getContextMark() != null) {
                line = OptionalInt.of(marked.getContextMark().getLine() + 1);
            }
        }
        String where = file + (line.isPresent() ? " line " + line.getAsInt() : "");
        return new ConfigParseException(where + ": " + problem, cause, file,
                ConfigValues.quoteLine(content, cause));
    }

    String file() {
        return file;
    }

    OptionalInt quoteLine() {
        return quoteLine;
    }

    /**
     * This error with the hint added to its message, when the error line holds {@code {}.
     *
     * @param hint the {@code yaml_quote_hint} text, with {@code {line}} still in it
     */
    ConfigParseException withHint(String hint) {
        if (quoteLine.isEmpty() || hint.isBlank()) {
            return this;
        }
        return new ConfigParseException(getMessage() + " " + hint.replace("{line}", Integer.toString(quoteLine.getAsInt())),
                this, file, quoteLine);
    }
}
