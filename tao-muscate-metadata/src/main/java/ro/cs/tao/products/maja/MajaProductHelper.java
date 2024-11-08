package ro.cs.tao.products.maja;

import ro.cs.tao.eodata.util.BaseProductHelper;

import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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
