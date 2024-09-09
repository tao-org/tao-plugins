package ro.cs.tao.tests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ro.cs.tao.datasource.CollectionDescription;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.earthdata.EarthDataSource;
import ro.cs.tao.datasource.remote.earthdata.download.EarthDataDownloadStrategy;
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
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Efrem-Hagiu Stefan
 */
public class NasaDataSourceTest extends BaseTest {

    private static final int pageSize;
    private static final int maxResults;
    private static final String rowTemplate;
    private static final DownloadProgressListener listener;

    private QueryParameter<LocalDateTime> begin;
    private QueryParameter<LocalDateTime> end;
    private QueryParameter<Polygon2D> aoi;
    private static final String token = "eyJ0eXAiOiJKV1QiLCJvcmlnaW4iOiJFYXJ0aGRhdGEgTG9naW4iLCJzaWciOiJlZGxqd3RwdWJrZXlfb3BzIiwiYWxnIjoiUlMyNTYifQ.eyJ0eXBlIjoiVXNlciIsInVpZCI6InZuZXRvaXUiLCJleHAiOjE3MjE1NTE4MzksImlhdCI6MTcxNjM2NzgzOSwiaXNzIjoiRWFydGhkYXRhIExvZ2luIn0.QgZSZf4NsqNanuHaJLDE3LGW9ShgFAdgRHj2vNL3-ahFwnUsTc7KY5bhj0BRtEHTgEdRM_Ak029uqhVOBtEoo5yIFa9qa5dYCihICio7mskE7ddBH7qHWOUz0Iq-yJQXBTWif1uOqUPeFsNxscsics-VQyvrJ4_fZhKr-Q1UGcJi-EzQrl--S-K-ipDFm6abhRYCT8tiTwOMz0a0EQOWNnbgf7-SBKAlGDHo4rzHlhxvY8v6JbGUQ05r0YwKdfWOHkanNMi0eqS1p1OxEI2pMGlRIL0RQ3DAuojJfDRabIvcOkOZ0G3IXf8BZDoCW-4hPTQ52arGX_9c5thCqEIE9g";
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
    public void test_MODIS_CHLM() {
        testEarthData("MODIS-CHLM" );
    }

    @Test
    public void test_MODIS_CHLB() {
        testEarthData("MODIS-CHLB" );
    }

    @Test
    public void test_ICESAT2() {
        testEarthData("IceSAT-2-ATL08V3" );
    }


    private void testEarthData(String sensor) {
        try {
            DataSource<?, ?> dataSource = new EarthDataSource();
            dataSource.setCredentials(user, password);
            DataQuery query = dataSource.createQuery(sensor);
            setTokenIfNeeded(dataSource, sensor);
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.setMaxResults(maxResults);
            query.setPageSize(pageSize);
            List<EOProduct> results = query.execute();
            testAssertions(results);

            final EarthDataDownloadStrategy strategy = (EarthDataDownloadStrategy) dataSource.getProductFetchStrategy(sensor);

            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setDestination("D:\\eodata");
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

    private static void setTokenIfNeeded(DataSource dataSource, String sensor) {
        final Map<String, CollectionDescription> sensorTypes = dataSource.getSensorTypes();
        if (sensorTypes.get(sensor).isTokenNeeded()) {
            dataSource.setBearerToken(token);
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
