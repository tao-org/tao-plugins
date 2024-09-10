package ro.cs.tao.tests;

/*
 *
 *  * Copyright (C) 2018 CS ROMANIA
 *  *
 *  * This program is free software; you can redistribute it and/or modify it
 *  * under the terms of the GNU General Public License as published by the Free
 *  * Software Foundation; either version 3 of the License, or (at your option)
 *  * any later version.
 *  * This program is distributed in the hope that it will be useful, but WITHOUT
 *  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 *  * more details.
 *  *
 *  * You should have received a copy of the GNU General Public License along
 *  * with this program; if not, see http://www.gnu.org/licenses/
 *  *
 *
 */

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.das.DASDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.utils.DateUtils;
import ro.cs.tao.utils.StringUtilities;
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
 * @author Cosmin Cara
 */
public class DASTest extends BaseTest {

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
    public void testSentinel1SLC() {
        testSentinel1("SLC");
    }

    @Test
    public void testSentinel1GRDCOG() {
        testSentinel1("GRD-COG");
    }

    @Test
    public void testSentinel1OrbitFiles() {
        testSentinel1Orbit("AUX_POEORB");
    }

    @Test
    public void testS2L1C() {
        testSentinel2("L1C");
    }

    @Test
    public void testS2L2A() {
        testSentinel2("L2A");
    }

    @Test
    public void testS3LST() {
        testSentinel3("SLSTR", "SL_2_LST___", "FRAME");
    }

    @Test
    public void testS5PL2CO() {
        testSentinel5P("L2__CO____", "NRTI");
    }

    @Test
    public void testS5PL2NO2() {
        testSentinel5P("L2__NO2___", "NRTI");
    }

    private void testSentinel1(String productType) {
        try {
            DataSource<?, ?> dataSource = new DASDataSource();
            dataSource.setCredentials(user, password);
            DataQuery query = dataSource.createQuery("Sentinel1");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, productType);
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            testAssertions(results);
            /*results.forEach(r -> {
                System.out.printf((rowTemplate) + "%n", r.getId(), r.getName(), r.getLocation(), r.getQuicklookLocation());
            });*/
            /*final DASDownloadStrategy strategy = (DASDownloadStrategy) dataSource.getProductFetchStrategy("Sentinel1");
            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setDestination(destination);
                strategy.setProgressListener(listener);
                Path path = strategy.fetch(results.get(0));
                if (path != null) {
                    System.out.println("Product downloaded at " + path.toString());
                } else {
                    System.out.println("Product not downloaded");
                }
            }*/
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void testSentinel1Orbit(String productType) {
        try {
            DataSource<?, ?> dataSource = new DASDataSource();
            dataSource.setCredentials(user, password);
            DataQuery query = dataSource.createQuery("Sentinel1-orbit-files");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, productType);
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            testAssertions(results);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void testSentinel2(String productType) {
        try {
            DataSource<?, ?> dataSource = new DASDataSource();
            dataSource.setCredentials(user, password);

            DataQuery query = dataSource.createQuery("Sentinel2");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, productType);
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            testAssertions(results);
            /*final DASDownloadStrategy strategy = (DASDownloadStrategy) dataSource.getProductFetchStrategy("Sentinel2");
            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setDestination(destination);
                strategy.setProgressListener(listener);
                Path path = strategy.fetch(results.get(0));
                strategy.cancel();
                if (path != null) {
                    System.out.println("Product downloaded at " + path.toString());
                } else {
                    System.out.println("Product not downloaded");
                }
            }*/
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void testSentinel3(String instrument, String productType, String productSize) {
        try {
            DataSource<?, ?> dataSource = new DASDataSource();
            dataSource.setCredentials(user, password);

            DataQuery query = dataSource.createQuery("Sentinel3");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, productType);
            query.addParameter("instrument", instrument);
            query.addParameter("processingLevel", "2");
            if (!StringUtilities.isNullOrEmpty(productSize)) {
                query.addParameter("productSize", productSize);
            }
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            testAssertions(results);
            /*final DASDownloadStrategy strategy = (DASDownloadStrategy) dataSource.getProductFetchStrategy("Sentinel3");
            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setDestination(destination);
                strategy.setProgressListener(listener);
                Path path = strategy.fetch(results.get(0));
                if (path != null) {
                    System.out.println("Product downloaded at " + path.toString());
                } else {
                    System.out.println("Product not downloaded");
                }
            }*/
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void testSentinel5P(String productType, String processingMode) {
        try {
            DataSource<?, ?> dataSource = new DASDataSource();
            dataSource.setCredentials(user, password);

            DataQuery query = dataSource.createQuery("Sentinel5P");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, productType);
            query.addParameter("timeliness", processingMode);
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.printf((rowTemplate) + "%n", r.getId(), r.getName(), r.getLocation(), r.getQuicklookLocation());
            });
            testAssertions(results);
            /*final DASDownloadStrategy strategy = (DASDownloadStrategy) dataSource.getProductFetchStrategy("Sentinel1");
            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setDestination(destination);
                strategy.setProgressListener(listener);
                Path path = strategy.fetch(results.get(0));
                if (path != null) {
                    System.out.println("Product downloaded at " + path.toString());
                } else {
                    System.out.println("Product not downloaded");
                }
            }*/
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void testLandsat8(String productType) {
        try {
            DataSource<?, ?> dataSource = new DASDataSource();
            dataSource.setCredentials(user, password);

            DataQuery query = dataSource.createQuery("Landsat8");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, productType);
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.printf((rowTemplate) + "%n", r.getId(), r.getName(), r.getLocation());
            });
            testAssertions(results);
            /*final DASDownloadStrategy strategy = (DASDownloadStrategy) dataSource.getProductFetchStrategy("Landsat8");
            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setDestination(destination);
                strategy.setProgressListener(listener);
                Path path = strategy.fetch(results.get(0));
                if (path != null) {
                    System.out.println("Product downloaded at " + path.toString());
                } else {
                    System.out.println("Product not downloaded");
                }
            }*/
        } catch (Exception e) {
            e.printStackTrace();
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
