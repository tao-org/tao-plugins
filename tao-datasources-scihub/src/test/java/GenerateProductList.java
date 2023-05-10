import org.locationtech.jts.geom.Geometry;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.scihub.SciHubDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.products.sentinels.Sentinel2TileExtent;
import ro.cs.tao.serialization.GeometryAdapter;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;
import ro.cs.tao.utils.Triple;
import ro.cs.tao.utils.async.Parallel;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GenerateProductList {

    public static void main(String[] args) throws Exception {
        final String wkt = "MULTIPOLYGON(((36.9645132292764 -5.92801938295989,36.941580107 -5.97565272599996,36.8688495120001 -6.00986716099993,36.817048572 -6.05664310099996,36.8502045490001 -6.35878144999998,36.7261051200001 -6.46067709799996,36.7225411150001 -6.49399729499993,36.7455293040001 -6.50041183999997,36.749449864 -6.55921216999997,36.7286001420001 -6.57275409999994,36.6640909010001 -6.70728224999993,36.63165802 -6.73988976399994,36.645557899 -6.76911172799993,36.6373749800001 -6.92438376299998,36.6487215390001 -7.02680148699994,36.5685193420001 -7.05319972399997,36.5470550220001 -7.08755205099993,36.504343636 -7.19194711599994,36.534031806 -7.33369336799996,36.482237094 -7.37936257599995,36.46927341 -7.35116868999995,36.395787028 -7.31474648399995,36.3308106060001 -7.33654995899997,36.302171576 -7.24478046999997,36.279287997 -7.23290569199997,36.249922213 -7.17579452599995,36.2012919700001 -7.17069833799997,36.191700774 -7.14784992799997,36.168989921 -7.15520817399994,36.144996586 -7.13460531999993,36.074062483 -7.13374333199994,36.050829132 -7.11974422799994,35.9800080100001 -7.13691458499994,35.8507643040001 -7.08550573299993,35.8246793750001 -7.09806203899996,35.7363846330001 -7.08502052199998,35.580835967 -7.00476910499998,35.5416053540001 -7.01499491999994,35.4952613190001 -6.95004990599995,35.467576337 -6.95165138699997,35.4438916450001 -6.93257510799998,35.3042469010001 -6.93671617999996,35.2416871790001 -6.99663413999997,35.169542817 -6.98144769699996,35.083580435 -6.92605363099995,35.0732593270001 -6.76942208499997,35.053751177 -6.71674368899994,35.0951645310001 -6.74584915899993,35.200950973 -6.72450446199997,35.184696838 -6.65795129099996,35.20545287 -6.56135061799995,35.2291514390001 -6.52296320399995,35.2019489240001 -6.46452462999997,35.2439606410001 -6.43542533699997,35.244275407 -6.38306139599996,35.218778376 -6.34649020099994,35.201646598 -6.25869533799994,35.173072018 -6.24192330399995,35.1646020380001 -6.20871976399997,35.2070161550001 -6.10445055699995,35.2117846520001 -5.96271791599997,35.2790516480001 -5.76169658599997,35.3355842010001 -5.71738581699998,35.3571741120001 -5.67200783199996,35.2535944150001 -5.58636670299995,35.241813117 -5.54172716299996,35.1999131750001 -5.50961191599993,35.187456866 -5.45971708399998,35.13844464 -5.41324658599996,35.1278128110001 -5.34353736099996,35.090817395 -5.26919932299995,35.09368355 -5.17776400099996,35.0661732460001 -5.13388543999997,35.117413211 -5.10782193099993,35.1692720520001 -5.03822855499993,35.1894638320001 -5.04203706099997,35.2812363470001 -4.98033605699993,35.3399535952307 -4.87751834072045,35.2842089100001 -4.80640015299997,35.157456756 -4.75776537999997,35.128504343 -4.63086883199998,35.0737691310001 -4.61846627099993,35.037710557 -4.53828949499996,34.9725850040001 -4.46016142399998,34.875547533 -4.41355330099998,34.8664650860001 -4.36201962999996,34.906008619 -4.27895331499997,34.928032075 -4.26679901999995,34.958864487 -4.23767144399994,34.9707613510001 -4.20808504599995,34.966713203 -4.09239944899997,34.991032841 -3.99075448299993,34.936890282 -3.99467491699994,34.9121167210001 -3.97097373199995,34.891975362 -3.90877307099993,34.983826815 -3.86329248799996,35.0184169 -3.87143187099997,35.1407799890001 -3.80364523599997,35.2928912510001 -3.68968053399993,35.3293467400001 -3.69088750099996,35.386019047 -3.61719835499997,35.540700278 -3.69200319599997,35.624858412 -3.69785301099995,35.5920251480001 -3.77532874199994,35.6797110740001 -3.76646185999994,35.713420533 -3.63152891699997,35.716000077 -3.55691284199997,35.7443215210001 -3.57306200999994,35.7786173200001 -3.56134290699993,35.7666331290001 -3.53860588199996,35.7922124040001 -3.50298523299995,35.795526291 -3.46674331299994,35.839366629 -3.42197587799996,35.8743698920001 -3.42587616999998,35.8910775900001 -3.45949484899995,35.8874875750001 -3.53467094199993,35.8644627990001 -3.59203673699994,35.8796116400001 -3.64041513699993,35.942350334 -3.63716590099995,35.9811971070001 -3.65658706599993,36.149205232 -3.87111635499997,36.282538968 -4.15051443399994,36.29859558 -4.14481729999994,36.299750476 -3.80439299899996,36.491411144 -3.77602953499996,36.5491792190001 -3.71202333399998,36.6109517150001 -3.68508253399995,36.6186321110001 -3.64900074999997,36.6668393900001 -3.61390828999993,36.7025110640001 -3.61148803699996,36.6908039520001 -3.59295239799997,36.7159383580001 -3.55337050399993,36.769436915 -3.60080398499997,36.801492533 -3.65479755099994,36.8335480970001 -3.64984990099993,36.861838693 -3.58585348499997,36.8934415480001 -3.59801960199997,36.9202993440001 -3.57069666299998,36.966194075 -3.57281210999997,37.020950912 -3.50260431399994,37.11046414 -3.47660578999995,37.1390187900001 -3.48741681799993,37.1646141480001 -3.45473281599993,37.2119076760001 -3.45417105999996,37.229252182 -3.48906673499994,37.300835874 -3.50218137199994,37.3127621200001 -3.57590639499995,37.346011801 -3.63525232099994,37.4091019260001 -3.68472666499997,37.465020538 -3.80900423699995,37.4501621520001 -3.88548666899993,37.468277738 -3.91567632199997,37.4632206630001 -3.97989887299997,37.511882595 -4.08260577699997,37.5155812240001 -4.13380365199993,37.4964586190001 -4.17229298999996,37.513688626 -4.28033691899998,37.4944950460001 -4.30851025299995,37.507678571 -4.33991443599996,37.4917808490001 -4.35529346399994,37.4992381260001 -4.40105364899995,37.5536875560001 -4.46950310699998,37.6304031600001 -4.52302244899994,37.67400665 -4.52560338399996,37.757392807 -4.60889240599994,37.762960063 -4.59775910399998,37.791763561 -4.60741568799995,37.8443221150001 -4.57230176699994,37.874364202 -4.58821237499996,37.933832029 -4.57631535199994,37.9350276320001 -4.59951134399995,37.99940142 -4.61364133799998,37.9867762200387 -4.71730093613314,38.0022596360001 -4.60103291799993,38.0382132140001 -4.59578267099994,38.0684504020001 -4.61586914499998,38.109730927 -4.59835348999997,38.1208933890001 -4.53290958799994,38.4048106750001 -4.11058097299997,39.0363340100001 -4.55167173399997,39.672718645847 -4.78986247067856,38.8760953 -5.74504409999997,38.8240693000001 -5.84274459999995,38.8196609 -5.93537999999995,38.791708999 -5.96895093399996,38.7047423540001 -5.94766422299995,38.6957881750001 -5.96370507499995,38.655924338 -5.95652940899993,38.6301121880001 -5.98835894599995,38.60496276 -5.97933093699993,38.562590195 -6.01789536599995,38.544724982 -5.99894759499995,38.4834485590001 -6.00166942799996,38.4651443380001 -6.02191940399996,38.415553779 -6.00823850999996,38.4040569050001 -5.98040695099996,38.3921178630001 -5.99570747299992,38.3798310710001 -5.98365252299993,38.342507061 -5.98933227299995,38.3194403370001 -5.93670777499995,38.232675337 -5.92432835799997,38.219893943 -5.89623811899997,38.1989863760001 -5.89623812099995,38.2005236900001 -5.87656042399993,38.168854869 -5.87133354499997,38.1646932960001 -5.85730599499993,38.1357424760001 -5.86781027999996,38.0739977920001 -5.84987617199994,38.0583694760001 -5.86550448999998,38.0099473020001 -5.86114907399997,38.00251745 -5.87575257999993,37.9387231440001 -5.84347117299995,37.8660898970001 -5.89407105599997,37.819973541 -5.88621421499994,37.775966259 -5.90073502299992,37.755267173 -5.89074804199993,37.756653344 -5.87603613799996,37.7203499730001 -5.86578326799997,37.6896449860001 -5.91546332999997,37.6358958270001 -5.95240527199996,37.6091030740001 -5.85836350399995,37.565014622 -5.84391447599995,37.5413768880001 -5.81367442799996,37.488671878 -5.82688501799993,37.448825664 -5.80232344299998,37.396765701 -5.86188071699996,37.394083847 -5.93725249799996,37.3668989290001 -5.97526439799998,37.2337223160001 -6.00226026599995,37.147009731 -5.99187637999995,37.0747534290001 -5.86048821499998,37.0247189180001 -5.89671539799997,37.0221495453306 -5.8874393018095,36.999674903 -5.91984894299998,36.9645132292764 -5.92801938295989),(35.3626345930001 -3.64897803999997,35.362631311 -3.64898468499996,35.362653515 -3.64902908799996,35.3626345930001 -3.64897803999997),(35.3626345930001 -3.64897803999997,35.362772105 -3.64869977399997,35.362588203 -3.64885288899995,35.3626345930001 -3.64897803999997),(37.9826293386546 -4.75134903510222,37.982317073 -4.75391290199997,37.9983234380001 -4.77966730399993,37.9826293386546 -4.75134903510222),(35.8643652310001 -4.34407792099995,36.0006806480001 -4.39701299099994,35.818538248 -4.31507612199994,35.8643652310001 -4.34407792099995)),((34.928032075 -4.26679901999995,34.928001141 -4.26679879199997,34.9280322450001 -4.26679879299996,34.928032075 -4.26679901999995)))";
        final Polygon2D footprint = Polygon2D.fromWKT(wkt);
        final String tileCodes = "36MZS,36MZT,36MYT,36MZU,36MYU,36MZV,36MYV,37MBQ,37MCR,37MBR,37MCS,37MBS,36MZA,36MZB,36MYA,36MYB,37MBN,37MDP,37MEQ,37MDQ,37MCP,37MDR,37MBP,37MCQ";
        final String[] tokens = tileCodes.split(",");
        final Set<String> tiles = new HashSet<>(Arrays.asList(tokens));
        final double slcIntersection = 0.05;
        final double s2Intersection = 0.05;
        final int coherenceDays = 6;
        List<String> results = findS1Intersections(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 12, 31),
                                                   footprint.toWKT(), tiles, slcIntersection, s2Intersection, coherenceDays);
        Path destination = Paths.get("D:\\s1_tanzania_production_plan_6days.txt");
        Files.deleteIfExists(destination);
        Files.createFile(destination);
        Files.write(destination, (String.join("\n", results)).getBytes(), StandardOpenOption.APPEND);
        System.exit(0);
    }

    private static Connection openConnection() throws Exception {
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection("jdbc:postgresql://localhost:5432/sen4cap", "admin", "admin");
    }

    private static List<String> findS1Intersections(LocalDate from, LocalDate to, String footprint, Set<String> s2TileFilter,
                                                    double slcIntersection, double s2Intersection, int coherenceDays) {
        Map<String, List<Triple<LocalDateTime, String, Geometry>>> orbits = new TreeMap<>();
        final List<String> allLines = new ArrayList<>();
        try {
            final DataSource<?, ?> dataSource = getDatasourceRegistry().getService(SciHubDataSource.class);
            dataSource.setCredentials("", "");
            final DataQuery query = dataSource.createQuery("Sentinel-1".replace("-", ""));
            query.addParameter(CommonParameterNames.PLATFORM, "Sentinel-1");
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, "SLC");
            Class clazz = LocalDateTime[].class;
            final QueryParameter begin = query.createParameter(CommonParameterNames.START_DATE,
                                                               clazz,
                                                               from.atStartOfDay(),
                                                               to.plusDays(1).atStartOfDay().minusSeconds(1));
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
            int size = results.size();
            orbits.values().forEach(l -> {
                l.sort(Comparator.comparing(Triple::getKeyOne));
            });
            final AtomicInteger bCounter = new AtomicInteger(1);
            final AtomicInteger cCounter = new AtomicInteger(1);
            final AtomicInteger gCounter = new AtomicInteger(1);
            final Map<String, List<String>> orbitLines = new HashMap<>();
            allLines.add(String.format("%-12s", "AOI:") + footprint);
            allLines.add(String.format("%-12s", "Start date:") + from.format(DateTimeFormatter.ISO_DATE));
            allLines.add(String.format("%-12s", "End date:") + to.format(DateTimeFormatter.ISO_DATE));
            allLines.add(String.format("%-12s", "S2 Tiles:") + String.join(",", s2TileFilter));
            allLines.add(String.format("%-12s", "Coherence@:") + coherenceDays + " days");
            orbits.keySet().forEach(k -> orbitLines.put(k, new ArrayList<>()));
            Parallel.ForEach(orbits.keySet(), (orbit) -> {
            //for (String orbit : orbits.keySet()) {
                final List<String> lines = orbitLines.get(orbit);
                String message;
                lines.add("ORBIT " + orbit);
                final List<Triple<LocalDateTime, String, Geometry>> list = orbits.get(orbit);
                final int listSize = list.size();
                for (int i = 0; i < listSize; i++) {
                    final Triple<LocalDateTime, String, Geometry> previous = list.get(i);
                    Triple<LocalDateTime, String, Geometry> current = null;
                    System.out.println(previous.getKeyTwo() + " (" + gCounter.getAndIncrement() + " / " + size + ")");
                    lines.add("\t" + previous.getKeyTwo());
                    lines.add("\t\tBackscatter");
                    Map<String, Double> s2Tiles = getIntersectingS2Tiles(previous.getKeyThree(), s2Intersection);
                    s2Tiles.entrySet().removeIf(e -> !s2TileFilter.contains(e.getKey()));
                    if (s2Tiles.isEmpty()) {
                        lines.add("\t\t\tNo intersection with S2 tile filter");
                    } else {
                        final Map<String, Double> bckNames = computeBackscatterProducts(previous.getKeyTwo(), previous.getKeyOne(), orbit, s2Tiles);
                        for (Map.Entry<String, Double> entry : bckNames.entrySet()) {
                            lines.add("\t\t\t" + entry.getKey() + "    " + String.format("%.2f%%", entry.getValue() * 100));
                            bCounter.incrementAndGet();
                        }
                    }
                    if (i < listSize - 1) {
                        for (int j = i + 1; j < listSize; j++) {
                            current = list.get(j);
                            final long offset = Duration.between(previous.getKeyOne(), current.getKeyOne()).toDays();
                            if (offset > coherenceDays + 1) break;
                            if (offset >= coherenceDays - 1 && offset <=  coherenceDays + 1) {
                                final Geometry intersection = current.getKeyThree().intersection(previous.getKeyThree());
                                if (intersection != null && intersection.getArea() > 0 && intersection.getArea() / previous.getKeyThree().getArea() > slcIntersection) {
                                    message = current.getKeyTwo() + "\t" + String.format("%.2f%%", (intersection.getArea() / previous.getKeyThree().getArea() * 100));
                                    //System.out.println(message);
                                    lines.add("\t\tCoherence @ " + coherenceDays + " days: " + message);
                                    s2Tiles = getIntersectingS2Tiles(intersection, s2Intersection);
                                    s2Tiles.entrySet().removeIf(e -> !s2TileFilter.contains(e.getKey()));
                                    if (s2Tiles.isEmpty()) {
                                        lines.add("\t\t\tNo intersection with S2 tile filter");
                                    } else {
                                        final Map<String, Double> coheNames = computeCoherenceProducts(current.getKeyTwo(), previous.getKeyTwo(),
                                                                                                       current.getKeyOne(), previous.getKeyOne(),
                                                                                                       orbit, s2Tiles, Master.NEWEST);
                                        for (Map.Entry<String, Double> entry : coheNames.entrySet()) {
                                            lines.add("\t\t\t" + entry.getKey() + "    " + String.format("%.2f%%", entry.getValue() * 100));
                                            cCounter.incrementAndGet();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                lines.add(String.format("%102s", "").replace(" ", "-"));
            });
            allLines.add(String.format("%-12s", "S1 Orbits:") + orbits.entrySet().stream().map(e -> e.getKey() + "(" + e.getValue().size() + ")").collect(Collectors.joining(",")));
            allLines.add("Acquisitions: " + size
                                 + ". Backscatter products: " + bCounter.decrementAndGet()
                                 + ". Coherence products: " + cCounter.decrementAndGet());
            allLines.add(String.format("%102s", "").replace(" ", "="));
            orbitLines.values().forEach(allLines::addAll);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return allLines;
    }

    private static Map<String, Double> getIntersectingS2Tiles(Geometry s1Geometry, double intersection) {
        final Set<String> tiles = Sentinel2TileExtent.getInstance().intersectingTiles(Polygon2D.fromWKT(new GeometryAdapter().unmarshal(s1Geometry)));
        final Map<String, Double> values = new LinkedHashMap<>();
        tiles.stream().sorted().forEach(t -> {
            try {
                final Polygon2D polygon = Polygon2D.fromPath2D(Sentinel2TileExtent.getInstance().getTileExtent(t));
                final Geometry tileGeometry = new GeometryAdapter().marshal(polygon.toWKT());
                final double intersect = tileGeometry.intersection(s1Geometry).getArea() / tileGeometry.getArea();
                if (intersect > intersection) {
                    values.put(t, intersect);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return values;
    }

    private static Map<String, Double> computeBackscatterProducts(String s1Name, LocalDateTime date, String orbit, Map<String, Double> s2Tiles) {
        final Map<String, Double> names = new LinkedHashMap<>();
        final String baseName = s1Name.substring(0, 3) + "_L2_BCK_" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")) + "_";
        for (Map.Entry<String, Double> entry : s2Tiles.entrySet()) {
            names.put(baseName + "VH_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(), entry.getValue());
            names.put(baseName + "VV_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(), entry.getValue());
        }
        return names;
    }

    private static Map<String, Double> computeCoherenceProducts(String s1Name, String s1PrevName, LocalDateTime current, LocalDateTime previous,
                                                         String orbit, Map<String, Double> s2Tiles, Master mode) {
        final Map<String, Double> names = new LinkedHashMap<>();
        final DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        final String currentSensor = s1Name.substring(0, 3);
        String baseName = currentSensor + "_L2_COHE_";
        for (Map.Entry<String, Double> entry : s2Tiles.entrySet()) {
            switch (mode) {
                case S1A:
                    if (currentSensor.equals("S1A")) {
                        names.put(baseName + current.format(format) + "_" + previous.format(format) + "_VH_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(),
                                  entry.getValue());
                        names.put(baseName + current.format(format) + "_" + previous.format(format) + "_VV_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(),
                                  entry.getValue());
                    } else {
                        names.put(baseName + previous.format(format) + "_" + current.format(format) + "_VH_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(),
                                  entry.getValue());
                        names.put(baseName + previous.format(format) + "_" + current.format(format) + "_VV_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(),
                                  entry.getValue());
                    }
                    break;
                case S1B:
                    if (currentSensor.equals("S1B")) {
                        names.put(baseName + current.format(format) + "_" + previous.format(format) + "_VH_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(),
                                  entry.getValue());
                        names.put(baseName + current.format(format) + "_" + previous.format(format) + "_VV_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(),
                                  entry.getValue());
                    } else {
                        names.put(baseName + previous.format(format) + "_" + current.format(format) + "_VH_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(),
                                  entry.getValue());
                        names.put(baseName + previous.format(format) + "_" + current.format(format) + "_VV_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(),
                                  entry.getValue());
                    }
                    break;
                case NEWEST:
                    names.put(baseName + current.format(format) + "_" + previous.format(format) + "_VH_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(),
                              entry.getValue());
                    names.put(baseName + current.format(format) + "_" + previous.format(format) + "_VV_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(),
                              entry.getValue());
                    break;
                case OLDEST:
                    names.put(baseName + previous.format(format) + "_" + previous.format(format) + "_VH_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(),
                              entry.getValue());
                    names.put(baseName + previous.format(format) + "_" + previous.format(format) + "_VV_" + String.format("%3s", orbit).replace(" ", "0") + "_" + entry.getKey(),
                              entry.getValue());
                    break;
            }
        }
        return names;
    }

    private static ServiceRegistry<DataSource> getDatasourceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }

    private enum Master {
        S1A,
        S1B,
        NEWEST,
        OLDEST
    }
}
