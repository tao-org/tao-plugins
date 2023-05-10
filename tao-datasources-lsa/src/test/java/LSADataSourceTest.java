import reactor.core.support.Assert;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.DataSourceComponent;
import ro.cs.tao.datasource.ProductStatusListener;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.lsa.LSADataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;
import ro.cs.tao.utils.executors.monitoring.DownloadProgressListener;

import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Cosmin Cara
 */
public class LSADataSourceTest {

    private static String user;
    private static String password;

    public static void main(String[] args) {
        final DataSource dataSource = initAndGetDatasource();
        //test_ping(dataSource);
        //sentinel1Test(dataSource);
        sentinel2Test(dataSource);
    }

    private static DataSource initAndGetDatasource() {
        final Logger logger = LogManager.getLogManager().getLogger("");
        for (final Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.INFO);
        }
        final ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
        return serviceRegistry.getService(LSADataSource.class);
    }

    private static String getPlatformName(DataSource dataSource, int platformIndex) {
        final String[] platforms = dataSource.getSupportedSensors();
        if (platforms.length < 1) {
            throw new IllegalStateException("no platforms for LSA");
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

    private static List<EOProduct> test_search(DataSource dataSource, String sensor, String productType) {
        final DataSourceComponent component = new DataSourceComponent(sensor, dataSource.getId());
        component.setUserCredentials(user, password);
        final DataQuery query = component.createQuery();
        /*query.addParameter("productType", productType);
        final QueryParameter<LocalDateTime> begin = query.createParameter("startDate", LocalDateTime.class);
        begin.setValue(LocalDateTime.of(2021, 8, 1, 0, 0, 0, 0));
        query.addParameter(begin);
        final Polygon2D aoi = Polygon2D.fromWKT("POLYGON((21.99462890625 47.109375," +
                "27.22412109375 47.1533203125," +
                "27.24609375 44.18701171875," +
                "22.060546875 44.27490234375," +
                "21.99462890625 47.109375))");
        query.addParameter("footprint", aoi);
        query.setPageSize(10);
        query.setPageNumber(1);*/
        query.addParameter(CommonParameterNames.PRODUCT, "S2B_MSIL2A_20201231T105349_N0214_R051_T31UFS_20201231T130556");
        final long count = query.getCount();
        System.out.println("\n\nFound " + count + " products.\nFetching list with first 10 " + dataSource.getId() + " products (maximum)\n");
        final List<EOProduct> results = query.execute();
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

    private static void testDownload(DataSource dataSource, String sensor, List<EOProduct> results) {
        final DataSourceComponent component = new DataSourceComponent(sensor, dataSource.getId());
        component.setUserCredentials(user, password);
        if (!results.isEmpty()) {
            component.setFetchMode(FetchMode.OVERWRITE);
            component.setProgressListener(new DownloadProgressListener() {
                @Override
                public void started(String taskName) {
                    System.out.println("Started " + taskName);
                }

                @Override
                public void subActivityStarted(String subTaskName) {
                    //System.out.println("Started " + subTaskName);
                }

                @Override
                public void subActivityEnded(String subTaskName) {
                    //System.out.println("Finished " + subTaskName);
                }

                @Override
                public void ended() {
                    System.out.println("Download completed");
                }

                @Override
                public void notifyProgress(double progressValue) {
                    System.out.printf("Progress: %.2f%%\r", progressValue * 100);
                }

                @Override
                public void notifyProgress(double progressValue, double transferSpeed) {
                    System.out.printf("Progress: %.2f%%, %.2fMB/s\r", progressValue * 100, transferSpeed);
                }

                @Override
                public void notifyProgress(String subTaskName, double subTaskProgress) {
                    System.out.printf("Progress: %s %.2f%%\n", subTaskName, subTaskProgress * 100);
                }

                @Override
                public void notifyProgress(String subTaskName, double subTaskProgress, double overallProgress) {
                    System.out.printf("Progress: %s %.2f%% (%.2f%%)\n", subTaskName, subTaskProgress * 100, overallProgress * 100);
                }
            });
            component.setProductStatusListener(new ProductStatusListener() {
                @Override
                public boolean downloadStarted(EOProduct product) {
                    return true;
                }

                @Override
                public void downloadCompleted(EOProduct product) {
                    System.out.println("Download " + product.getName() + " completed");
                }

                @Override
                public void downloadFailed(EOProduct product, String reason) {
                    System.out.println("Download " + product.getName() + " failed");
                }

                @Override
                public void downloadAborted(EOProduct product, String reason) {
                    System.out.println("Download " + product.getName() + " aborted");
                }

                @Override
                public void downloadIgnored(EOProduct product, String reason) {
                    System.out.println("Download " + product.getName() + " ignored");
                }

                @Override
                public void downloadQueued(EOProduct product, String reason) {
                    System.out.println("Download " + product.getName() + " queued");
                }
            });
            String folder = ConfigurationManager.getInstance().getValue("product.location");
            List<EOProduct> downloadedResults = component.doFetch(results.subList(0, 2), null, folder);
            Assert.notNull(downloadedResults, "Unexpected result");
            Assert.notEmpty(downloadedResults.toArray(), "The list should have contained one result");
            Assert.isTrue(downloadedResults.size() == 2, "The list should have contained only one result");
            final String location = downloadedResults.get(0).getLocation();
            Assert.notNull(location, "The location of the product should not be null");
            final URI path = URI.create(location);
            Assert.isTrue("file".equals(path.getScheme()), "Product was not downloaded");
            Assert.isTrue(Files.exists(new java.io.File(path).toPath()), "Path not found");
        }
    }

    private static void sentinel1Test(DataSource dataSource) {
        String sentinel1Platform = getPlatformName(dataSource, 0);
        List<EOProduct> searchResults = test_search(dataSource, sentinel1Platform, "S1_SAR_GRD");
        testDownload(dataSource, sentinel1Platform, searchResults);
    }

    private static void sentinel2Test(DataSource dataSource) {
        String sentinel2Platform = getPlatformName(dataSource, 1);
        List<EOProduct> searchResults = test_search(dataSource, sentinel2Platform, "S2_MSIL2A");
        //testDownload(dataSource, sentinel2Platform, searchResults);
    }

    private static ServiceRegistry<DataSource> getServiceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }
}
