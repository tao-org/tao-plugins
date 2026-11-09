package ro.cs.tao.tests;

import org.junit.Test;
import ro.cs.tao.datasource.remote.lsa.LSADataSource;
import ro.cs.tao.datasource.tests.DataSourceTest;

/**
 * @author Efrem-Hagiu Stefan
 */
public class LSADataSourceTest extends DataSourceTest<LSADataSource> {

    public LSADataSourceTest() {
        super(LSADataSource.class);
    }

    @Test
    public void testLSA_Sentinel1() {
        testSensor("Sentinel1", "S1_SAR_GRD", false);
    }
    @Test
    public void testLSA_Sentinel1_2() {
        testSensor("Sentinel1", "S1_SAR_SLC", false);
    }

    @Test
    public void testLSA_Sentinel2() {
        testSensor("Sentinel2", "S2_MSIL2A", false);
    }

    @Test
    public void testLSA_Sentinel2_2() {
        testSensor("Sentinel2", "S2_MSIL1C", false);
    }

}
