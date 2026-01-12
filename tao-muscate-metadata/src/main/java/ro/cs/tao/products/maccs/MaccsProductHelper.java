package ro.cs.tao.products.maccs;

import ro.cs.tao.eodata.util.BaseProductHelper;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Abstract helper class for working with product files compatible with the MACCS processing chain.
 * It provides a base structure for parsing metadata, extracting product-specific information,
 * and verifying naming conventions using patterns.
 *
 * This class is intended to be extended by concrete implementations specific to particular
 * satellite products, such as Sentinel-2 or Landsat-8.
 *
 * Features of this class include:
 * - Creation of a specific helper instance based on product type.
 * - Utilities for verifying product names against predefined patterns.
 * - Framework for extracting product-specific attributes such as granule folder names.
 */
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

    @Override
    public int order() {
        return 2;
    }

    public abstract String getGranuleFolder(String granuleIdentifier);

    @Override
    protected boolean verifyProductName(String name) {
        return getNamePatterns()[0].matcher(name).matches();
    }
}
