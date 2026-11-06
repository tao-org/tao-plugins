package ro.cs.tao.products.maja;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A helper class for handling MAJA products specific to Landsat 8.
 * This class extends the functionality of {@link MajaProductHelper}.
 * It provides methods for working with Landsat 8-specific metadata and file
 * naming patterns, as well as utilities for identifying granule folders
 * and product metadata files within a Landsat 8 processing context.
 */
public class MajaLandsat8ProductHelper extends MajaProductHelper {

    private static final Pattern L8L2Pattern = Pattern.compile("LC08_L2A_(\\d{6})_(\\d{8})_(\\d{8})_(\\d{2})_(T[12]|RT)");
    private static final Pattern L8L2TilePattern = Pattern.compile("L8_\\w+_L8C_L2VALD_(\\d{6})_(\\d{8}).DBL.DIR");

    public MajaLandsat8ProductHelper() {
    }

    MajaLandsat8ProductHelper(Path productPath) {
        super(productPath);
    }

    @Override
    public Pattern[] getNamePatterns() {
        return new Pattern[] { L8L2Pattern };
    }

    @Override
    public MajaLandsat8ProductHelper duplicate() {
        return new MajaLandsat8ProductHelper(this.path != null ? this.path : Path.of(this.name));
    }

    @Override
    public String getGranuleFolder(String granuleIdentifier) {
        String folder = null;
        try (Stream<Path> stream = Files.list(this.path)) {
            Path granuleFolder = stream
                    .filter(p -> L8L2TilePattern.matcher(p.getFileName().toString()).matches() && p.getFileName().toString().contains(granuleIdentifier))
                    .findFirst().orElse(null);
            if (granuleFolder != null) {
                folder = granuleFolder.getFileName().toString();
            } else {
                logger.warning(String.format("Cannot determine granule folder for product %s", this.name));
            }
        } catch (IOException e) {
            logger.severe(String.format("Cannot list contents of product %s. Reason; %s", this.name, e.getMessage()));
        }
        return folder;
    }

    @Override
    public String getMetadataFileName() {
        String metaDataFile = null;
        final String granuleFolder = getGranuleFolder(getTokens(L8L2Pattern)[0]);
        if (granuleFolder != null) {
            metaDataFile = granuleFolder.replace(".DBL.DIR", ".HDR");
        }
        return metaDataFile;
    }

    @Override
    public Pattern getTilePattern() {
        return L8L2TilePattern;
    }

    @Override
    public String getOrbit() { return getTokens(L8L2Pattern)[0]; }

    @Override
    public String getSensingDate() { return getTokens(L8L2Pattern)[1]; }
}
