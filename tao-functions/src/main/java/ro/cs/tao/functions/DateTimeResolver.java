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

    /**
     * A constant function that extracts the first four characters (typically representing a year)
     * from the input string. This function is designed to work with strings where the first four
     * characters correspond to a year format (e.g., "2022").
     *
     * Behavior:
     * - This function assumes the input string starts with a valid four-character year.
     * - The substring operation retrieves the first four characters from the input string.
     *
     * Expectations:
     * - The input string should be at least four characters long to avoid potential runtime exceptions.
     * - If the string is shorter than four characters, a runtime error such as
     *   `StringIndexOutOfBoundsException` may occur.
     *
     * Example:
     * Input: "2022-09-15T10:30:00"
     * Output: "2022"
     *
     * Thread Safety:
     * This function is thread-safe as it operates on the given input without maintaining any
     * mutable state. It can safely be used in concurrent environments.
     */
    public static final Function<String, String> YEAR = (str) -> str.substring(0, 4);

    /**
     * A functional constant that extracts the month component from a date string and ensures it is left-padded
     * with zeroes to maintain a fixed length of 2 characters. The date string is expected to follow a
     * specific "YYYY-MM-DD" format or positions where the month can be properly resolved.
     *
     * The function explicitly identifies the first dash ('-') in the input string to locate the month
     * portion of the date. If the dash exists, it extracts the substring between the first and second
     * dashes as the month. Otherwise, it assumes the month occupies characters 4 to 6 of the string.
     * The extracted month value is then padded to ensure it has a length of 2 characters.
     *
     * Example valid inputs include strings in ISO 8601 compatible formats. If the input does not
     * match an expected pattern or lacks sufficient data for a month resolution, behavior may vary.
     *
     * Note: This function assumes proper input validation and that the input string containing the date
     * is correctly formatted.
     */
    public static final Function<String, String> MONTH = (str) -> {
        int idx = str.indexOf('-');
        return StringUtilities.padLeft(idx > 0 ? str.substring(idx + 1, str.indexOf('-', idx + 1)) : str.substring(4, 6),
                                       2, "0");
    };

    /**
     * A lambda function that extracts the "day" component from a string representing a date
     * and pads it to ensure a fixed length of 2 characters.
     *
     * The function expects the input string to follow a specific date pattern
     * (e.g., "YYYY-MM-DD-HH-MM-SS"). It identifies the last two instances of the '-' character
     * to delimit the "day" portion of the date. If the input string does not follow this pattern,
     * it defaults to extracting characters at position 6 and 7 (0-based index) from the input string.
     *
     * If the extracted "day" value is less than 2 characters long, it is left-padded with "0"
     * using the {@link StringUtilities#padLeft(String, int, String)} method.
     *
     * Note: This variable is defined as a constant and cannot be modified at runtime.
     */
    public static final Function<String, String> DAY = (str) -> {
        int idx = str.lastIndexOf('-');
        return StringUtilities.padLeft(idx > 0 ? str.substring(str.lastIndexOf('-', idx - 1) + 1, idx) : str.substring(6, 8),
                                       2, "0");
    };
    /**
     * A static function that extracts the hour component from the provided string.
     * It assumes that the input string contains time information at a specific position,
     * where the first two characters represent the hour in a two-digit format (e.g., "HH").
     * The returned value is a substring containing only the hour.
     *
     * Behavior:
     * - Extracts the first two characters of the input string.
     * - If the string is not well-formed or does not contain sufficient characters,
     *   the behavior depends on the implementation of the substring method.
     *
     * Key Characteristics:
     * - Stateless and thread-safe, as it operates only on the input string and does
     *   not modify any external state.
     * - Designed to work with strings representing date or time where the time portion
     *   follows a standardized format.
     *
     * Usage Context:
     * Suitable for scenarios where a time string needs to be parsed or processed to
     * isolate the hour component for additional computation or display purposes.
     */
    public static final Function<String, String> HOUR = (str) -> str.substring(0, 2);
    /**
     * A function that extracts the minute portion from a time string and returns it
     * as a two-digit, left-padded string. The input string is expected to follow a
     * specific time format containing a colon-separated time representation.
     *
     * - If the input string contains multiple colon-separated components (e.g., "HH:mm:ss"),
     *   the function extracts the portion after the first colon and before the second colon
     *   to determine the minute field.
     * - If the input string does not contain colons (e.g., "HHMMSS"), it extracts
     *   characters at indexes 2 and 3 as the minute field.
     * - The extracted minute value is padded to ensure it is two characters long,
     *   using "0" as the padding character if necessary.
     *
     * This function relies on the `StringUtilities.padLeft` method for padding.
     */
    public static final Function<String, String> MINUTE = (str) -> {
        int idx = str.indexOf(':');
        return StringUtilities.padLeft(idx > 0 ? str.substring(idx + 1, str.indexOf(':', idx + 1)) : str.substring(2, 4),
                                       2, "0");
    };
    /**
     * A constant function that extracts and returns the "second" component
     * from a given time string in a specific format, padding it to ensure
     * it always has two digits.
     *
     * The time string is expected to contain colon-separated components (e.g., "HH:MM:SS").
     * This function identifies the second occurrence of the colon (:) to locate
     * the "second" portion of the string. If the format does not include explicit colons
     * (e.g., "YYYYMMDDHHMMSS"), it retrieves characters corresponding to the seconds location.
     *
     * The result is then padded with leading zeros if necessary to ensure it is represented
     * as a two-character string.
     *
     * If the input string does not have valid time components or formatting, the behavior may not be guaranteed.
     */
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
