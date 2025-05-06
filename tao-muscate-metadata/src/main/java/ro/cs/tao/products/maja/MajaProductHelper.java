package ro.cs.tao.products.maja;

import ro.cs.tao.eodata.util.BaseProductHelper;

import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * An abstract helper class for managing and processing MAJA products.
 * This class is designed to facilitate product-specific functionality
 * such as verifying file naming patterns, identifying granule folders,
 * and retrieving metadata information. It serves as a base class for
 * product-specific implementations such as Sentinel-2 and Landsat-8 products.
 */
public abstract class MajaProductHelper extends BaseProductHelper {

    public static MajaProductHelper createHelper(Path productPath) {
        MajaProductHelper helper = null;
        try {
            helper = new MajaSentinel2ProductHelper(productPath);
        } catch (IllegalArgumentException e) {
            helper = new MajaLandsat8ProductHelper(productPath);
        }
        return helper;
    }

    final Logger logger = Logger.getLogger(MajaProductHelper.class.getName());

    MajaProductHelper() { super(); }

    MajaProductHelper(Path productPath) {
        super(productPath);
    }

    @Override
    public int order() {
        return 1;
    }

    public abstract String getGranuleFolder(String granuleIdentifier);

    protected abstract Pattern getNamePattern();

    @Override
    protected boolean verifyProductName(String name) {
        return getNamePattern().matcher(name).matches();
    }
}
