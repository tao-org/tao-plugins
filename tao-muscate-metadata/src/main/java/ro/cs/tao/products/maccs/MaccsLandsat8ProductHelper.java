package ro.cs.tao.products.maccs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class MaccsLandsat8ProductHelper extends MaccsProductHelper {

    private static final Pattern L8L2Pattern = Pattern.compile("LC08_L2A_(\\d{6})_(\\d{8})_(\\d{8})_(\\d{2})_(T[12]|RT)");
    private static final Pattern L8L2TilePattern = Pattern.compile("L8_\\w+_L8C_L2VALD_(\\d{6})_(\\d{8}).DBL.DIR");

    public MaccsLandsat8ProductHelper() { super() ;}

    MaccsLandsat8ProductHelper(Path productPath) {
        super(productPath);
    }

    @Override
    public MaccsLandsat8ProductHelper duplicate() {
        return new MaccsLandsat8ProductHelper(this.path != null ? this.path : Path.of(this.name));
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
    protected Pattern getNamePattern() {
        return L8L2Pattern;
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
    public String getSensingDate() {
        return getTokens(L8L2Pattern)[1];
    }
}
