package ro.cs.tao.tests;

import org.junit.Test;
import ro.cs.tao.datasource.CollectionDescription;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.remote.earthdata.EarthDataSource;
import ro.cs.tao.datasource.tests.DataSourceTest;
import ro.cs.tao.eodata.EOProduct;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * @author Efrem-Hagiu Stefan
 */
public class NasaDataSourceTest extends DataSourceTest<EarthDataSource> {


    private static final String token = "eyJ0eXAiOiJKV1QiLCJvcmlnaW4iOiJFYXJ0aGRhdGEgTG9naW4iLCJzaWciOiJlZGxqd3RwdWJrZXlfb3BzIiwiYWxnIjoiUlMyNTYifQ.eyJ0eXBlIjoiVXNlciIsInVpZCI6InZuZXRvaXUiLCJleHAiOjE3MjE1NTE4MzksImlhdCI6MTcxNjM2NzgzOSwiaXNzIjoiRWFydGhkYXRhIExvZ2luIn0.QgZSZf4NsqNanuHaJLDE3LGW9ShgFAdgRHj2vNL3-ahFwnUsTc7KY5bhj0BRtEHTgEdRM_Ak029uqhVOBtEoo5yIFa9qa5dYCihICio7mskE7ddBH7qHWOUz0Iq-yJQXBTWif1uOqUPeFsNxscsics-VQyvrJ4_fZhKr-Q1UGcJi-EzQrl--S-K-ipDFm6abhRYCT8tiTwOMz0a0EQOWNnbgf7-SBKAlGDHo4rzHlhxvY8v6JbGUQ05r0YwKdfWOHkanNMi0eqS1p1OxEI2pMGlRIL0RQ3DAuojJfDRabIvcOkOZ0G3IXf8BZDoCW-4hPTQ52arGX_9c5thCqEIE9g";

    public NasaDataSourceTest() {
        super(EarthDataSource.class);
    }

    @Test
    public void test_MODIS_CHLM() {
        testEarthData("MODIS-CHLM", false);
    }

    @Test
    public void test_MODIS_CHLB() {
        testEarthData("MODIS-CHLB", false);
    }

    @Test
    public void test_ICESAT2() {
        testEarthData("IceSAT-2-ATL08V3", false);
    }


    private void testEarthData(String sensor, boolean download) {
        try {
            DataSource<?, ?> dataSource = new EarthDataSource();
            dataSource.setCredentials(user, password);
            DataQuery query = dataSource.createQuery(sensor);
            setTokenIfNeeded(dataSource, sensor);
            query.addParameter(startDate);
            query.addParameter(endDate);
            query.addParameter(aoi);
            query.setMaxResults(maxResults);
            query.setPageSize(pageSize);
            List<EOProduct> results = query.execute();
            testAssertions(results);
            if (download) {
                final ProductFetchStrategy strategy = dataSource.getProductFetchStrategy(sensor);
                if (!results.isEmpty()) {
                    strategy.setProgressListener(listener);
                    Path path = strategy.fetch(results.getFirst());
                    strategy.cancel();
                    if (path != null) {
                        System.out.println("Product downloaded at " + path);
                    } else {
                        System.out.println("Product not downloaded");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setTokenIfNeeded(DataSource dataSource, String sensor) {
        final Map<String, CollectionDescription> sensorTypes = dataSource.getSensorTypes();
        if (sensorTypes.get(sensor).isTokenNeeded()) {
            dataSource.setBearerToken(token);
        }
    }
}
