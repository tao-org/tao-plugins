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

    /**
     * A {@link TriFunction} that extracts a substring from a given string based on the specified start
     * index and length. If the provided arguments are invalid, the original string is returned.
     *
     * The function operates as follows:
     * - If the input string is null or empty, or if the start index or length are non-positive,
     *   or if the calculated end index (start index + length) exceeds the string length,
     *   then the input string is returned unmodified.
     * - Otherwise, it uses the {@link String#substring(int, int)} method to extract the substring.
     *
     * Note:
     * - The function uses {@link StringUtilities#isNullOrEmpty(String)} to check for null or empty strings.
     *
     * Input Parameters:
     * - {@code str}: The source string from which a substring is to be extracted.
     * - {@code i}: The zero-based starting index for extraction. Must be greater than 0.
     * - {@code j}: The length of the substring to extract. Must be greater than 0.
     *
     * Output:
     * - The extracted substring if the arguments are valid; otherwise, the original string.
     */
    public static final TriFunction<String, Integer, Integer, String> SUBSTRING = (str, i, j) -> {
        return (!StringUtilities.isNullOrEmpty(str) && i > 0 && j > 0 && i + j < str.length())
               ? str.substring(i, i + j)
               : str;
    };

    /**
     * A constant function that converts the provided string to uppercase.
     * If the input string is null or empty, the original string is returned unchanged.
     * Utilizes {@link StringUtilities#isNullOrEmpty(String)} to determine if the input is null or empty.
     */
    public static final Function<String, String> UPPER = (str) -> {
        return !StringUtilities.isNullOrEmpty(str) ? str.toUpperCase() : str;
    };

    /**
     * A function that converts a given string to lowercase.
     * If the input string is null or empty, it returns the input string unchanged.
     * This function makes use of the {@link StringUtilities#isNullOrEmpty(String)} method
     * to check whether the input is null or empty.
     */
    public static final Function<String, String> LOWER = (str) -> {
        return !StringUtilities.isNullOrEmpty(str) ? str.toLowerCase() : str;
    };

    /**
     * A function that attempts to convert a string representation of an integer
     * into its corresponding ASCII character. The integer value must be within
     * the printable ASCII range (32 to 126). If the input string is null, empty,
     * non-numeric, or the numeric value falls outside the printable ASCII range,
     * the function returns null.
     *
     * The function uses the following steps:
     * 1. Validates if the input string is not null or empty using
     *    {@link StringUtilities#isNullOrEmpty(String)}.
     * 2. Parses the input string to an integer.
     * 3. Checks whether the integer is within the printable ASCII range.
     * 4. Converts the integer to a character and returns the resulting string.
     *
     * @see StringUtilities#isNullOrEmpty(String)
     */
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
