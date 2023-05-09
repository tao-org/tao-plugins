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

import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.das.DASDataSource;
import ro.cs.tao.datasource.remote.das.download.DASDownloadStrategy;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.utils.executors.monitoring.DownloadProgressListener;

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
public class DASTest {

    private static final QueryParameter<LocalDateTime> begin;
    private static final QueryParameter<LocalDateTime> end;
    private static final QueryParameter<Polygon2D> aoi;
    private static final int pageSize;
    private static final int maxResults;
    private static final String rowTemplate;
    private static final String user;
    private static final String password;
    private static final DownloadProgressListener listener;

    static {
        begin = new QueryParameter<>(LocalDateTime.class, CommonParameterNames.START_DATE);
        begin.setValue(LocalDateTime.of(2023, 3, 1, 0, 0, 0, 0));
        end = new QueryParameter<>(LocalDateTime.class, CommonParameterNames.END_DATE);
        end.setValue(LocalDateTime.of(2023, 3, 7, 23, 59, 59, 0));
        Polygon2D footprint = Polygon2D.fromWKT("POLYGON((4.67 53.134,5.964 53.108,5.839 51.44,4.608 51.463,4.67 53.134))");
        aoi = new QueryParameter<>(Polygon2D.class, CommonParameterNames.FOOTPRINT);
        aoi.setValue(footprint);
        pageSize = 50;
        maxResults = 50;
        rowTemplate = "ID=%s, NAME=%s, LOCATION=%s";
        //user = "";
        //password = "";
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

    public static void main(String[] args) {
        Sentinel1_Test();
        Sentinel2_Test();
        Sentinel3_Test();
    }

    public static void Sentinel1_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            DataSource<?, ?> dataSource = new DASDataSource();
            dataSource.setCredentials(user, password);

            DataQuery query = dataSource.createQuery("Sentinel1");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, "SLC");
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.printf((rowTemplate) + "%n", r.getId(), r.getName(), r.getLocation());
            });
            final DASDownloadStrategy strategy = (DASDownloadStrategy) dataSource.getProductFetchStrategy("Sentinel1");
            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setProgressListener(listener);
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

    public static void Sentinel2_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            DataSource<?, ?> dataSource = new DASDataSource();
            dataSource.setCredentials(user, password);

            DataQuery query = dataSource.createQuery("Sentinel2");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, "S2MSI1C");
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.printf((rowTemplate) + "%n", r.getId(), r.getName(), r.getLocation());
            });
            final DASDownloadStrategy strategy = (DASDownloadStrategy) dataSource.getProductFetchStrategy("Sentinel2");
            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setProgressListener(listener);
                Path path = strategy.fetch(results.get(0));
                strategy.cancel();
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

    public static void Sentinel3_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            DataSource<?, ?> dataSource = new DASDataSource();
            dataSource.setCredentials(user, password);

            DataQuery query = dataSource.createQuery("Sentinel3");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, "SL_2_LST___");
            query.addParameter("instrument", "SLSTR");
            query.addParameter("processingLevel", "2");
            query.addParameter("productSize", "FRAME");
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.printf((rowTemplate) + "%n", r.getId(), r.getName(), r.getLocation());
            });
            final DASDownloadStrategy strategy = (DASDownloadStrategy) dataSource.getProductFetchStrategy("Sentinel3");
            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setProgressListener(listener);
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

    public static void Landsat8_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            DataSource<?, ?> dataSource = new DASDataSource();
            dataSource.setCredentials(user, password);

            DataQuery query = dataSource.createQuery("Landsat8");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            //query.addParameter(CommonParameterNames.TILE, "198033");
            //query.addParameter(CommonParameterNames.PRODUCT_TYPE, "L1T");
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.printf((rowTemplate) + "%n", r.getId(), r.getName(), r.getLocation());
            });
            final DASDownloadStrategy strategy = (DASDownloadStrategy) dataSource.getProductFetchStrategy("Landsat8");
            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setProgressListener(listener);
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
}
