import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.products.sentinels.Sentinel1MetadataInspector;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Sentinel1Test {

    public static void main(String[] args) {
        runSentinel1Inspection();
    }

    private static void runSentinel1Inspection() {
        Path productPath = Paths.get("D:\\download\\Italy_Sep_Dec_44\\S1A_IW_SLC__1SDV_20180625T165858_20180625T165925_022516_027050_C3B1.SAFE");
        Sentinel1MetadataInspector inspector = new Sentinel1MetadataInspector();
        try {
            MetadataInspector.Metadata metadata = inspector.getMetadata(productPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
