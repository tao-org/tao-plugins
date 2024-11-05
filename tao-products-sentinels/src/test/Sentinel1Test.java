import org.junit.Test;
import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.products.sentinels.Sentinel1MetadataInspector;
import ro.cs.tao.products.sentinels.Sentinel2MetadataInspector;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Sentinel1Test {

    @Test
    public void runSentinel1Inspection() {
        Path productPath = Paths.get("W:\\download\\S1B_IW_SLC__1SDV_20190613T161857_20190613T161924_016680_01F645_68DA.SAFE");
        Sentinel1MetadataInspector inspector = new Sentinel1MetadataInspector();
        try {
            MetadataInspector.Metadata metadata = inspector.getMetadata(productPath);
            System.out.println(metadata.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void runSentinel12nspection() {
        Path productPath = Paths.get("W:\\mnt\\tao\\working_dir\\public\\S2A_MSIL1C_20181107T105231_N0207_R051_T32UME_20181107T111341.SAFE");
        Sentinel2MetadataInspector inspector = new Sentinel2MetadataInspector();
        try {
            MetadataInspector.Metadata metadata = inspector.getMetadata(productPath);
            System.out.println(metadata.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
