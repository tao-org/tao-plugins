package ro.cs.tao.products.maccs;

import ro.cs.tao.eodata.util.BaseProductHelper;

import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public abstract class MaccsProductHelper extends BaseProductHelper {

    public static MaccsProductHelper createHelper(Path productPath) {
        MaccsProductHelper helper = null;
        try {
            helper = new MaccsSentinel2ProductHelper(productPath);
        } catch (IllegalArgumentException e) {
            helper = new MaccsLandsat8ProductHelper(productPath);
        }
        return helper;
    }

    final Logger logger = Logger.getLogger(MaccsProductHelper.class.getName());

    MaccsProductHelper() { super() ;}

    MaccsProductHelper(Path productPath) {
        super(productPath);
    }

    public abstract String getGranuleFolder(String granuleIdentifier);

    protected abstract Pattern getNamePattern();

    @Override
    protected boolean verifyProductName(String name) {
        return getNamePattern().matcher(name).matches();
    }
}
