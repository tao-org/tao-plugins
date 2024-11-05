package ro.cs.tao.products.sentinels;

import ro.cs.tao.eodata.util.BaseProductHelper;

import java.util.regex.Pattern;

public class Sentinel5PProductHelper extends BaseProductHelper {

    static final Pattern S5PPattern =
            Pattern.compile("(S5P)_(TEST|OGCA|GSOV|OPER|NRTI|OFFL|RPRO)_(MPL|TLM|LOG|REP|L0_|L1B|L2_|AUX|ICM|CFG|REF|LUT)_([A-Z1-9_]{6})_(\\d{8}T\\d{6})_(\\d{8}T\\d{6})_(\\d{5})_(\\d{2})_(\\d{6})_(\\d{8}T\\d{6})(?:.nc)?");

    public Sentinel5PProductHelper() { super(); }

    Sentinel5PProductHelper(String productName) { super(productName); }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Sentinel5PProductHelper duplicate() {
        return new Sentinel5PProductHelper(getName());
    }

    @Override
    public String getProductRelativePath() {
        return null;
    }

    @Override
    public Pattern getTilePattern() { return null; }

    @Override
    public String getMetadataFileName() {
        return this.name != null
               ? this.name.replace(".nc", "") + ".cdl"
               : null;
    }

    @Override
    public String getOrbit() {
        return getTokens(S5PPattern, this.name, null)[6];
    }

    @Override
    public String getSensingDate() {
        return getTokens(S5PPattern, this.name, null)[4];
    }

    @Override
    public String getProcessingDate() {
        return getTokens(S5PPattern, this.name, null)[9];
    }

    @Override
    protected boolean verifyProductName(String name) {
        return S5PPattern.matcher(name).matches();
    }
}
