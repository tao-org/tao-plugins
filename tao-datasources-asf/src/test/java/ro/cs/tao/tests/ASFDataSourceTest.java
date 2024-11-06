package ro.cs.tao.tests;

import org.junit.Test;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.asf.ASFDataSource;
import ro.cs.tao.datasource.tests.DataSourceTest;
import ro.cs.tao.eodata.EOProduct;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Efrem-Hagiu Stefan
 */
public class ASFDataSourceTest extends DataSourceTest<ASFDataSource> {
    public ASFDataSourceTest() {
        super(ASFDataSource.class);
    }

    @Test
    public void testASFSentinel1() {
        testSensor("Sentinel1", "SLC", false);
    }

    @Test
    public void testASF_SMAP_L1A() {
        testSensor("SMAP", "L1A_Radar_RO_HDF5", false);
    }

    @Test
    public void testASFSentinel1WithFilters() {
        testSensor("Sentinel1", "Funny name", false);
    }


    @Override
    protected void testSensor(String sensor, String productType, boolean download, QueryParameter<?>... additionalParams) {
        try {
            DataSource<?, ?> dataSource = getInstance();
            dataSource.setCredentials(user, password);
            if(productType.equals("Funny name")) {
                dataSource.setFilteredParameters(makeFilterParameters(sensor));
            }
            DataQuery query = dataSource.createQuery(sensor);
            query.addParameter(startDate);
            query.addParameter(endDate);
            query.addParameter(aoi);
            if (additionalParams != null) {
                for (QueryParameter<?> parameter : additionalParams) {
                    query.addParameter(parameter);
                }
            }
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, productType);
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


    private Map<String, Map<String, Map<String, String>>> makeFilterParameters(String sensor) {
        final Map<String, Map<String, Map<String, String>>> filteredParameters = new HashMap<>();
        filteredParameters.put(sensor, new HashMap<>());
        filteredParameters.get(sensor).put("productType", new HashMap<>());
        final Map<String, String> productType = filteredParameters.get(sensor).get("productType");
        productType.put("GRD_HS", "GRD_HS");
        productType.put("GRD_HD", "GRD_HD");
        productType.put("GRD_MS", "GRD_MS");
        productType.put("GRD_MD", "GRD_MD");
        productType.put("GRD_FS", "GRD_FS");
        productType.put("GRD_FD", "GRD_FD");
        productType.put("SLC", "SLC");
        productType.put("OCN", "Funny name");
        return filteredParameters;
    }

}
