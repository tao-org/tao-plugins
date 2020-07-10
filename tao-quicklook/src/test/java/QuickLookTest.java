import ro.cs.tao.eodata.quicklook.Sentinel2L2AQuicklookGenerator;

import java.nio.file.Paths;

public class QuickLookTest {

    public static void main(String[] args) {
        Sentinel2L2AQuicklookGenerator generator = new Sentinel2L2AQuicklookGenerator();
        String productPath = "E:\\S2B_MSIL2A_20190225T081919_N0211_R121_T36RXV_20190225T125206.SAFE";
        System.out.println(generator.handle(Paths.get(productPath)));
    }

}
