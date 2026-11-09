package ro.cs.tao.tests;

import org.junit.Test;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.odata.CreoDiasODataSource;
import ro.cs.tao.datasource.tests.DataSourceTest;

/**
 * @author Efrem-Hagiu Stefan
 */
public class CreoDias2DataSourceTest extends DataSourceTest<CreoDiasODataSource> {

    public CreoDias2DataSourceTest() {
        super(CreoDiasODataSource.class);
    }

    @Test
    public void testCreoDiasNew_Sentinel1() {
        testSensor("Sentinel1", "SLC", false);
    }

    @Test
    public void testCreoDiasNew_Sentinel2() {
        testSensor("Sentinel2", "S2MSI1C", false);
    }

    @Test
    public void testCreoDiasNew_Sentinel3() {
        QueryParameter<String> instrument = new QueryParameter<>(String.class, "instrument", "SLSTR");
        QueryParameter<String> processingLevel = new QueryParameter<>(String.class, "processingLevel", "2");
        QueryParameter<String> productSize = new QueryParameter<>(String.class, "productSize", "FRAME");
        testSensor("Sentinel3", "SL_2_LST___", false, instrument, processingLevel, productSize);
    }

    @Test
    public void testCreoDiasNew_Sentinel5P() {
        testSensor("Sentinel5P", "L1B_RA_BD1", false);
    }

    @Test
    public void testCreoDiasNew_Landsat8() {
        testSensor("Landsat8", "L1TP", false);
    }
}
