import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.products.maja.Landsat8MetadataInspector;
import ro.cs.tao.products.maja.Sentinel2MetadataInspector;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MuscateTest {

    public static void main(String[] args) {
        runSentinel1Inspection();
    }

    private static void runSentinel1Inspection() {
        Path productPath;
        MetadataInspector inspector;
        MetadataInspector.Metadata metadata;
        try {
            productPath = Paths.get("E:\\Muscate\\S2B_MSIL2A_20190706T093049_N0207_R136_T34TFQ_20190706T113524.SAFE");
            inspector = new Sentinel2MetadataInspector();
            metadata = inspector.getMetadata(productPath);
            System.out.println(metadata.toString());
            productPath = Paths.get("E:\\Muscate\\LC08_L2A_185029_20190616_20190620_01_T1");
            inspector = new Landsat8MetadataInspector();
            metadata = inspector.getMetadata(productPath);
            System.out.println(metadata.toString());
            productPath = Paths.get("E:\\Muscate\\S2A_MSIL2A_20171003T105021_N0205_R051_T31UES_20171003T105024.SAFE");
            inspector = new ro.cs.tao.products.maccs.Sentinel2MetadataInspector();
            metadata = inspector.getMetadata(productPath);
            System.out.println(metadata.toString());
            productPath = Paths.get("E:\\Muscate\\LC08_L2A_185029_20190616_20190620_01_T1");
            inspector = new ro.cs.tao.products.maccs.Landsat8MetadataInspector();
            metadata = inspector.getMetadata(productPath);
            System.out.println(metadata.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
