package ro.cs.tao.products.maja;

import ro.cs.tao.datasource.remote.ProductHelper;

import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public abstract class MajaProductHelper extends ProductHelper {

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

    MajaProductHelper(Path productPath) {
        super(productPath);
    }

    public abstract String getGranuleFolder(String granuleIdentifier);

    protected abstract Pattern getNamePattern();

    @Override
    protected boolean verifyProductName(String name) {
        return getNamePattern().matcher(name).matches();
    }
}
