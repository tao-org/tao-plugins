package ro.cs.tao.functions;

import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.utils.TriFunction;

import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StringResolver is an implementation of the {@link TokenResolver} interface designed to process
 * and transform string expressions containing specific token patterns. The resolution process involves
 * detecting and replacing tokens within an input string based on predefined transformation functions.
 *
 * The supported token patterns and their corresponding transformations are:
 * - SUBSTRING: Extracts a substring from a given string based on start and length parameters.
 * - UPPER: Converts the entire string to uppercase.
 * - LOWER: Converts the entire string to lowercase.
 * - CHAR: Converts a numeric value into its corresponding ASCII character representation.
 *
 * Thread Safety:
 * This class is thread-safe as it does not maintain mutable internal state.
 *
 * Error Handling:
 * If invalid or malformed input is encountered during token resolution, the method logs a warning
 * and gracefully returns the input string unchanged.
 *
 * Responsibilities:
 * - Define token patterns using regular expressions.
 * - Resolve and transform detected tokens from an input string expression.
 *
 * Example Scenarios:
 * - Transforming placeholders in strings into substrings, uppercase, or lowercase formats.
 * - Resolving numeric values into their ASCII character equivalents.
 */
public class StringResolver implements TokenResolver {
    private static final Pattern substringPattern = Pattern.compile("(SUBSTRING\\(([A-Za-z0-9_\\/\\\\.-]+),(\\d+),(\\d+)\\))");
    private static final Pattern upperPattern = Pattern.compile("(UPPER\\(([A-Za-z0-9_\\/\\\\.-]+)\\))");
    private static final Pattern lowerPattern = Pattern.compile("(LOWER\\(([A-Za-z0-9_\\/\\\\.-]+)\\))");
    private static final Pattern charPattern = Pattern.compile("(CHAR\\((\\d{2,3})\\))");

    public static final TriFunction<String, Integer, Integer, String> SUBSTRING = (str, i, j) -> {
        return (!StringUtilities.isNullOrEmpty(str) && i > 0 && j > 0 && i + j < str.length())
               ? str.substring(i, i + j)
               : str;
    };

    public static final Function<String, String> UPPER = (str) -> {
        return !StringUtilities.isNullOrEmpty(str) ? str.toUpperCase() : str;
    };

    public static final Function<String, String> LOWER = (str) -> {
        return !StringUtilities.isNullOrEmpty(str) ? str.toLowerCase() : str;
    };

    public static final Function<String, String> CHAR = (str) -> {
        if (!StringUtilities.isNullOrEmpty(str)) {
            return null;
        }
        try {
            int value = Integer.parseInt(str);
            if (value < 32 || value > 126) {
                return null;
            }
            char chr = (char) value;
            return new String(new char[] { chr });
        } catch (Throwable t) {
            return null;
        }
    };

    @Override
    public String resolve(String expression) {
        if (StringUtilities.isNullOrEmpty(expression)) {
            return expression;
        }
        String transformed = expression;
        try {
            Matcher matcher = substringPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1),
                                                  SUBSTRING.apply(matcher.group(2),
                                                                  Integer.parseInt(matcher.group(3)),
                                                                  Integer.parseInt(matcher.group(4))));
            }
            matcher = upperPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), UPPER.apply(matcher.group(2)));
            }
            matcher = lowerPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), LOWER.apply(matcher.group(2)));
            }
            matcher = charPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), CHAR.apply(matcher.group(2)));
            }
        } catch (Throwable t) {
            Logger.getLogger(StringResolver.class.getName()).warning(t.getMessage());
        }
        return transformed;
    }
}
