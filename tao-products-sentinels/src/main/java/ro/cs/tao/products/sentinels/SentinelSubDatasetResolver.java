package ro.cs.tao.products.sentinels;

import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;

import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolver class for substituting function to extract Sentinel-2 subdatasets required by GDAL/OTB
 *
 * @author Cosmin Cara
 * @since 1.5.2
 */
public class SentinelSubDatasetResolver implements TokenResolver {
    private static final Pattern sentinelPattern = Pattern.compile("S2[ABCD]_MSI(L1C|L2A)_\\d{8}T\\d{6}_N\\d{4}_R\\d{3}_T(\\d{2}\\w{3})_\\d{8}T\\d{6}(?:.SAFE)?");
    private static final Pattern subdatasetPattern = Pattern.compile("(S2SUBDATASET\\(([a-zA-Z0-9_\\-./:\\\\]+),(10|20|60)\\))");
    private static final String resolvedPattern = "SENTINEL2_%s:%s:%sm:EPSG_%d";
    private static final String resolvedPatternEx = "SENTINEL2_%s:%s/MTD_MSI%s.xml:%sm:EPSG_%d";

    public static final BiFunction<String, String, String> SUBDATASET = (name, resolution) -> {
        final Matcher matcher = sentinelPattern.matcher(name);
        if (matcher.find()) {
            String str = matcher.group(2);
            final int code = Integer.parseInt(str.substring(0, 2));
            final String level = matcher.group(1);
            return name.endsWith(".xml")
                   ? String.format(resolvedPattern,
                                   level, FileUtilities.asUnixPath(name, true), resolution, (str.charAt(0) < 70 ? 32600 + code : 32700 + code))
                   : String.format(resolvedPatternEx,
                                   level, FileUtilities.asUnixPath(name, true), level, resolution, (str.charAt(0) < 70 ? 32600 + code : 32700 + code));
        }
        return name;
    };

    @Override
    public String resolve(String expression) {
        if (StringUtilities.isNullOrEmpty(expression)) {
            return expression;
        }
        String transformed = expression;
        try {
            Matcher matcher = subdatasetPattern.matcher(expression);
            if (matcher.find()) {
                final String expr = matcher.group(1);
                if (matcher.groupCount() == 3) {
                    transformed = transformed.replace(expr, SUBDATASET.apply(matcher.group(2), matcher.group(3)));
                }
            }
        } catch (Throwable t) {
            Logger.getLogger(FootprintResolver.class.getName()).warning(t.getMessage());
        }
        return transformed;
    }
}
