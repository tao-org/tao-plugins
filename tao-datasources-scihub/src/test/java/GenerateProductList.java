import org.locationtech.jts.geom.Geometry;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.scihub.SciHubDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.serialization.GeometryAdapter;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;
import ro.cs.tao.utils.Triple;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GenerateProductList {

    public static void main(String[] args) throws Exception {
        /*final int year = 2018;
        Path destination = Paths.get(String.format("C:\\ssd\\s1_products_%d.txt", year));
        Files.deleteIfExists(destination);
        Files.createFile(destination);
        final List<String> tiles = new ArrayList<String>() {{
           add("17MNU"); add("17MPU"); add("17MQU"); add("17MRU");
        }};
        Polygon2D footprint;
        Set<String> results = new TreeSet<>();
        for (String tile : tiles) {
            System.out.println("Querying for tile " + tile);
            footprint = Polygon2D.fromPath2D(Sentinel2TileExtent.getInstance().getTileExtent(tile));
            results.addAll(doSciHubQuery(year, footprint.toWKT(), "Sentinel-1", "SLC"));
            Files.write(destination, (String.join("\n", results)).getBytes(), StandardOpenOption.APPEND);
        }*/

        final Polygon2D footprint = Polygon2D.fromWKT("POLYGON((22.818184293951 68.3254798849925,23.183510518201 68.3209647665854,22.9980588647949 66.487618048158,24.4225830865676 66.5007727056205,24.4662724591616 65.8695796662729,22.9943856156235 65.8887818153708,22.9278306343594 65.2643298478309,22.8732061660273 64.7212260156388,23.0941872551635 64.7183591844833,23.0848510709629 64.5969261225862,23.0711893042866 64.4170008656673,23.0711889706488 64.416996436051,23.0560780386926 64.2145926951465,23.0503483722181 64.1372242487091,20.7441319284759 64.122818588628,20.7769975540749 63.2569027306149,18.8080322521958 63.2234154343098,19.0409118078332 62.6963967268619,19.2410925853785 59.141270614987,19.3542473626231 56.9115327851718,18.0245491943313 56.8958842586146,17.1747265142224 56.9138053363136,16.4820696898141 56.9252945186477,16.4658386014265 56.6011807375053,16.4636261534182 56.5439946476007,16.4558620027284 56.3421294578728,16.4558617001012 56.3421215151656,16.4481965838829 56.1390364830921,16.4426895130589 55.9918444128693,14.8736733674247 56.0037850884389,14.8840731255522 55.0485787258359,12.5645720322653 55.0231296474966,12.6855698931147 56.9279384456749,11.2838300279325 56.9707931092627,11.3010713317383 59.0764340620351,12.8609195945216 59.0575719154989,12.9734894926756 60.1395081899876,13.0207270246208 60.6917733224376,13.7569080496495 60.6993611723549,13.7634886425233 60.9573151026684,13.7790661639985 61.5553325464409,13.5123101381097 61.5627327694032,13.4711124051306 63.8410205101361,14.7485927735277 63.8488635244128,15.3925304785524 63.8459326135409,15.4291231963437 65.6426816642402,18.6971670699958 65.5983944850448,19.7630200831301 65.5651600608564,19.9512199098822 66.5372954201197,20.8468369433518 66.5403973978949,21.487084076893 66.5370027866439,21.4791585460744 68.3345569568174,21.8447311244143 68.3332430853025,22.3315406319436 68.3300718188142,22.818184293951 68.3254798849925))");
        Path destination = Paths.get("D:\\ssd\\s1_products.txt");
        Files.deleteIfExists(destination);
        Files.createFile(destination);
        /*final List<String> tiles = new ArrayList<String>() {{
            add("17MNU"); add("17MPU"); add("17MQU"); add("17MRU");
        }};
        Polygon2D footprint;*/
        List<String> results = findS1Intersections(LocalDate.of(2021,5,1), LocalDate.of(2021,12,31),
                                            footprint.toWKT());
        Files.write(destination, (String.join("\n", results)).getBytes(), StandardOpenOption.APPEND);
        System.exit(0);
    }

    private static Connection openConnection() throws Exception {
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection("jdbc:postgresql://localhost:5432/sen4cap", "admin", "admin");
    }

    private static List<String> doSciHubQuery(int year, String footprint, String satellite, String productType) {
        List<String> products = new ArrayList<>();
        try {
            final DataSource<?, ?> dataSource = getDatasourceRegistry().getService(SciHubDataSource.class);
            dataSource.setCredentials("kraftek", "cei7samurai");
            final DataQuery query = dataSource.createQuery(satellite.replace("-", ""));
            query.addParameter(CommonParameterNames.PLATFORM, satellite);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, productType);
            final QueryParameter<LocalDateTime> begin = query.createParameter(CommonParameterNames.START_DATE, LocalDateTime.class);
            begin.setMinValue(LocalDateTime.of(year, 1, 1, 0, 0, 0, 0));
            begin.setMaxValue(LocalDateTime.of(year, 12, 31, 23, 59, 59, 0));
            query.addParameter(begin);
            final Polygon2D aoi = Polygon2D.fromWKT(footprint);
            query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
            query.setPageSize(100);
            query.setMaxResults(300);
            final List<EOProduct> results = query.execute();
            for (EOProduct result : results) {
                products.add("('" + result.getAcquisitionDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "'," + result.getAttributeValue("orbit") + ", '" + result.getGeometry() + "')");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return products;
    }

    private static List<String> findS1Intersections(LocalDate from, LocalDate to, String footprint) {
        Map<String, List<Triple<LocalDateTime, String, Geometry>>> orbits = new TreeMap<>();
        final List<String> lines = new ArrayList<>();
        try {
            final DataSource<?, ?> dataSource = getDatasourceRegistry().getService(SciHubDataSource.class);
            dataSource.setCredentials("kraftek", "cei7samurai");
            final DataQuery query = dataSource.createQuery("Sentinel-1".replace("-", ""));
            query.addParameter(CommonParameterNames.PLATFORM, "Sentinel-1");
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, "SLC");
            final QueryParameter<LocalDateTime> begin = query.createParameter(CommonParameterNames.START_DATE, LocalDateTime.class);
            begin.setMinValue(from.atStartOfDay());
            begin.setMaxValue(to.plusDays(1).atStartOfDay().minusSeconds(1));
            query.addParameter(begin);
            final Polygon2D aoi = Polygon2D.fromWKT(footprint);
            query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
            query.setPageSize(100);
            query.setMaxResults(5000);
            final List<EOProduct> results = query.execute();
            final GeometryAdapter adapter = new GeometryAdapter();
            for (EOProduct result : results) {
                final String orbit = result.getAttributeValue("relativeorbitnumber");
                if (!orbits.containsKey(orbit)) {
                    orbits.put(orbit, new ArrayList<>());
                }
                final Geometry geometry = adapter.marshal(result.getGeometry());
                if (geometry != null && result.getAcquisitionDate() != null) {
                    orbits.get(orbit).add(new Triple<>(result.getAcquisitionDate(), result.getName(), geometry));
                } else {
                    System.err.println("Empty field on " + result.getName());
                }
            }
            String message;
            orbits.values().forEach(l -> {
                l.sort(Comparator.comparing(Triple::getKeyOne));
            });
            for (String orbit : orbits.keySet()) {
                lines.add("Orbit " + orbit);
                final List<Triple<LocalDateTime, String, Geometry>> list = orbits.get(orbit);
                for (int i = 0; i < list.size() - 1; i++) {
                    final Triple<LocalDateTime, String, Geometry> previous = list.get(i);
                    Triple<LocalDateTime, String, Geometry> current = null;
                    for (int j = i + 1; j < list.size(); j++) {
                        current = list.get(j);
                        final long offset = Duration.between(previous.getKeyOne(), current.getKeyOne()).toDays();
                        if (offset > 10) break;
                        if (offset >= 5 && offset <= 7) {
                            final Geometry intersection = current.getKeyThree().intersection(previous.getKeyThree());
                            if (intersection != null && intersection.getArea() > 0) {
                                message = current.getKeyTwo() + "\t" + previous.getKeyTwo() + "\t" + String.format("%.2f%%", (intersection.getArea() / current.getKeyThree().getArea() * 100));
                                System.out.println(message);
                                lines.add(message);
                            } else {
                                current = null;
                            }
                        } else {
                            current = null;
                        }
                    }
                    if (current == null) {
                        message = previous.getKeyTwo() + "\tNo intersection";
                        System.out.println(message);
                        lines.add(message);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return lines;
    }

    private static ServiceRegistry<DataSource> getDatasourceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }
}
