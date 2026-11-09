package ro.cs.tao.tests;

import org.junit.Assert;
import org.junit.Test;
import ro.cs.tao.datasource.CollectionDescription;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.eumetsat.EumetsatDataSource;
import ro.cs.tao.datasource.tests.DataSourceTest;
import ro.cs.tao.eodata.EOProduct;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class EumetsatDataSourceTest extends DataSourceTest<EumetsatDataSource> {


    public EumetsatDataSourceTest() {
        super(EumetsatDataSource.class);
    }

    @Test
    public void testOne() {
        testEumetsatCollections();
    }

    @Test
    public void testTwo() {
        testEumetsat("EO:EUM:DAT:0959", true);
    }

    private void testEumetsatCollections() {
        try {
            DataSource<?, ?> dataSource = new EumetsatDataSource();
            dataSource.setCredentials(user, password);
            String[] sensors = dataSource.getSupportedSensors();
            System.out.println("Supported sensors (initial call): " + sensors.length);
            Map<String, CollectionDescription> sensorTypes = dataSource.getSensorTypes();
            for (Map.Entry<String, CollectionDescription> entry : sensorTypes.entrySet()) {
                System.out.println("Collection: " + entry.getKey());
                System.out.println(entry.getValue().getDescription());
            }
            sensors = dataSource.getSupportedSensors();
            System.out.println("Supported sensors (final call): " + sensors.length);
        } catch (Exception e) {
            Assert.fail("Exception in testing the data source: " + e.getMessage());
        }
    }

    private void testEumetsat(String sensor, boolean download, QueryParameter<?>... additionalParams) {
        try {
            DataSource<?, ?> dataSource = new EumetsatDataSource();
            dataSource.setCredentials(user, password);
            Map<String, Map<String, DataSourceParameter>> supportedParams = dataSource.getSupportedParameters();
            Map<String, DataSourceParameter> params = supportedParams.get(sensor);
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
