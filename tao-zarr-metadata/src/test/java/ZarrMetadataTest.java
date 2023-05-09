import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.products.zarr.ZarrMetadataInspector;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ZarrMetadataTest {

    public static void main(String[] args) {
        runMetadataTest();
    }

    private static void runMetadataTest() {
        Path productPath;
        MetadataInspector inspector;
        MetadataInspector.Metadata metadata;
        try {
            productPath = Paths.get("D:\\mnt\\tao\\working_dir\\admin\\31-110-gdal_to_xarray\\S2A_20210908093031_NDVI_ZARR");
            inspector = new ZarrMetadataInspector();
            metadata = inspector.getMetadata(productPath);
            System.out.println(metadata.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
