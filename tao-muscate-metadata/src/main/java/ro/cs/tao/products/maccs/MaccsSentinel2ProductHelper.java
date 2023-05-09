package ro.cs.tao.products.maccs;

import java.nio.file.Path;
import java.util.regex.Pattern;

public class MaccsSentinel2ProductHelper extends MaccsProductHelper {
    private static final Pattern S2L2Pattern = Pattern.compile("(S2[A-B])_MSIL2A_(\\d{8}T\\d{6})_(N\\d{4})_R(\\d{3})_T(\\d{2}\\w{3})_(\\d{8}T\\d{6})(?:.SAFE)?");
    private static final Pattern S2L2TilePattern = Pattern.compile("(S2[A-B])_OPER_SSC_L2VALD_(\\d{2}\\w{3})____(\\d{8}).DBL.DIR");

    public MaccsSentinel2ProductHelper() {
    }

    MaccsSentinel2ProductHelper(Path productPath) {
        super(productPath);
    }

    @Override
    public MaccsSentinel2ProductHelper duplicate() {
        return new MaccsSentinel2ProductHelper(this.path);
    }

    @Override
    public String getMetadataFileName() {
        String[] tokens = getTokens(S2L2Pattern);
        return tokens[0] + "_OPER_SSC_L2VALD_" + tokens[4] + "____" + tokens[1].substring(0, tokens[1].indexOf("T")) + ".HDR";
    }

    @Override
    public String getGranuleFolder(String granuleIdentifier) {
        String[] tokens = getTokens(S2L2Pattern);
        return tokens[0] + "_OPER_SSC_L2VALD_" + tokens[4] + "____" + tokens[1].substring(0, tokens[1].indexOf("T")) + ".DBL.DIR";
    }

    @Override
    public String getProductRelativePath() {
        return null;
    }

    @Override
    public Pattern getTilePattern() { return S2L2TilePattern; }

    @Override
    public String getOrbit() { return getTokens(S2L2Pattern)[3]; }

    @Override
    protected Pattern getNamePattern() { return S2L2Pattern; }

    @Override
    public String getSensingDate() {
        return getTokens(S2L2Pattern)[1];
    }
}
