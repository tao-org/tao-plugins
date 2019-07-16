package ro.cs.tao.products.maja;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class MajaSentinel2ProductHelper extends MajaProductHelper {

    private static final Pattern S2L2Pattern = Pattern.compile("S2([AB])_MSIL2A_(\\d{8}T\\d{6})_(N\\d{4})_R(\\d{3})_T(\\d{2}\\w{3})_(\\d{8}T\\d{6})(?:.SAFE)?");
    private static final Pattern S2L2TilePattern = Pattern.compile("SENTINEL2[AB]_(\\d{8})-(\\d{6})-(\\d{3})_L2A_T(\\d{2}\\w{3})_C_V(\\d+)-(\\d+)");

    MajaSentinel2ProductHelper(Path productPath) {
        super(productPath);
    }

    @Override
    public String getGranuleFolder(String granuleIdentifier) {
        String folder = null;
        try {
            Path granuleFolder = Files.list(this.path)
                                      .filter(p -> S2L2TilePattern.matcher(p.getFileName().toString()).matches() && p.getFileName().toString().contains(granuleIdentifier))
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
        final String granuleFolder = getGranuleFolder(getTokens(S2L2Pattern)[4]);
        if (granuleFolder != null) {
            metaDataFile = granuleFolder + "_MTD_ALL.xml";
        }
        return metaDataFile;
    }

    @Override
    public Pattern getTilePattern() { return S2L2TilePattern; }

    @Override
    public String getOrbit() { return getTokens(S2L2Pattern)[3]; }

    public String getTileIdentifier() { return getTokens(S2L2Pattern)[4]; }

    @Override
    protected Pattern getNamePattern() { return S2L2Pattern; }

    @Override
    public String getSensingDate() {
        return getTokens(S2L2Pattern)[1];
    }

}
