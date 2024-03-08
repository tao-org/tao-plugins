package ro.cs.tao.products.sentinels;

import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.StringUtilities;

import java.awt.geom.Path2D;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
