package ro.cs.tao.products.sentinels;

import ro.cs.tao.eodata.util.BaseProductHelper;

import java.util.regex.Pattern;

public class Sentinel1OrbitFileHelper extends BaseProductHelper {
    /**
     * Tokens:
     * 0: Mission identifier
     * 1: File class
     * 2: File category
     * 3: Semantic descriptor (file type)
     * 4: Site centre
     * 5: Creation date
     * 6: Option identifier
     * 7: Validity start time and Validity stop time
     * 7: (alternative) Source data
     * 8: File extension
     */
    static final Pattern S1AuxPattern =
            Pattern.compile("(S1[A-B])_(OPER|TEST|REP\\d|TD\\d{2})_([A-Z]{3})_([A-Z0-9_]{6})_(OPOD)_(\\d{8}T\\d{6})_([DV])(\\d{8}T\\d{6}_\\d{8}T\\d{6}|[GDNS_]{3})(.EOF|.eof|.HDR|.hdr|.DBL|.dbl|.ZIP|.zip)");

    public Sentinel1OrbitFileHelper() {
        super();
    }

    public Sentinel1OrbitFileHelper(String name) {
        super(name);
        this.processingDate = getTokens(S1AuxPattern, name, null)[5];
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Sentinel1OrbitFileHelper duplicate() {
        return new Sentinel1OrbitFileHelper(getName());
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

    public FileCategory getFileCategory() {
        return Enum.valueOf(FileCategory.class, getTokens(S1AuxPattern, this.name, null)[2]);
    }

    public FileType getFileType() {
        return Enum.valueOf(FileType.class, getTokens(S1AuxPattern, this.name, null)[3]);
    }

    public String getValidityStart() {
        final String token = getTokens(S1AuxPattern, this.name, null)[7];
        return token.length() > 4 ? token.split("_")[0]: null;
    }

    public String getValidityEnd() {
        final String token = getTokens(S1AuxPattern, this.name, null)[7];
        return token.length() > 4 ? token.split("_")[1]: null;
    }

    public String getExtension() {
        return getTokens(S1AuxPattern, this.name, null)[8];
    }

    @Override
    protected boolean verifyProductName(String name) {
        return S1AuxPattern.matcher(name).matches();
    }

    public enum FileCategory {
        AUX, CNF, LOG, MPL, REP
    }

    public enum FileType {
        PREORB, RESORB, MOEORB, POEORB, RESATT, PRLPTF, PRCPTF, GNSSRX, GNSSRD, PROQUA
    }
}
