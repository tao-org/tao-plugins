package ro.cs.tao.tests;

import org.junit.Assert;
import org.junit.Test;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.tests.DataSourceTest;
import ro.cs.tao.datasource.usgs.USGSDataSource;
import ro.cs.tao.eodata.EOProduct;

import java.util.List;

/**
 * @author Efrem-Hagiu Stefan
 */
public class LandsatUsgsTest extends DataSourceTest<USGSDataSource> {

    public LandsatUsgsTest() {
        super(USGSDataSource.class);
    }

    @Test
    public void testDownloadLandsat8C2_L1() {
        QueryParameter<String> platformName = new QueryParameter<>(String.class, CommonParameterNames.PLATFORM, "landsat_ot_c2_l1");
        QueryParameter<Integer> maxClouds = new QueryParameter<>(int.class,"maxClouds", 100);
        testSensor("Landsat8", "L1TP", false, platformName, maxClouds);
    }

    @Test
    public void testDownloadLandsat8C2_L2() {
        QueryParameter<String> platformName = new QueryParameter<>(String.class, CommonParameterNames.PLATFORM, "landsat_ot_c2_l2");
        QueryParameter<Integer> maxClouds = new QueryParameter<>(int.class,"maxClouds", 100);
        testSensor("Landsat8", "L1GS", false, platformName, maxClouds);
    }

    @Test
    public void testDownloadLandsat9_C2_L1() {
        QueryParameter<String> platformName = new QueryParameter<>(String.class, CommonParameterNames.PLATFORM, "landsat_ot_c2_l1");
        testSensor("Landsat9", "L1TP", false, platformName);
    }

    @Test
    public void testDownloadLandsat9_C2_L2() {
        QueryParameter<String> platformName = new QueryParameter<>(String.class, CommonParameterNames.PLATFORM, "landsat_ot_c2_l2");
        testSensor("Landsat9","L1TP", false, platformName);
    }

//    @Test
//    public void testEcoStressSearch() {
//        ecoStressSearch("ecostress_eco1batt");
//    }

    @Override
    protected void testAssertions(List<EOProduct> results) {
        Assert.assertNotNull(results);
        Assert.assertNotEquals(0, results.size());
        Assert.assertNotNull(results.getFirst());
        Assert.assertNotNull(results.getFirst().getId());
    }

}
