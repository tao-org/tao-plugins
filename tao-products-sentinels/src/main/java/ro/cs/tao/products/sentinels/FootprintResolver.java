package ro.cs.tao.products.sentinels;

import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.StringUtilities;

import java.awt.geom.Path2D;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FootprintResolver is an implementation of the {@link TokenResolver} interface that processes string expressions
 * to transform specific tokens identified as spatial footprints of Sentinel-2 tiles. These tokens follow a specific
 * pattern and are substituted with their respective Well-Known Text (WKT) representation of polygons.
 *
 * The class uses regular expressions to detect tokens in the format `FOOTPRINT(tileCode)` within an input string.
 * The `tileCode` is a 5-character code identifying a Sentinel-2 tile. If a valid tile code is found, the corresponding
 * spatial boundary (retrieved using {@link Sentinel2TileExtent}) is converted to a WKT format and replaces the
 * original token in the input string.
 *
 * Key Responsibilities:
 * - Parse and identify `FOOTPRINT(tileCode)` tokens in a string.
 * - Fetch the spatial extent of the specified tile using {@link Sentinel2TileExtent}.
 * - Convert the spatial extent to a WKT representation and substitute the token in the input string.
 *
 * Thread Safety:
 * This class is thread-safe as it only relies on immutable or thread-safe constructs.
 *
 * Error Handling:
 * - If the input string is null or empty, the original value is returned unchanged.
 * - Any exceptions during processing are caught and logged using {@link Logger}.
 *
 * Pattern and Substitution Logic:
 * - The token pattern is defined as `(FOOTPRINT(tileCode))`, where `tileCode` is expressed as two digits
 *   followed by three uppercase letters.
 * - If the tile code is invalid or does not exist, the token remains unchanged.
 */
public class FootprintResolver implements TokenResolver {
    private static final Pattern footprintPattern = Pattern.compile("(FOOTPRINT\\((\\d{2}[A-Z]{3})\\))");

    public static final Function<String, String> FOOTPRINT = (str) -> {
        final Path2D.Double extent = Sentinel2TileExtent.getInstance().getTileExtent(str);
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
