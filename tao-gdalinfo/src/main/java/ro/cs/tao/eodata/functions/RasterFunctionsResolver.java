package ro.cs.tao.eodata.functions;

import ro.cs.tao.datasource.param.JavaType;
import ro.cs.tao.eodata.GdalInfo;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.eodata.naming.ResolverFunction;
import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.StringUtilities;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A specific implementation of the {@link TokenResolver} interface that resolves and processes
 * raster-related expressions and functions. This resolver focuses on identifying and evaluating
 * certain predefined patterns related to raster data analysis and modifications.
 *
 * The primary functionality of this class includes:
 * - Identifying and resolving expressions related to raster operations using regular expressions.
 * - Supporting predefined raster-related functions such as checking for "ALL_NODATA" and counting
 *   pixels above or below a specified threshold.
 * - Utilizing GDAL-based metadata inspection to evaluate raster properties and compute results.
 *
 * Thread Safety:
 * This class is not explicitly thread-safe due to the mutable state and external dependencies. Proper
 * synchronization or external concurrency handling is recommended if instances of this resolver are shared
 * across multiple threads.
 *
 * Error Handling:
 * This implementation catches and logs errors during metadata inspection or evaluations to ensure
 * that invalid or malformed inputs are handled gracefully. In such cases, fallback default values
 * are returned instead of halting execution.
 */
public class RasterFunctionsResolver implements TokenResolver {

    private static final Pattern allNoDataPattern = Pattern.compile("(ALL_NODATA\\((?:[a-zA-Z]:\\\\|/)(?:[^<>:\"/\\\\|?*\\r\\n]+[\\\\/])*[^<>:\"/\\\\|?*\\r\\n]*\\))");
    private static final Pattern countPixelsAbovePattern = Pattern.compile("(COUNT_PIXELS_ABOVE\\(((?:[a-zA-Z]:\\\\|/)(?:[^<>:\"/\\\\|?*\\r\\n]+[\\\\/])*[^<>:\"/\\\\|?*\\r\\n]*),([0-9.]+)\\))");
    private static final Pattern countPixelsBelowPattern = Pattern.compile("(COUNT_PIXELS_BELOW\\(((?:[a-zA-Z]:\\\\|/)(?:[^<>:\"/\\\\|?*\\r\\n]+[\\\\/])*[^<>:\"/\\\\|?*\\r\\n]*),([0-9.]+)\\))");

    /**
     * A {@link Function} implementation that determines whether all pixel values in a raster
     * dataset are equal to the NoData value. The function takes a file path as input and returns
     * a {@code String} representation of either {@code "true"} or {@code "false"}.
     *
     * It makes use of GDAL (Geospatial Data Abstraction Library) to retrieve metadata
     * and statistics for the raster dataset. Specifically, it evaluates if the minimum
     * and maximum pixel values are the same as the NoData value for the dataset. If both
     * the minimum and maximum values are equal to the NoData value, the method returns {@code "true"}.
     * Otherwise, it returns {@code "false"}.
     *
     * If an I/O exception occurs during metadata retrieval or if the raster dataset is not
     * deemed suitable for decoding, the function will return {@code "false"} as the default value.
     *
     * Returns:
     * - {@code "true"}: if the raster contains only NoData values.
     * - {@code "false"}: if the raster contains other values or if an error occurs.
     *
     * Field of the {@code RasterFunctionsResolver} class.
     */
    public static final Function<String, String> ALL_NODATA = (path) -> {
        String retVal = Boolean.FALSE.toString();
        final GdalInfo gdalInfo = new GdalInfo(true, false);
        Path productPath = Paths.get(path);
        if (gdalInfo.decodeQualification(productPath) == DecodeStatus.SUITABLE) {
            try {
                final MetadataInspector.Metadata metadata = gdalInfo.getMetadata(productPath);
                final Map<String, Double> statistics = metadata.getStatistics();
                if (statistics != null) {
                    final Double min = statistics.get("min");
                    final Double max = statistics.get("max");
                    final Double noData = Double.parseDouble(metadata.getAttribute("nodata"));
                    if (min != null && max != null && Double.compare(min, noData) == 0 && Double.compare(max, noData) == 0) {
                        return Boolean.TRUE.toString();
                    }
                }
            } catch (IOException e) {
                Logger.getLogger(RasterFunctionsResolver.class.getName()).severe(e.getMessage());
            }
        }
        return retVal;
    };

    /**
     * A BiFunction that computes the count of pixels in a raster dataset
     * whose values are greater than or equal to the specified threshold.
     *
     * The function takes two string inputs:
     * - The file path of the raster dataset.
     * - The value threshold as a string.
     *
     * It accesses the metadata of the raster dataset using a GdalInfo instance
     * and retrieves the statistical information such as minimum, maximum,
     * histogram, etc. If the dataset is identified as suitable, it determines
     * the pixel count above the specified threshold by analyzing the histogram.
     *
     * If the dataset is not suitable, if there is an error processing
     * the data, or if the required metadata/statistics are missing,
     * the function returns a default value of "0".
     *
     * Errors during file access or parsing are logged using Java's
     * logging framework.
     */
    public static final BiFunction<String, String, String> COUNT_PIXELS_ABOVE = (path, value) -> {
        String retVal = "0";
        final GdalInfo gdalInfo = new GdalInfo(true, true);
        Path productPath = Paths.get(path);
        if (gdalInfo.decodeQualification(productPath) == DecodeStatus.SUITABLE) {
            try {
                final MetadataInspector.Metadata metadata = gdalInfo.getMetadata(productPath);
                final Map<String, Double> statistics = metadata.getStatistics();
                if (statistics != null) {
                    final Double min = statistics.get("min");
                    final Double max = statistics.get("max");
                    final Double dVal = Double.parseDouble(value);
                    if ((Double.compare(min, max) != 0) && (Double.compare(min, dVal) <= 0 || Double.compare(max, dVal) >= 0)) {
                        final int[] histogram = metadata.getHistogram();
                        if (histogram != null && histogram.length > 0) {
                            final int idx = (int) ((double) histogram.length * (dVal - min) / (max - min)) + 1;
                            long count = 0;
                            for (int i = idx; i < histogram.length; i++) {
                                count += histogram[i];
                            }
                            retVal = Long.toString(count);
                        }
                    }
                }
            } catch (IOException e) {
                Logger.getLogger(RasterFunctionsResolver.class.getName()).severe(e.getMessage());
            }
        }
        return retVal;
    };

    /**
     * A predefined {@link BiFunction} that computes the total number of pixels in a raster dataset
     * with intensity values less than or equal to a specified threshold. The raster dataset is
     * provided as a file path, and the threshold value is specified as a string.
     *
     * <p>The function performs the following steps:
     * - Loads metadata from the raster dataset using the GDAL library.
     * - Extracts necessary statistical information, such as the minimum, maximum, and histogram of pixel intensities.
     * - Verifies that the provided threshold falls within the valid range of pixel intensity values.
     * - Calculates the cumulative pixel count below or equal to the given threshold based on the histogram.
     *
     * In case of an error during file reading or if the dataset is not deemed suitable, the function
     * returns "0".
     *
     * The function relies on GDAL-based decoding and metadata inspection to analyze raster data.
     *
     * Parameters:
     *  - Path to the raster dataset, provided as a string.
     *  - Threshold value for comparison, provided as a string.
     *
     * Returns:
     *  - A string representation of the total number of pixels below or equal to the threshold. Returns "0" in
     *    case of an error or if the dataset is unsuitable for processing.
     */
    public static final BiFunction<String, String, String> COUNT_PIXELS_BELOW = (path, value) -> {
        String retVal = "0";
        final GdalInfo gdalInfo = new GdalInfo(true, true);
        Path productPath = Paths.get(path);
        if (gdalInfo.decodeQualification(productPath) == DecodeStatus.SUITABLE) {
            try {
                final MetadataInspector.Metadata metadata = gdalInfo.getMetadata(productPath);
                final Map<String, Double> statistics = metadata.getStatistics();
                if (statistics != null) {
                    final Double min = statistics.get("min");
                    final Double max = statistics.get("max");
                    final Double dVal = Double.parseDouble(value);
                    if ((Double.compare(min, max) != 0) && (Double.compare(min, dVal) <= 0 || Double.compare(max, dVal) >= 0)) {
                        final int[] histogram = metadata.getHistogram();
                        if (histogram != null && histogram.length > 0) {
                            final int idx = (int) ((double) histogram.length * (dVal - min) / (max - min));
                            long count = 0;
                            for (int i = 0; i <= idx; i++) {
                                count += histogram[i];
                            }
                            retVal = Long.toString(count);
                        }
                    }
                }
            } catch (IOException e) {
                Logger.getLogger(RasterFunctionsResolver.class.getName()).severe(e.getMessage());
            }
        }
        return retVal;
    };

    @Override
    public String resolve(String expression) {
        if (StringUtilities.isNullOrEmpty(expression)) {
            return expression;
        }
        String transformed = expression;
        try {
            Matcher matcher = allNoDataPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), ALL_NODATA.apply(matcher.group(2)));
            }
            matcher = countPixelsAbovePattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), COUNT_PIXELS_ABOVE.apply(matcher.group(2), matcher.group(3)));
            }
            matcher = countPixelsBelowPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), COUNT_PIXELS_BELOW.apply(matcher.group(2), matcher.group(3)));
            }
        } catch (Throwable t) {
            Logger.getLogger(RasterFunctionsResolver.class.getName()).warning(t.getMessage());
        }
        return transformed;
    }

    @Override
    public List<ResolverFunction> definedFunctions() {
        final List<ResolverFunction> functions = new ArrayList<>();
        ResolverFunction function = new ResolverFunction("ALL_NODATA",
                                                         JavaType.STRING.friendlyName(),
                                                         "Check if all pixels in a given raster are nodata",
                                                         "Raster");
        function.addArgument("path", JavaType.STRING.friendlyName(), "the path of the raster");
        functions.add(function);
        function = new ResolverFunction("COUNT_PIXELS_ABOVE",
                                        JavaType.STRING.friendlyName(),
                                        "Counts the pixels in a given raster whose values are greater than or equal to the specified threshold",
                                        "Raster");
        function.addArgument("path", JavaType.STRING.friendlyName(), "the path of the raster");
        function.addArgument("value", JavaType.STRING.friendlyName(), "the value to compare with");
        functions.add(function);
        function = new ResolverFunction("COUNT_PIXELS_BELOW",
                                        JavaType.STRING.friendlyName(),
                                        "Counts the pixels in a given raster whose values are less than the specified threshold",
                                        "Raster");
        function.addArgument("path", JavaType.STRING.friendlyName(), "the path of the raster");
        function.addArgument("value", JavaType.STRING.friendlyName(), "the value to compare with");
        functions.add(function);
        return functions;
    }
}
