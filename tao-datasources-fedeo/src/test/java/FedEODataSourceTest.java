import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.fedeo.FedEODataSource;
import ro.cs.tao.datasource.remote.fedeo.download.FedEODownloadStrategy;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Cosmin Cara
 */
public class FedEODataSourceTest {

    public static void main(String[] args) {
        DataSource dataSource = initAndGetDatasource();
        test_ping(dataSource);
        kompsat2_Test(dataSource);
        envisat_Test(dataSource);
//        groupCollectionsByQueryAuthentication(dataSource);
    }

    private static DataSource initAndGetDatasource() {
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.INFO);
        }
        ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
        DataSource dataSource = serviceRegistry.getService(FedEODataSource.class);
        dataSource.setCredentials("", "");
        return dataSource;
    }

    private static String getPlatformName(DataSource dataSource, int platformIndex) {
        String[] platforms = dataSource.getSupportedSensors();
        if (platforms.length < 1) {
            throw new IllegalStateException("no platforms for FedEO");
        }
        return platforms[platformIndex];
    }

    private static void test_ping(DataSource dataSource) {
        if (dataSource.ping()) {
            System.out.println("Ping successful");
        } else {
            System.out.println("Ping failed");
        }
    }

    private static List<EOProduct> test_search(DataSource dataSource, String platform, int collectionIndex) {
        Map<String, Map<String, DataSourceParameter>> supportedParams = dataSource.getSupportedParameters();
        Map<String, DataSourceParameter> params = supportedParams.get(platform);
        String collection = (String) params.get("parentIdentifier").getValueSet()[collectionIndex];
        DataQuery query = dataSource.createQuery(platform);
        query.addParameter(new QueryParameter<>(String.class, "parentIdentifier", collection));
        query.addParameter(CommonParameterNames.START_DATE, LocalDateTime.of(2011, 3, 21, 0, 0, 0, 0));
        query.addParameter(CommonParameterNames.END_DATE, LocalDateTime.of(2021, 3, 22, 0, 0, 0, 0));
        Polygon2D aoi = Polygon2D.fromWKT("POLYGON((28.7037 44.1142,20.4266 44.1142,20.4266 49.0227,28.7037 49.0227,28.7037 44.1142))");
        query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
        query.setPageSize(10);
        query.setPageNumber(1);

        long count = query.getCount();
        System.out.println("\n\nFound " + count + " products.\nFetching list with first 10 products (maximum)\n");
        List<EOProduct> results = query.execute();
        results.forEach(r -> {
            System.out.println("ID=" + r.getId());
            System.out.println("NAME=" + r.getName());
            System.out.println("LOCATION=" + r.getLocation());
            System.out.println("FOOTPRINT=" + r.getGeometry());
            System.out.println("Attributes ->");
            r.getAttributes().forEach(a -> System.out.println("\tName='" + a.getName() + "', value='" + a.getValue() + "'"));
        });
        if (results.isEmpty()) {
            throw new IllegalStateException("Search failed. No products found.");
        }
        return results;
    }

    private static void testDownload(DataSource dataSource, String platform, List<EOProduct> results) {
        try {
            final FedEODownloadStrategy strategy = (FedEODownloadStrategy) dataSource.getProductFetchStrategy(platform);
            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                Path path = strategy.fetch(results.get(0));
                if (path != null) {
                    System.out.println("Product downloaded at " + path.toString());
                } else {
                    System.out.println("Product not downloaded");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void kompsat2_Test(DataSource dataSource) {
        String kompsat2Platform = getPlatformName(dataSource, 135);
        List<EOProduct> searchResults = test_search(dataSource, kompsat2Platform, 0);
        testDownload(dataSource, kompsat2Platform, searchResults);
    }

    private static void envisat_Test(DataSource dataSource) {
        String envisatPlatform = getPlatformName(dataSource, 70);
        List<EOProduct> searchResults = test_search(dataSource, envisatPlatform, 0);
        testDownload(dataSource, envisatPlatform, searchResults);
    }

    private static void groupCollectionsByQueryAuthentication(DataSource dataSource) {
        String[] platforms = dataSource.getSupportedSensors();
        List<String> noAuthCollections = new ArrayList<>();
        List<String> withAuthCollections = new ArrayList<>();
        for (String platform : platforms) {
            Map<String, Map<String, DataSourceParameter>> supportedParams = dataSource.getSupportedParameters();
            Map<String, DataSourceParameter> params = supportedParams.get(platform);

            Object[] collections = params.get("parentIdentifier").getValueSet();
            for (Object collectionO : collections) {
                String collection = (String) collectionO;
                try {
                    DataQuery query = dataSource.createQuery(platform);
                    query.addParameter(new QueryParameter<>(String.class, "parentIdentifier", collection));
                    if (params.containsKey(CommonParameterNames.START_DATE)) {
                        query.addParameter(CommonParameterNames.START_DATE, LocalDateTime.of(2011, 3, 21, 0, 0, 0, 0));
                    }
                    if (params.containsKey(CommonParameterNames.END_DATE)) {
                        query.addParameter(CommonParameterNames.END_DATE, LocalDateTime.of(2021, 3, 22, 0, 0, 0, 0));
                    }
                    if (params.containsKey(CommonParameterNames.FOOTPRINT)) {
                        Polygon2D aoi = Polygon2D.fromWKT("POLYGON((28.7037 44.1142,20.4266 44.1142,20.4266 49.0227,28.7037 49.0227,28.7037 44.1142))");
                        query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
                    }
                    query.setPageSize(2);
                    query.setPageNumber(1);
                    List<EOProduct> results = query.execute();
                    if (results == null) {
                        throw new NullPointerException();
                    }
                    if (!noAuthCollections.contains(collection)) {
                        noAuthCollections.add(collection);
                    }
                    System.out.println(platform + " - " + collection + " :OK. ");
                } catch (Exception ex) {
                    if (ex.getMessage() != null && (ex.getMessage().contains("401") || ex.getMessage().contains("403")) && !withAuthCollections.contains(collection)) {
                        if (!withAuthCollections.contains(collection)) {
                            withAuthCollections.add(collection);
                        }
                    } else {
                        if (!noAuthCollections.contains(collection)) {
                            noAuthCollections.add(collection);
                        }
                    }
                    System.err.println(platform + " - " + collection + " :FAIL. " + ex.getMessage());
                }
            }
        }
        System.out.println("Collections without authentication:");
        for (String noAuthCollection : noAuthCollections) {
            System.out.println(noAuthCollection);
        }
        System.out.println("\n\nCollections with authentication:");
        for (String withAuthCollection : withAuthCollections) {
            System.out.println(withAuthCollection);
        }
    }

    private static ServiceRegistry<DataSource> getServiceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }
}
