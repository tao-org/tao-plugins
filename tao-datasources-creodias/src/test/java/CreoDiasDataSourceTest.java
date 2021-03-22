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

import ro.cs.tao.ProgressListener;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.creodias.CreoDiasDataSource;
import ro.cs.tao.datasource.remote.creodias.download.CreoDIASDownloadStrategy;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Cosmin Cara
 */
public class CreoDiasDataSourceTest {

    private static final QueryParameter<Date> begin;
    private static final QueryParameter<Date> end;
    private static final QueryParameter<Polygon2D> aoi;
    private static final int pageSize;
    private static final int maxResults;
    private static final String rowTemplate;

    static {
        begin = new QueryParameter<>(Date.class, CommonParameterNames.START_DATE);
        begin.setValue(Date.from(LocalDateTime.of(2019, 5, 21, 0, 0, 0, 0)
                                         .atZone(ZoneId.systemDefault())
                                         .toInstant()));
        end = new QueryParameter<>(Date.class, CommonParameterNames.END_DATE);
        end.setValue(Date.from(LocalDateTime.of(2019, 10, 1, 23, 59, 59, 0)
                                       .atZone(ZoneId.systemDefault())
                                       .toInstant()));
        Polygon2D footprint = Polygon2D.fromWKT("POLYGON((4.67 53.134,5.964 53.108,5.839 51.44,4.608 51.463,4.67 53.134))");
        aoi = new QueryParameter<>(Polygon2D.class, CommonParameterNames.FOOTPRINT);
        aoi.setValue(footprint);
        pageSize = 50;
        maxResults = 50;
        rowTemplate = "ID=%s, NAME=%s, LOCATION=%s";
    }

    public static void main(String[] args) {
        //Sentinel2_Count_Test();
        Sentinel2_Test();
        //Sentinel1_Count_Test();
        //Sentinel1_Test();
        //Landsat8_Count_Test();
        //Landsat8_Test();
    }

    public static void Sentinel2_Count_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel2DataSource.class);
            DataSource<?, ?> dataSource = new CreoDiasDataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("Sentinel2");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            System.out.printf("Sentinel2 query returned %s%n", query.getCount());
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
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel2DataSource.class);
            DataSource<?, ?> dataSource = new CreoDiasDataSource();
            dataSource.setCredentials("kraftek@gmail.com", "cei7pitici@creodias.");
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("Sentinel2");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter("status", "all");
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, "L1C");
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.printf((rowTemplate) + "%n", r.getId(), r.getName(), r.getLocation());
            });
            final CreoDIASDownloadStrategy strategy = (CreoDIASDownloadStrategy) dataSource.getProductFetchStrategy("Sentinel2");
            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setProgressListener(new ProgressListener() {
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
                    public void notifyProgress(String subTaskName, double subTaskProgress) {
                        System.out.printf("Progress: %s %.2f%%\n", subTaskName, subTaskProgress * 100);
                    }

                    @Override
                    public void notifyProgress(String subTaskName, double subTaskProgress, double overallProgress) {
                        System.out.printf("Progress: %s %.2f%% (%.2f%%)\n", subTaskName, subTaskProgress * 100, overallProgress * 100);
                    }
                });
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

    public static void Sentinel1_Count_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel2DataSource.class);
            DataSource<?, ?> dataSource = new CreoDiasDataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("Sentinel1");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            System.out.printf("Sentinel1 query returned %s%n", query.getCount());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void Sentinel1_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel1DataSource.class.getName());
            DataSource<?, ?> dataSource = new CreoDiasDataSource();
            dataSource.setCredentials("cosmin.cara@c-s.ro", "cei7pitici.");
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("Sentinel1");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, "SLC");
            query.addParameter("status", "all");
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.printf((rowTemplate) + "%n", r.getId(), r.getName(), r.getLocation());
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void Landsat8_Count_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel2DataSource.class);
            DataSource<?, ?> dataSource = new CreoDiasDataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("Landsat8");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            System.out.printf("Landsat8 query returned %s%n", query.getCount());
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
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel1DataSource.class.getName());
            DataSource<?, ?> dataSource = new CreoDiasDataSource();
            String[] sensors = dataSource.getSupportedSensors();

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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ServiceRegistry<DataSource> getDatasourceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }

}
