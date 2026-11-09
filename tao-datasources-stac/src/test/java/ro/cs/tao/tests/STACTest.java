package ro.cs.tao.tests;

import org.junit.Test;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.stac.STACSource;
import ro.cs.tao.datasource.tests.DataSourceTest;
import ro.cs.tao.eodata.EOProduct;

import java.nio.file.Path;
import java.util.List;

/**
 * @author Efrem-Hagiu Stefan
 */
public class STACTest extends DataSourceTest<STACSource> {

    public STACTest() {
        super(STACSource.class);
    }

    @Test
    public void testSentinel2() {
        testSensor("SENTINEL-2", "", false);
    }

    @Test
    public void testSentinel5P() {
        testSensor("SENTINEL-5P", "", false);
    }

    @Override
    protected void testSensor(String sensor, String productType, boolean download, QueryParameter<?>... additionalParams) {
        try {
            DataSource<?, ?> dataSource = new STACSource("test") {

                @Override
                public String[] getSupportedSensors() {
                    return new String[] { sensor };
                }

                @Override
                protected void readInfo() {
                    this.connectionString = "https://datahub.creodias.eu/stac/";
                    this.authentication = null;
                }
            };
            dataSource.setCredentials(user, password);
            DataQuery query = dataSource.createQuery(sensor);
            query.addParameter(startDate);
            query.addParameter(endDate);
            query.addParameter(aoi);
            if (additionalParams != null) {
                for (QueryParameter<?> parameter : additionalParams) {
                    query.addParameter(parameter);
                }
            }
            query.setMaxResults(maxResults);
            query.setPageSize(pageSize);
            List<EOProduct> results = query.execute();
            testAssertions(results);
            results.forEach(r -> {
                System.out.println("ID=" + r.getId());
                System.out.println("NAME=" + r.getName());
                System.out.println("LOCATION=" + r.getLocation());
                System.out.println("FOOTPRINT=" + r.getGeometry());
                System.out.println("QUICKLOOK=" + r.getQuicklookLocation());
            });
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
}
