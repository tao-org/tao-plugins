package ro.cs.tao.products.landsat;

import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.StringUtilities;

import java.awt.geom.Path2D;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The FootprintResolver class implements the {@link TokenResolver} interface, providing functionality
 * to resolve and transform specific tokens within string expressions. The tokens processed by this class
 * are expected to match the format defined by the regular expression {@code "(FOOTPRINT\\((\\d{6})\\))"}.
 * When a matching token is detected, it translates the token into the corresponding Well-Known Text (WKT)
 * representation of a geographic footprint.
 *
 * Responsibilities:
 * - Detect tokens in the input string that match the defined pattern.
 * - Retrieve the corresponding geographic footprint for the specified tile code using
 *   {@link Landsat8TileExtent#getInstance()}.
 * - Transform the token into the WKT representation or leave it unchanged if the footprint is not found.
 *
 * Thread Safety:
 * This class is thread-safe as it primarily relies on stateless operations and immutable components.
 *
 * Methods:
 * - {@link #resolve(String)}: Resolves and transforms matching tokens within the input string.
 *
 * Token Pattern:
 * The token format is {@code FOOTPRINT(XXXXXX)}, where {@code XXXXXX} represents a 6-digit tile code.
 *
 * Examples of Token Resolution:
 * - A token like {@code FOOTPRINT(123456)} transforms into the WKT of the geographic extent if valid.
 * - If no matching tile extent is found for the given code, the token remains as is.
 *
 * Error Handling:
 * - If the input string is null or empty, it is returned as is.
 * - Any unexpected errors during resolution are logged, and the input string remains unchanged.
 *
 * Dependencies:
 * - Uses {@link Landsat8TileExtent} to retrieve geographic footprint data for tile codes.
 * - Employs regular expressions for token detection.
 * - Converts geographic extents to WKT format through utility methods in {@link Polygon2D}.
 *
 * Logging:
 * Warnings are logged in case of errors during token resolution to assist with debugging.
 */
public class FootprintResolver implements TokenResolver {
    private static final Pattern footprintPattern = Pattern.compile("(FOOTPRINT\\((\\d{6})\\))");

    public static final Function<String, String> FOOTPRINT = (str) -> {
        final Path2D.Double extent = Landsat8TileExtent.getInstance().getTileExtent(str);
        return extent != null ? Polygon2D.fromPath2D(extent).toWKT(6) : str;
    };

    @Override
    public String resolve(String expression) {
        if (StringUtilities.isNullOrEmpty(expression)) {
            return expression;
        }
        String transformed = expression;
        try {
            Matcher matcher = footprintPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), FOOTPRINT.apply(matcher.group(2)));
            }
        } catch (Throwable t) {
            Logger.getLogger(FootprintResolver.class.getName()).warning(t.getMessage());
        }
        return transformed;
    }
}

