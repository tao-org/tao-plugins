package ro.cs.tao.functions;

import ro.cs.tao.datasource.param.JavaType;
import ro.cs.tao.eodata.naming.ResolverFunction;
import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.StringUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A specialized implementation of the {@link TokenResolver} interface that resolves and transforms
 * string expressions containing specific coordinate reference system (CRS) tokens, particularly for
 * UTM zones. This class interprets and converts tokens embedded in the provided string expressions
 * into coordinate reference system codes in the EPSG standard format.
 *
 * The resolver uses predefined patterns to match and identify valid tokens within the input strings,
 * specifically targeting UTM zone representations. Upon successful identification, it transforms
 * these tokens into standardized CRS codes following EPSG conventions.
 *
 * Key Features:
 * - Identifies UTM zone tokens formatted as "EPSG_CODE(xxAAA)" within the input string,
 *   where "xx" denotes a two-digit zone number and "AAA" represents additional zone designations.
 * - Transforms identified tokens into valid EPSG codes, considering hemisphere-specific rules
 *   based on the provided zone number and designation.
 * - Handles inputs gracefully, returning the original string if no transformations were applied
 *   or if the input is null or empty.
 * - Logs warnings in case of errors during pattern matching or transformation.
 *
 * Thread Safety:
 * This class is thread-safe and can be used concurrently across multiple threads, as it does not
 * maintain any mutable state in its operations.
 *
 * Error Handling:
 * In the event of processing errors, this class logs warnings using the standard logging framework.
 * It does not throw exceptions to the caller directly but ensures a fallback behavior by returning
 * the original input string in case of any failures.
 */
public class ProjectionResolver implements TokenResolver {
    private static final Pattern utmZonePattern = Pattern.compile("(EPSG_CODE\\((\\d{2}[A-Z]{3})\\))");

    /**
     * A function that transforms a string representation of a UTM zone specification
     * into its corresponding EPSG code in the format "EPSG:xxxxx".
     *
     * The function operates based on the input string's first two characters, which
     * represent the numeric UTM zone. The hemisphere is determined by the ASCII value
     * of the first character in the string:
     * - If the ASCII value of the first character is less than 70 ('F'), the zone
     *   is assumed to be in the northern hemisphere, and the EPSG code is calculated
     *   as 32600 plus the numeric zone.
     * - Otherwise, the zone is assumed to be in the southern hemisphere, and the EPSG
     *   code is calculated as 32700 plus the numeric zone.
     *
     * Input:
     * - A two-character string representing the UTM zone.
     *
     * Output:
     * - A string prefixed with "EPSG:" followed by the calculated EPSG code.
     *
     * Errors:
     * - If the input string is not in the expected format, or if the first two
     *   characters cannot be parsed as an integer, a runtime exception may occur.
     *
     * Usage Context:
     * This function is used in resolving and standardizing CRS (Coordinate Reference
     * System) codes based on UTM zone specifications.
     */
    public static final Function<String, String> EPSG_CODE = (str) -> {
        final int code = Integer.parseInt(str.substring(0, 2));
        return "EPSG:" + (str.charAt(0) < 70 ? 32600 + code : 32700 + code);
    };

    @Override
    public List<ResolverFunction> definedFunctions() {
        final List<ResolverFunction> functions = new ArrayList<>();
        ResolverFunction function = new ResolverFunction("EPSG_CODE",
                                                         JavaType.STRING.friendlyName(),
                                                         "Transforms a UTM zone specification into its corresponding EPSG code in the format \"EPSG:xxxxx\"",
                                                         "CRS");
        function.addArgument("utmZone", JavaType.STRING.friendlyName(), "the UTM zone in the format nnAAA");
        functions.add(function);
        return functions;
    }

    @Override
    public String resolve(String expression) {
        if (StringUtilities.isNullOrEmpty(expression)) {
            return expression;
        }
        String transformed = expression;
        try {
            Matcher matcher = utmZonePattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), EPSG_CODE.apply(matcher.group(2)));
            }
        } catch (Throwable t) {
            Logger.getLogger(ProjectionResolver.class.getName()).warning(t.getMessage());
        }
        return transformed;
    }
}
