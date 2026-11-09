package ro.cs.tao.tests;

import org.junit.Assert;
import org.junit.Test;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.eocat.EOCATDataSource;
import ro.cs.tao.datasource.tests.DataSourceTest;
import ro.cs.tao.eodata.EOProduct;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * @author Efrem-Hagiu Stefan
 */
public class EOCATDataSourceTest extends DataSourceTest<EOCATDataSource> {
    public EOCATDataSourceTest() {
        super(EOCATDataSource.class);
    }

    @Test
    public void testKOMPSAT2() {
        testEOCAT("KOMPSAT-2",false, 0);
    }

    @Test
    public void testERS1() {
        testEOCAT("ERS-1", false, 12);
    }

    @Test
    public void testCryoSat2() {
        testEOCAT("CryoSat-2", false, 0);
    }

    @Test
    public void testEnvisat() {
        testEOCAT("Envisat",false, 17);
    }

    private void testEOCAT(String sensor, boolean download, int parentIdentifier,  QueryParameter<?>... additionalParams) {
        try {
            DataSource<?, ?> dataSource = new EOCATDataSource();
            dataSource.setCredentials(user, password);
            Map<String, Map<String, DataSourceParameter>> supportedParams = dataSource.getSupportedParameters();
            Map<String, DataSourceParameter> params = supportedParams.get(sensor);
            String collection = (String) params.get("parentIdentifier").getValueSet()[parentIdentifier];
            DataQuery query = dataSource.createQuery(sensor);
            query.addParameter(new QueryParameter<>(String.class, "parentIdentifier", collection));
            query.addParameter(startDate);
            query.addParameter(endDate);
            if (additionalParams != null) {
                for (QueryParameter<?> parameter : additionalParams) {
                    query.addParameter(parameter);
                }
            }
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
            Assert.fail("Exception in testing the data source: " + e.getMessage());
        }
    }

}
