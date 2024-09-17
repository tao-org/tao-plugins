package ro.cs.tao.tests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
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
import ro.cs.tao.utils.DateUtils;
import ro.cs.tao.utils.executors.monitoring.DownloadProgressListener;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Efrem-Hagiu Stefan
 */
public class LandsatUsgsTest extends BaseTest {

    private static final int pageSize;
    private static final int maxResults;
    private static final String rowTemplate;
    private static final DownloadProgressListener listener;

    private QueryParameter<LocalDateTime> begin;
    private QueryParameter<LocalDateTime> end;
    private QueryParameter<Polygon2D> aoi;

    private String user = "";
    private String password = "";
    private Path destination;

    static {
        pageSize = 50;
        maxResults = 50;
        rowTemplate = "ID=%s, NAME=%s, LOCATION=%s, QUICKLOOK=%s";
        listener = new DownloadProgressListener() {
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
                System.out.println("Finished " + subTaskName);
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
                System.out.printf("Progress: %.2f%% [%.2fMB/s]\r", progressValue * 100, transferSpeed);
            }

            @Override
            public void notifyProgress(String subTaskName, double subTaskProgress) {
                System.out.printf("Progress: %s %.2f%%\n", subTaskName, subTaskProgress * 100);
            }

            @Override
            public void notifyProgress(String subTaskName, double subTaskProgress, double overallProgress) {
                System.out.printf("Progress: %s %.2f%% (%.2f%%)\n", subTaskName, subTaskProgress * 100, overallProgress * 100);
            }
        };
    }



    @Before
    public void setUp() throws IOException, URISyntaxException {
        super.setUp();
        destination = Files.createTempDirectory("tmp");
        ConfigurationProvider configurationProvider = new EmptyConfigurationProvider();
        configurationProvider.setValue("products.location", destination.toString());
        configurationProvider.setScriptsFolder(destination);
        configurationProvider.setConfigurationFolder(destination);
        ConfigurationManager.setConfigurationProvider(configurationProvider);

        begin = new QueryParameter<>(LocalDateTime.class, CommonParameterNames.START_DATE);
        begin.setValue(DateUtils.parseDateTime(getValue(CommonParameterNames.START_DATE)));
        end = new QueryParameter<>(LocalDateTime.class, CommonParameterNames.END_DATE);
        end.setValue(DateUtils.parseDateTime(getValue(CommonParameterNames.END_DATE)));
        Polygon2D footprint = Polygon2D.fromWKT(getValue(CommonParameterNames.FOOTPRINT));
        aoi = new QueryParameter<>(Polygon2D.class, CommonParameterNames.FOOTPRINT);
        aoi.setValue(footprint);
        user = getValue("user");
        password = getValue("password");
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.FINEST);
        }
    }

    @Test
    public void testDownloadLandsat8C1_L1() {
        downloadLandsat8("landsat_ot_c2_l1", "L1TP");
    }

    @Test
    public void testDownloadLandsat8C2_L2() {
        downloadLandsat8("landsat_ot_c2_l2", "L1GS");
    }

    @Test
    public void testDownloadLandsat9_C2_L1() {
        downloadLandsat9("landsat_ot_c2_l1");
    }

    @Test
    public void testDownloadLandsat9_C2_L2() {
        downloadLandsat9("landsat_ot_c2_l2");
    }

//    @Test
//    public void testEcoStressSearch() {
//        ecoStressSearch("ecostress_eco1batt");
//    }

    private void downloadLandsat8(String platformName, String productType) {
        try {
            DataSource<?, ?> dataSource = new USGSDataSource();
            dataSource.setCredentials(user, password);
            DataQuery query = dataSource.createQuery("Landsat8");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PLATFORM, platformName);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, productType);
            query.addParameter("maxClouds", 100);
            query.setMaxResults(maxResults);
            query.setPageSize(pageSize);
            List<EOProduct> results = query.execute();
            testAssertions(results);
        } catch (Exception e) {
            Assert.fail("Exception in testing the data source: " + e.getMessage());
        }
    }

    private void downloadLandsat9(String platformName) {
        try {
            DataSource<?, ?> dataSource = new USGSDataSource();
            dataSource.setCredentials(user, password);
            DataQuery query = dataSource.createQuery("Landsat9");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PLATFORM, platformName);
            query.setMaxResults(maxResults);
            query.setPageSize(pageSize);
            List<EOProduct> results = query.execute();
            testAssertions(results);
        } catch (Exception e) {
            Assert.fail("Exception in testing the data source: " + e.getMessage());
        }
    }

    private void ecoStressSearch(String platformName) {
        try {
            DataSource<?, ?> dataSource = new USGSDataSource();
            dataSource.setCredentials(user, password);
            DataQuery query = dataSource.createQuery("ECOSTRESS");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PLATFORM, platformName);
//            query.addParameter(CommonParameterNames.PRODUCT, productName);
            query.setMaxResults(maxResults);
            query.setPageSize(pageSize);
            List<EOProduct> results = query.execute();
            testAssertions(results);
        } catch (Exception e) {
            Assert.fail("Exception in testing the data source: " + e.getMessage());
        }
    }


    private void testAssertions(List<EOProduct> results) {
        Assert.assertNotNull(results);
        Assert.assertNotEquals(0, results.size());
        Assert.assertNotNull(results.get(0));
        Assert.assertNotNull(results.get(0).getId());
        Assert.assertNotNull(results.get(0).getLocation());
    }
}
