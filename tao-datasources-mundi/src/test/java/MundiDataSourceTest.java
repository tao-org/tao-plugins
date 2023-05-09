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
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.mundi.MundiDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;
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
public class MundiDataSourceTest {

    private static final QueryParameter<LocalDateTime> begin;
    private static final QueryParameter<LocalDateTime> end;
    private static final QueryParameter<Polygon2D> aoi;
    private static final int pageSize;
    private static final int maxResults;
    private static final String rowTemplate;

    static {
        begin = new QueryParameter<>(LocalDateTime.class, CommonParameterNames.START_DATE);
        begin.setValue(LocalDateTime.of(2018, 9, 1, 0, 0, 0, 0));
        end = new QueryParameter<>(LocalDateTime.class, CommonParameterNames.END_DATE);
        end.setValue(LocalDateTime.of(2018, 9, 30, 0, 0, 0, 0));
        Polygon2D footprint = Polygon2D.fromWKT("POLYGON((22.8042573604346 43.8379609098684,24.83885442747927 43.8379609098684," +
                                                        "24.83885442747927 44.795645304033826,22.8042573604346 44.795645304033826," +
                                                        "22.8042573604346 43.8379609098684))");
        aoi = new QueryParameter<>(Polygon2D.class, CommonParameterNames.FOOTPRINT);
        aoi.setValue(footprint);
        pageSize = 10;
        maxResults = 1;
        rowTemplate = "ID=%s, NAME=%s, LOCATION=%s";
    }

    public static void main(String[] args) {
        Sentinel2_Count_Test();
        Sentinel2_Test();
        Sentinel1_Count_Test();
        Sentinel1_Test();
        Landsat8_Count_Test();
        Landsat8_Test();
    }

    public static void Sentinel2_Count_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel2DataSource.class);
            DataSource<?, ?> dataSource = new MundiDataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("Sentinel2");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.setPageSize(pageSize);
            query.setMaxResults(maxResults);
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
            DataSource<?, ?> dataSource = new MundiDataSource();

            DataQuery query = dataSource.createQuery("Sentinel2");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.setPageSize(pageSize);
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.printf((rowTemplate) + "%n", r.getId(), r.getName(), r.getLocation());
            });
            DownloadStrategy<?> downloadStrategy = (DownloadStrategy<?>) dataSource.getProductFetchStrategy("Sentinel2");
            downloadStrategy.setFetchMode(FetchMode.OVERWRITE);
            downloadStrategy.setProgressListener(new DownloadProgressListener() {});
            Path path = downloadStrategy.fetch(results.get(0));
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
            DataSource<?, ?> dataSource = new MundiDataSource();

            DataQuery query = dataSource.createQuery("Sentinel1");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.setPageSize(pageSize);
            query.setMaxResults(maxResults);
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
            DataSource<?, ?> dataSource = new MundiDataSource();
            dataSource.setCredentials("", "");
            DataQuery query = dataSource.createQuery("Sentinel1");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.setPageSize(pageSize);
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.printf((rowTemplate) + "%n", r.getId(), r.getName(), r.getLocation());
            });
            DownloadStrategy<?> downloadStrategy = (DownloadStrategy<?>) dataSource.getProductFetchStrategy("Sentinel1");
            downloadStrategy.setFetchMode(FetchMode.OVERWRITE);
            downloadStrategy.setProgressListener(new DownloadProgressListener() {});
            Path path = downloadStrategy.fetch(results.get(0));
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
            DataSource<?, ?> dataSource = new MundiDataSource();

            DataQuery query = dataSource.createQuery("Landsat8");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.setPageSize(pageSize);
            query.setMaxResults(maxResults);
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
            DataSource<?, ?> dataSource = new MundiDataSource();

            DataQuery query = dataSource.createQuery("Landsat8");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.setPageSize(pageSize);
            query.setMaxResults(maxResults);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.printf((rowTemplate) + "%n", r.getId(), r.getName(), r.getLocation());
            });
            DownloadStrategy<?> downloadStrategy= (DownloadStrategy<?>) dataSource.getProductFetchStrategy("Landsat8");
            downloadStrategy.setFetchMode(FetchMode.OVERWRITE);
            Path path = downloadStrategy.fetch(results.get(0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ServiceRegistry<DataSource> getDatasourceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }

}
