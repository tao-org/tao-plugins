import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.configuration.EmptyConfigurationProvider;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.usgs.USGSDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Cosmin Cara
 */
public class LandsatUsgsTest {

    public static void main(String[] args) throws Exception {
        setup();
        //downloadTest();
        searchEcostressTest();
//        searchLandsat8Test();
//        searchNewLandsat8Test();
        System.exit(0);
    }

    static void downloadTest() throws IOException {
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.INFO);
        }
        DataSource<?, ?> dataSource = getDatasourceRegistry().getService(USGSDataSource.class);
        dataSource.setCredentials("", "");
//        dataSource.setCredentials("lidia901", "USGS_Password1");
        String[] sensors = dataSource.getSupportedSensors();
//        String sensor = "ECOSTRESS-WUE";
//        String sensor = "MODIS-FIRE-8";
        String sensor = "Landsat9";
        DataQuery query = dataSource.createQuery(sensor);

        QueryParameter<LocalDateTime> begin = query.createParameter(CommonParameterNames.START_DATE, LocalDateTime.class);
        begin.setValue(LocalDateTime.of(2022, 5, 1, 0, 0, 0, 0));
        query.addParameter(begin);
        QueryParameter<LocalDateTime> end = query.createParameter(CommonParameterNames.END_DATE, LocalDateTime.class);
        end.setValue(LocalDateTime.of(2022, 6, 6, 0, 0, 0, 0));
        query.addParameter(end);
        query.addParameter(CommonParameterNames.TILE, "192026");
        Polygon2D aoi = Polygon2D.fromWKT("MULTIPOLYGON(((13.8407544343395 48.8698778299916,13.8301934869479 49.2557675230918,14.4620185534294 49.2124261409465,14.4206837170399 48.8286933623671,13.8407544343395 48.8698778299916)))");

        query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
//        query.addParameter(CommonParameterNames.PRODUCT, "LC08_L1TP_199024_20201026_20201026_01_RT");
        query.addParameter(CommonParameterNames.PLATFORM, "landsat_ot_c2_l1");
//        query.addParameter(CommonParameterNames.PRODUCT_TYPE, "L1TP");
//        query.addParameter("maxClouds", 100);
        query.setMaxResults(5);
        List<EOProduct> results = query.execute();
        results.forEach(r -> {
            System.out.println("ID=" + r.getId());
            System.out.println("NAME=" + r.getName());
            System.out.println("PRODUCT TYPE=" + r.getProductType());
            System.out.println("LOCATION=" + r.getLocation());
            System.out.println("FOOTPRINT=" + r.getGeometry());
            System.out.println("QUICK LOOK=" + r.getQuicklookLocation());
        });

        /*final Landsat8Strategy strategy = (Landsat8Strategy) dataSource.getProductFetchStrategy(sensor);
//        final EcostressStrategy strategy = (EcostressStrategy) dataSource.getProductFetchStrategy(sensor);
        //final ModisStrategy strategy = (ModisStrategy) dataSource.getProductFetchStrategy(sensor);

        ProgressListener progressListener = new ProgressListener() {
            @Override
            public void started(String taskName) {
                System.out.println("Started " + taskName);
            }

            @Override
            public void subActivityStarted(String subTaskName) {
                System.out.println("Started " + subTaskName);
            }

            @Override
            public void subActivityEnded(String subTaskName) {
                System.out.println("Completed " + subTaskName);
            }

            @Override
            public void ended() {
                System.out.println("Done");
            }

            @Override
            public void notifyProgress(double progressValue) {
                System.out.println("Progress : " + progressValue);
            }

            @Override
            public void notifyProgress(String subTaskName, double subTaskProgress) {
                System.out.println("Progress: " + subTaskName + "," + subTaskProgress);
            }

            @Override
            public void notifyProgress(String subTaskName, double subTaskProgress, double overallProgress) {
                System.out.println("Progress: " + overallProgress + " - " + subTaskName + "," + subTaskProgress);
            }
        };
        strategy.setProgressListener(progressListener);

        if (!results.isEmpty()) {
            strategy.setFetchMode(FetchMode.OVERWRITE);
            strategy.setDestination("E:\\testTAO");
            Path path = strategy.fetch(results.get(0));
            if (path != null) {
                System.out.println("Product downloaded at " + path.toString());
            } else {
                System.out.println("Product not downloaded");
            }
        }*/
    }

    static void searchLandsat8Test() throws Exception {
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.INFO);
        }
        DataSource<?, ?> dataSource = getDatasourceRegistry().getService(USGSDataSource.class);
        dataSource.setCredentials("", "");
        String[] sensors = dataSource.getSupportedSensors();

        DataQuery query = dataSource.createQuery("Landsat8");
        QueryParameter<LocalDateTime> begin = query.createParameter(CommonParameterNames.START_DATE, LocalDateTime.class);
        begin.setValue(LocalDateTime.of(2020, 11, 1, 0, 0, 0, 0));
        query.addParameter(begin);
        QueryParameter<LocalDateTime> end = query.createParameter(CommonParameterNames.END_DATE, LocalDateTime.class);
        end.setValue(LocalDateTime.of(2020, 12, 31, 0, 0, 0, 0));
        query.addParameter(end);
        Polygon2D aoi = Polygon2D.fromWKT("POLYGON((0.5688037328 42.426901687,1.9031483158 42.4474430295,1.9199728398 41.4586816132,0.6060608364 41.438836285,0.5688037328 42.426901687))");

        query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
        //query.addParameter(CommonParameterNames.PRODUCT, "LC08_L1TP_199024_20201026_20201026_01_RT");
        query.addParameter(CommonParameterNames.PLATFORM, "LANDSAT_8_C1");
        query.addParameter(CommonParameterNames.PRODUCT_TYPE, "L1TP");
        query.addParameter("maxClouds", 100);
        query.setMaxResults(5);
        List<EOProduct> results = query.execute();
        results.forEach(r -> {
            System.out.println("ID=" + r.getId());
            System.out.println("NAME=" + r.getName());
            System.out.println("LOCATION=" + r.getLocation());
            System.out.println("FOOTPRINT=" + r.getGeometry());
        });
    }

    static void searchEcostressTest() throws Exception {
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.INFO);
        }
        DataSource<?, ?> dataSource = getDatasourceRegistry().getService(USGSDataSource.class);
        dataSource.setCredentials("kraftek", "cei7pitici.");
        String[] sensors = dataSource.getSupportedSensors();

        DataQuery query = dataSource.createQuery("ECOSTRESS");
        QueryParameter<String> product = query.createParameter(CommonParameterNames.PRODUCT, String.class);
        product.setValue("L1B_ATT_32259_20240314T231351");
        query.addParameter(product);
        QueryParameter<String> collection = query.createParameter(CommonParameterNames.PLATFORM, String.class);
        collection.setValue("ecostress_eco1batt");
        query.addParameter(collection);
        List<EOProduct> results = query.execute();
        results.forEach(r -> {
            System.out.println("ID=" + r.getId());
            System.out.println("NAME=" + r.getName());
            System.out.println("LOCATION=" + r.getLocation());
            System.out.println("FOOTPRINT=" + r.getGeometry());
        });
    }

    static void searchNewLandsat8Test() throws Exception {
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.FINE);
        }
        DataSource<?, ?> dataSource = getDatasourceRegistry().getService(USGSDataSource.class);
        dataSource.setCredentials("", "");
        String[] sensors = dataSource.getSupportedSensors();

        DataQuery query = dataSource.createQuery("Landsat8");
        QueryParameter<LocalDateTime> begin = query.createParameter(CommonParameterNames.START_DATE, LocalDateTime.class);
        begin.setValue(LocalDateTime.of(2019, 1, 1, 0, 0, 0, 0));
        query.addParameter(begin);
        QueryParameter<LocalDateTime> end = query.createParameter(CommonParameterNames.END_DATE, LocalDateTime.class);
        end.setValue(LocalDateTime.of(2019, 12, 31, 0, 0, 0, 0));
        query.addParameter(end);
        Polygon2D aoi = Polygon2D.fromWKT("POLYGON((0.5688037328 42.426901687,1.9031483158 42.4474430295,1.9199728398 41.4586816132,0.6060608364 41.438836285,0.5688037328 42.426901687))");

        query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
        query.addParameter(CommonParameterNames.PLATFORM, "LANDSAT_OT_C2_L2");
        //query.addParameter(CommonParameterNames.PRODUCT_TYPE, "L2SP");
        query.setPageSize(10);
        query.setMaxResults(50);
        //System.out.printf("Count returned %d%n", query.getCount());
        List<EOProduct> results = query.execute();
        results.forEach(r -> {
            System.out.println("ID=" + r.getId());
            System.out.println("NAME=" + r.getName());
            System.out.println("LOCATION=" + r.getLocation());
            System.out.println("FOOTPRINT=" + r.getGeometry());
        });
        /*final ProductFetchStrategy fetchStrategy = dataSource.getProductFetchStrategy(sensors[0]);
        ProgressListener progressListener = new ProgressListener() {
            @Override
            public void started(String taskName) {
                System.out.println("Started " + taskName);
            }

            @Override
            public void subActivityStarted(String subTaskName) {
                System.out.println("Started " + subTaskName);
            }

            @Override
            public void subActivityEnded(String subTaskName) {
                System.out.println("Completed " + subTaskName);
            }

            @Override
            public void ended() {
                System.out.println("Done");
            }

            @Override
            public void notifyProgress(double progressValue) {
                System.out.println("Progress : " + progressValue);
            }

            @Override
            public void notifyProgress(String subTaskName, double subTaskProgress) {
                System.out.println("Progress: " + subTaskName + "," + subTaskProgress);
            }

            @Override
            public void notifyProgress(String subTaskName, double subTaskProgress, double overallProgress) {
                System.out.println("Progress: " + overallProgress + " - " + subTaskName + "," + subTaskProgress);
            }
        };
        fetchStrategy.setProgressListener(progressListener);
        ((DownloadStrategy) fetchStrategy).setDestination(ConfigurationManager.getInstance().getValue("products.location"));
        fetchStrategy.fetch(results.get(0));*/
    }

    private static void setup() {
        ConfigurationProvider configurationProvider = new EmptyConfigurationProvider();
        final String userHome = System.getProperty("user.home");
        configurationProvider.setValue("products.location", userHome);
        configurationProvider.setScriptsFolder(Paths.get(userHome));
        configurationProvider.setConfigurationFolder(Paths.get(userHome));
        ConfigurationManager.setConfigurationProvider(configurationProvider);
    }

    private static ServiceRegistry<DataSource> getDatasourceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }

}
