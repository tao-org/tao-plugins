package ro.cs.tao.products.enmap;

import ro.cs.tao.eodata.util.BaseProductHelper;
import ro.cs.tao.eodata.util.ProductHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class EnMAPProductHelper extends BaseProductHelper {

    public static final String LX_2 = "00.02.00";
    public static final String LX_6 = "00.06.00";

    static final Pattern ProductLX2 = Pattern.compile("(L[1-2][A-C]_Alps_\\d.*?)");
    static final Pattern ProductLX6 = Pattern.compile("(ENMAP01-____L[1-2][A-C]-DT\\d{9,10})_(\\d{8}T\\d{6}Z)_(\\d{3})_(V\\d{6})_(\\d{8}T\\d{6}Z)");
    private static final Pattern Tile = Pattern.compile("(ENMAP01-____L[1-2][A-C]-DT\\d{9,10})_(\\d{8}T\\d{6}Z)_(\\d{3})_(V\\d{6})_(\\d{8}T\\d{6}Z)");

    private static final Pattern Metadata = Pattern.compile(Tile.pattern() + "-METADATA.XML");

    private boolean demoFormat;

    public static EnMAPProductHelper createHelper(Path productPath) {
        return new EnMAPProductHelper(productPath);
    }

    EnMAPProductHelper(Path productPath) {
        super(productPath);
    }

    @Override
    public String getVersion() {
        if (this.version == null) {
            this.version = this.demoFormat ? LX_2 : LX_6;
        }
        return this.version;
    }

    @Override
    public String getOrbit() {
        return null;
    }

    @Override
    public ProductHelper duplicate() {
        return new EnMAPProductHelper(this.path);
    }

    @Override
    public String getSensingDate() {
        return null;
    }

    @Override
    public String getProcessingDate() {
        return null;
    }

    @Override
    public String getMetadataFileName() {
        try (final Stream<Path> files = Files.find(this.path, Integer.MAX_VALUE, (p, a) -> p.toFile().getName().matches(Metadata.pattern()))) {
            final Path metadataFile = files.findFirst().orElse(null);
            if (metadataFile != null) {
                return metadataFile.getFileName().toString();
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    @Override
    public Pattern getTilePattern() {
        return Tile;
    }

    @Override
    protected boolean verifyProductName(String name) {
        this.demoFormat = ProductLX2.matcher(name).matches();
        return this.demoFormat || ProductLX6.matcher(name).matches();
    }

}
