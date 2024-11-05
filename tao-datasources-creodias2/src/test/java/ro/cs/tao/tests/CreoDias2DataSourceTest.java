package ro.cs.tao.tests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.odata.CreoDiasODataSource;
import ro.cs.tao.datasource.tests.DataSourceTest;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.utils.DateUtils;
import ro.cs.tao.utils.executors.monitoring.DownloadProgressListener;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Efrem-Hagiu Stefan
 */
public class CreoDias2DataSourceTest extends DataSourceTest<CreoDiasODataSource> {

    private static final String rowTemplate;
    private static final DownloadProgressListener listener;

    private Path destination;

    static {
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

    public CreoDias2DataSourceTest() {
        super(CreoDiasODataSource.class);
    }

    @Test
    public void testCreoDiasNew_Sentinel1() {
        testSentinel("Sentinel1", "SLC");
    }

    @Test
    public void testCreoDiasNew_Sentinel2() {
        testSentinel("Sentinel2", "S2MSI1C");
    }

    @Test
    public void testCreoDiasNew_Sentinel3() {
        //testSentinel3("Sentinel3", "SL_2_LST___");
    }

    @Test
    public void testCreoDiasNew_Sentinel5P() {
        //testSentinel("Sentinel5P", "L2__CO____");
        //testSentinel("Sentinel5P", "L2__HCHO__");
        //testSensor("Sentinel5P", "L2__CH4___", false, new QueryParameter(String.class, "processingMode", "OFFL"));
        testSentinel("Sentinel5P", "L2__O3____");
        //testSentinel("Sentinel5P", "L2__NO2___");
        //testSentinel("Sentinel5P", "L2__SO2___");

    }

    @Test
    public void testCreoDiasNew_Landsat8() {
        //testLandsat("Landsat8", "L1TP");
    }

    private void testSentinel(String sensor, String productType) {
        testSensor(sensor, productType, false);
    }

    /*private void testSentinel3(String sensor, String productType) {
        try {
            DataSource<?, ?> dataSource = new CreoDiasODataSource();
            dataSource.setCredentials(user, password);
            DataQuery query = dataSource.createQuery(sensor);
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, productType);
            query.setMaxResults(maxResults);
            query.setPageSize(pageSize);
            query.addParameter("instrument", "SLSTR");
            query.addParameter("processingLevel", "2");
            query.addParameter("productSize", "FRAME");
            List<EOProduct> results = query.execute();
            testAssertions(results);
        } catch (Exception e) {
            Assert.fail("Exception in testing the data source: " + e.getMessage());
        }
    }

    private void testLandsat(String sensor, String productType) {
        try {
            DataSource<?, ?> dataSource = new CreoDiasODataSource();
            dataSource.setCredentials(user, password);
            DataQuery query = dataSource.createQuery(sensor);
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, productType);
            query.setMaxResults(maxResults);
            query.setPageSize(pageSize);
            List<EOProduct> results = query.execute();
            testAssertions(results);
        } catch (Exception e) {
            Assert.fail("Exception in testing the data source: " + e.getMessage());
        }
    }*/

    @Override
    protected void testAssertions(List<EOProduct> results) {
        try {
            Files.writeString(Paths.get("E:\\" + results.get(0).getProductType() + ".txt"),
                              results.stream().map(r -> r.getAttributeValue("S3Path")).collect(Collectors.joining("\n")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
