package ro.cs.tao.functions;

import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.StringUtilities;

import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves and transforms string expressions containing date and time-related tokens
 * into their respective component values (e.g., year, month, day, etc.).
 *
 * This class implements the {@code TokenResolver} interface and processes expressions
 * containing specific date and time function-like tokens (e.g., YEAR(), MONTH(), DAY(), etc.).
 * These tokens are replaced with the corresponding extracted values from the input string
 * representing a date or time. The supported patterns and transformations are applied using
 * regular expressions and predefined logic.
 *
 * Date Tokens:
 * - YEAR(date): Extracts the year component from the given date.
 * - MONTH(date): Extracts the month component from the given date and pads it to two digits.
 * - DAY(date): Extracts the day component from the given date and pads it to two digits.
 *
 * Time Tokens:
 * - HOUR(time): Extracts the hour component from the given time.
 * - MINUTE(time): Extracts the minute component from the given time and pads it to two digits.
 * - SECOND(time): Extracts the second component from the given time and pads it to two digits.
 *
 * Behavior:
 * - If the input string is null or empty, the method returns the input unchanged.
 * - For each supported pattern, it matches the token in the input expression,
 *   extracts the value, applies transformations, and replaces the token with the result.
 * - If any error occurs during the processing, a warning is logged, and processing continues.
 *
 * Thread Safety:
 * The class is thread-safe for multiple read operations but does not guarantee thread safety
 * for concurrent writes to modify any existing implementation. The logic uses thread-safe components.
 *
 * Example Use Cases:
 * - Parsing and extracting specific date components from a date string (e.g., "2023-10-12").
 * - Processing time tokens within a formatted string (e.g., "HOUR(15:30:45)").
 *
 * Error Handling:
 * If an error occurs during processing (e.g., malformed input or invalid token),
 * the input expression is returned as is, and a warning is logged. The class avoids interrupting
 * the resolution process to maintain consistency.
 */
public class DateTimeResolver implements TokenResolver {
    private static final Pattern yearPattern = Pattern.compile("(YEAR\\(([0-9-]+)\\))");
    private static final Pattern monthPattern = Pattern.compile("(MONTH\\(([0-9-]+)\\))");
    private static final Pattern dayPattern = Pattern.compile("(DAY\\(([0-9-]+)\\))");
    private static final Pattern hourPattern = Pattern.compile("(HOUR\\(([0-9:]+)\\))");
    private static final Pattern minutePattern = Pattern.compile("(MINUTE\\(([0-9:]+)\\))");
    private static final Pattern secondPattern = Pattern.compile("(SECOND\\(([0-9:]+)\\))");
    public static final Function<String, String> YEAR = (str) -> str.substring(0, 4);
    public static final Function<String, String> MONTH = (str) -> {
        int idx = str.indexOf('-');
        return StringUtilities.padLeft(idx > 0 ? str.substring(idx + 1, str.indexOf('-', idx + 1)) : str.substring(4, 6),
                                       2, "0");
    };
    public static final Function<String, String> DAY = (str) -> {
        int idx = str.lastIndexOf('-');
        return StringUtilities.padLeft(idx > 0 ? str.substring(str.lastIndexOf('-', idx - 1) + 1, idx) : str.substring(6, 8),
                                       2, "0");
    };
    public static final Function<String, String> HOUR = (str) -> str.substring(0, 2);
    public static final Function<String, String> MINUTE = (str) -> {
        int idx = str.indexOf(':');
        return StringUtilities.padLeft(idx > 0 ? str.substring(idx + 1, str.indexOf(':', idx + 1)) : str.substring(2, 4),
                                       2, "0");
    };
    public static final Function<String, String> SECOND = (str) -> {
        int idx = str.lastIndexOf(':');
        return StringUtilities.padLeft(idx > 0 ? str.substring(idx + 1, str.indexOf(':', idx + 1)) : str.substring(4, 6),
                                       2, "0");
    };

    @Override
    public String resolve(final String expression) {
        if (StringUtilities.isNullOrEmpty(expression)) {
            return expression;
        }
        String transformed = expression;
        try {
            Matcher matcher = yearPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), YEAR.apply(matcher.group(2)));
            }
            matcher = monthPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), MONTH.apply(matcher.group(2)));
            }
            matcher = dayPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), DAY.apply(matcher.group(2)));
            }
            matcher = hourPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), HOUR.apply(matcher.group(2)));
            }
            matcher = minutePattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), MINUTE.apply(matcher.group(2)));
            }
            matcher = secondPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), SECOND.apply(matcher.group(2)));
            }
        } catch (Throwable t) {
            Logger.getLogger(DateTimeResolver.class.getName()).warning(t.getMessage());
        }
        return transformed;
    }
}
