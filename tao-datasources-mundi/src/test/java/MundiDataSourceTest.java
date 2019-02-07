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
import ro.cs.tao.datasource.remote.mundi.landsat8.Landsat8DataSource;
import ro.cs.tao.datasource.remote.mundi.sentinel1.Sentinel1DataSource;
import ro.cs.tao.datasource.remote.mundi.sentinel2.Sentinel2DataSource;
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
public class MundiDataSourceTest {

    private static final QueryParameter<Date> begin;
    private static final QueryParameter<Date> end;
    private static final QueryParameter<Polygon2D> aoi;
    private static final int pageSize;
    private static final int maxResults;
    private static final String rowTemplate;

    static {
        begin = new QueryParameter<>(Date.class, CommonParameterNames.START_DATE);
        begin.setValue(Date.from(LocalDateTime.of(2018, 9, 1, 0, 0, 0, 0)
                                         .atZone(ZoneId.systemDefault())
                                         .toInstant()));
        end = new QueryParameter<>(Date.class, CommonParameterNames.END_DATE);
        end.setValue(Date.from(LocalDateTime.of(2018, 9, 30, 0, 0, 0, 0)
                                       .atZone(ZoneId.systemDefault())
                                       .toInstant()));
        Polygon2D footprint = Polygon2D.fromWKT("POLYGON((22.8042573604346 43.8379609098684,24.83885442747927 43.8379609098684," +
                                                        "24.83885442747927 44.795645304033826,22.8042573604346 44.795645304033826," +
                                                        "22.8042573604346 43.8379609098684))");
        aoi = new QueryParameter<>(Polygon2D.class, CommonParameterNames.FOOTPRINT);
        aoi.setValue(footprint);
        pageSize = 10;
        maxResults = 20;
        rowTemplate = "ID=%s, NAME=%s, LOCATION=%s";
    }

    public static void main(String[] args) {
        /*Sentinel2_Count_Test();
        Sentinel2_Test();
        Sentinel1_Count_Test();*/
        Sentinel1_Test();
        /*Landsat8_Count_Test();
        Landsat8_Test();*/
    }

    public static void Sentinel2_Count_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel2DataSource.class);
            DataSource dataSource = new Sentinel2DataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery(sensors[0]);
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            System.out.println(String.format("Sentinel2 query returned %s", query.getCount()));
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
            DataSource dataSource = new Sentinel2DataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery(sensors[0]);
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println(String.format(rowTemplate, r.getId(), r.getName(), r.getLocation()));
            });
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
            DataSource dataSource = new Sentinel1DataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery(sensors[0]);
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            System.out.println(String.format("Sentinel1 query returned %s", query.getCount()));
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
            DataSource dataSource = new Sentinel1DataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery(sensors[0]);
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println(String.format(rowTemplate, r.getId(), r.getName(), r.getLocation()));
            });
            Path path = dataSource.getProductFetchStrategy(sensors[0]).fetch(results.get(0));
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
            DataSource dataSource = new Landsat8DataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery(sensors[0]);
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            System.out.println(String.format("Landsat8 query returned %s", query.getCount()));
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
            DataSource dataSource = new Landsat8DataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery(sensors[0]);
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println(String.format(rowTemplate, r.getId(), r.getName(), r.getLocation()));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ServiceRegistry<DataSource> getDatasourceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }

}
