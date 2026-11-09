package ro.cs.tao.products.sentinels;

import ro.cs.tao.eodata.util.BaseProductHelper;
import ro.cs.tao.eodata.util.ProductHelper;

import java.util.regex.Pattern;

public class Sen2agriL3BProductHelper extends BaseProductHelper {
    static final Pattern Sen2AgriProductL3B = Pattern.compile("S2AGRI_L3B_PRD_S\\d+_(\\d{8}T\\d{6})_A(\\d{8}T\\d{6})");
    static final Pattern Sen2AgriProductL3BFiles = Pattern.compile("S2AGRI_L3B_S.*_A(\\d{8}T\\d{6})_T\\d{2}\\w{3}\\.TIF");

    public Sen2agriL3BProductHelper() {
        super();
    }

    Sen2agriL3BProductHelper(String name) {
        super(name);
    }

    @Override
    public String getProcessingDate() {
        return getTokens(Sen2AgriProductL3B)[0];
    }

    @Override
    public String getSensingDate() {
        final String sensingDate;
        try {
            return getTokens(Sen2AgriProductL3B)[1];
        } catch (Exception e) {
            return getTokens(Sen2AgriProductL3BFiles)[0];
        }
    }

    @Override
    protected boolean verifyProductName(String name) {
        return Sen2AgriProductL3B.matcher(name).matches() ||
                Sen2AgriProductL3BFiles.matcher(name).matches();
    }

    @Override
    public String getMetadataFileName() {
        return null;
    }

    @Override
    public Pattern getTilePattern() {
        return null;
    }

    @Override
    public String getOrbit() {
        return null;
    }

    @Override
    public ProductHelper duplicate() {
        return new Sen2agriL3BProductHelper(getName());
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Pattern[] getNamePatterns() {
        return new Pattern[] { Sen2AgriProductL3B };
    }
}
