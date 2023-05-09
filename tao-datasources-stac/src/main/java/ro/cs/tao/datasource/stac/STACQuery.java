package ro.cs.tao.datasource.stac;

import org.locationtech.jts.geom.Envelope;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.stac.parameters.DateParameterConverter;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.serialization.GeometryAdapter;
import ro.cs.tao.stac.core.STACClient;
import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.model.extensions.ExtensionType;
import ro.cs.tao.stac.core.model.extensions.projection.ProjExtension;

import java.awt.geom.Point2D;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class STACQuery extends DataQuery {

    static {
        final ConverterFactory factory = new ConverterFactory();
        factory.register(DateParameterConverter.class, LocalDateTime.class);
        converterFactory.put(STACQuery.class, factory);
    }

    public STACQuery(STACSource source, String sensorName) {
        super(source, sensorName);
    }

    @Override
    protected List<EOProduct> executeImpl() {
        try {
            STACClient client = ((STACSource) this.source).authenticate();
            Map<String, Object> params = new LinkedHashMap<>();
            QueryParameter<?> parameter = this.parameters.get(CommonParameterNames.FOOTPRINT);
            if (parameter != null) {
                String footprint = getParameterValue(parameter);
                Envelope envelope = new GeometryAdapter().marshal(footprint).getEnvelope().getEnvelopeInternal();
                String bbox = envelope.getMinX() + "," + envelope.getMinY() + ","
                            + envelope.getMaxX() + "," + envelope.getMaxY();
                params.put(getRemoteName(CommonParameterNames.FOOTPRINT), bbox);
            }
            String dateValue = null;
            parameter = this.parameters.get(CommonParameterNames.START_DATE);
            if (parameter != null) {
                // "2022-05-01T00:00:00Z/2022-05-02T23:59:59Z"
                dateValue = getParameterValue(parameter) + "/";
            }
            parameter = this.parameters.get(CommonParameterNames.END_DATE);
            if (parameter != null) {
                dateValue = (dateValue != null ? dateValue : "/") + getParameterValue(parameter);
            }
            params.put(getRemoteName(CommonParameterNames.START_DATE), dateValue);
            if (this.pageSize > 0) {
                params.put("limit", this.pageSize);
            }
            ItemCollection currentPage = client.search(this.sensorName, params);
            final List<EOProduct> results = new ArrayList<>();
            PageContext context = currentPage.getContext();
            final int total = context == null ? -1 : context.getMatched();
            Link link;
            if (total > this.pageSize && this.pageNumber > 1) {
                link = getLink(currentPage.getLinks(), "page");
                if (link != null) {
                    params.put("page", extractParameterValue(link.getHref(), "page"));
                    currentPage = client.search(this.sensorName, params);
                    List<EOProduct> eoProducts = extractResults(currentPage);
                    eoProducts.forEach(p -> p.setSatelliteName(this.sensorName));
                    results.addAll(eoProducts);
                } else { // we need to follow the 'next' link while we reach the page
                    link = getLink(currentPage.getLinks(), "next");
                    if (link != null) {
                        int idx = 1;
                        while (idx++ < this.pageNumber
                                && currentPage.getFeatures() != null
                                && currentPage.getFeatures().size() > 0) {
                            params.put("next", extractParameterValue(link.getHref(), "next"));
                            currentPage = client.search(this.sensorName, params);
                            link = getLink(currentPage.getLinks(), "next");
                        }
                        List<EOProduct> eoProducts = extractResults(currentPage);
                        eoProducts.forEach(p -> p.setSatelliteName(this.sensorName));
                        results.addAll(eoProducts);
                    } else {
                        List<EOProduct> eoProducts = extractResults(currentPage);
                        eoProducts.forEach(p -> p.setSatelliteName(this.sensorName));
                        results.addAll(eoProducts);
                    }
                }
            } else {
                List<EOProduct> eoProducts = extractResults(currentPage);
                eoProducts.forEach(p -> p.setSatelliteName(this.sensorName));
                results.addAll(eoProducts);
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Link getLink(final List<Link> links, String relation) {
        return links != null
               ? links.stream().filter(l -> relation.equalsIgnoreCase(l.getRel())).findFirst().orElse(null)
               : null;
    }

    private String extractParameterValue(String href, String name) throws URISyntaxException {
        int idx = href.indexOf(name + "=");
        int nextParamIdx = href.indexOf("&", idx);
        return href.substring(idx + name.length() + 1,  nextParamIdx > 0 ? nextParamIdx : href.length());
    }

    private List<EOProduct> extractResults(ItemCollection result) throws URISyntaxException {
        final List<EOProduct> results = new ArrayList<>();
        if (result != null) {
            List<Item> items = result.getFeatures();
            for (Item item : items) {
                EOProduct product = new EOProduct();
                product.setId(item.getId());
                product.setName(item.getId());
                product.setSensorType(SensorType.UNKNOWN);
                product.setPixelType(PixelType.INT16);
                product.setProductType("unknown");
                Geometry geometry = item.getGeometry();
                if (geometry != null) {
                    Polygon2D polygon = null;
                    switch (geometry.getRank()) {
                        case 2:
                            double[][] c2 = (double[][]) geometry.getCoordinates();
                            polygon = new Polygon2D();
                            for (double[] point : c2) {
                                polygon.append(point[0], point[1]);
                            }
                            break;
                        case 3:
                            double[][][] c3 = (double[][][]) geometry.getCoordinates();
                            polygon = new Polygon2D();
                            for (double[][] coord : c3) {
                                for (double[] point : coord) {
                                    polygon.append(point[0], point[1]);
                                }
                            }
                            break;
                        case 4:
                            double[][][][] c4 = (double[][][][]) geometry.getCoordinates();
                            polygon = new Polygon2D();
                            for (double[][][] coord : c4) {
                                for (double[][] c : coord) {
                                    for (double[] point : c) {
                                        polygon.append(point[0], point[1]);
                                    }
                                }
                            }
                            break;
                        case 1:
                        default:
                            break;
                    }
                    if (polygon != null) {
                        List<Point2D> points = polygon.getPoints();
                        if (!points.get(0).equals(points.get(points.size() - 1))) {
                            polygon.append(points.get(0).getX(), points.get(0).getY());
                        }
                        product.setGeometry(polygon.toWKT());
                    }
                }
                final Map<String, Asset> assets = item.getAssets();
                Asset aProduct = assets.get("PRODUCT");
                if (aProduct != null) {
                    product.setLocation(aProduct.getHref());
                } else {
                    Link link = getLink(item.getLinks(), "self");
                    if (link != null) {
                        product.setLocation(link.getHref());
                    }
                }
                product.setApproximateSize(assets.size());
                product.setAcquisitionDate(item.getDatetime());
                Asset thumbnail = assets.get("thumbnail");
                if (thumbnail != null) {
                    product.setQuicklookLocation(thumbnail.getHref());
                } else {
                    thumbnail = assets.get("QUICKLOOK");
                    if (thumbnail != null) {
                        product.setQuicklookLocation(thumbnail.getHref());
                    }
                }
                product.setFormatType(DataFormat.RASTER);
                Extension<?> extension = item.getExtension(ExtensionType.PROJ);
                if (extension != null) {
                    ProjExtension proj = ((ProjExtension) extension);
                    product.setCrs("EPSG:" + proj.getEpsg());
                }
                Map<String, Object> fields = item.getFields();
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    product.addAttribute(entry.getKey(), String.valueOf(entry.getValue()));
                }
                for (Asset asset : assets.values()) {
                    product.addFile(asset.getHref());
                }
                results.add(product);
            }
        }
        return results;
    }
}
