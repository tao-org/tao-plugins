package ro.cs.tao.datasource.remote.das;/*
 * Copyright (C) 2018 CS ROMANIA
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.component.enums.Condition;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.das.json.DASJsonResponseHandler;
import ro.cs.tao.datasource.remote.das.json.Results;
import ro.cs.tao.datasource.remote.das.parameters.DASPolygonParameterConverter;
import ro.cs.tao.datasource.remote.das.parameters.DateTimeParameterConverter;
import ro.cs.tao.datasource.remote.result.ResponseParser;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.util.ProductHelper;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;
import ro.cs.tao.products.sentinels.Sentinel2TileExtent;
import ro.cs.tao.products.sentinels.SentinelProductHelper;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.DateUtils;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class DASQuery extends DataQuery {
    static final Pattern S1Pattern =
            Pattern.compile("(S1[A-B])_(SM|IW|EW|WV)_(SLC|GRD|RAW|OCN)([FHM_])_([0-2])([AS])(SH|SV|DH|DV)_(\\d{8}T\\d{6})_(\\d{8}T\\d{6})_(\\d{6})_([0-9A-F]{6})_([0-9A-F]{4})(?:.SAFE)?");

    private static final DateTimeFormatter dateFormat = DateUtils.getFormatterAtUTC("yyyyMMdd'T'HHmmss");
    private static final Set<Class<?>> unquotableTypes  = new HashSet<>() {{
        add(short.class); add(int.class); add(long.class); add(float.class); add(double.class);
    }};

    private final String sensorName;

    static {
        final ConverterFactory factory = new ConverterFactory();
        factory.register(DASPolygonParameterConverter.class, Polygon2D.class);
        factory.register(DateTimeParameterConverter.class, LocalDateTime.class);
        converterFactory.put(DASQuery.class, factory);
    }

    DASQuery(DASDataSource source, String sensorName) {
        super(source, sensorName);
        this.sensorName = sensorName;
    }

    @Override
    protected List<EOProduct> executeImpl() throws QueryException {
        final Map<String, EOProduct> results = new LinkedHashMap<>();
        Map<String, String> queries = null;
        try {
            queries = buildQueriesParams();
        } catch (ConversionException e) {
            throw new RuntimeException(e);
        }
        final String platform = this.parameters.get(CommonParameterNames.PLATFORM).getValue().toString();
        final boolean isS2 = "Sentinel-2".equalsIgnoreCase(platform);
        final boolean isS1Aux = "Sentinel1-orbit-files".equals(this.sensorName);
        final boolean isS1 = "Sentinel-1".equalsIgnoreCase(platform) && !isS1Aux;
        final boolean isS3 = "Sentinel-3".equalsIgnoreCase(platform);
        final StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append(String.format("Query {%s-%s} has %d subqueries: ", this.source.getId(), this.sensorName, queries.size()));
        for (String queryId : queries.keySet()) {
            msgBuilder.append(queryId).append(",");
        }
        msgBuilder.setLength(msgBuilder.length() - 1);
        this.logger.fine(msgBuilder.toString());
        final int actualLimit = this.limit > 0 ? this.limit : DEFAULT_LIMIT;
        msgBuilder.setLength(0);
        if (this.pageSize <= 0) {
            this.pageSize = Math.min(actualLimit, DEFAULT_PAGE_SIZE);
        } else {
            this.pageSize = Math.min(actualLimit, this.pageSize);
        }
        if (this.pageNumber == 1) {
            this.pageNumber = -1;
        }
        final int page = Math.max(this.pageNumber, 1);
        List<EOProduct> tmpResults;
        for (Map.Entry<String, String> query : queries.entrySet()) {
            long i = page;
            long count;
            do {
                count = 0;
                final List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("$filter", query.getValue()));
                params.add(new BasicNameValuePair("$expand", "Attributes"));
                params.add(new BasicNameValuePair("$expand", "Assets"));
                params.add(new BasicNameValuePair("$top", String.valueOf(this.pageSize)));
                params.add(new BasicNameValuePair("$skip", String.valueOf((i - 1) * this.pageSize)));
                params.add(new BasicNameValuePair("$orderby", "ContentDate/Start asc"));
                final String queryUrl = this.source.getConnectionString() + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
                final boolean hasProcessingDate = "Sentinel2".equals(this.sensorName) || "Sentinel3".equals(this.sensorName);
                this.logger.fine(String.format("Executing query %s: %s", query.getKey(), queryUrl));
                try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, this.source.getCredentials())) {
                    final String rawResponse = EntityUtils.toString(response.getEntity());
                    switch (response.getStatusLine().getStatusCode()) {
                        case 200:
                            final ResponseParser<EOProduct> parser = new JsonResponseParser<>(new DASJsonResponseHandler((DASDataSource) this.source,
                                                                                                                         this.coverageFilter));
                            tmpResults = parser.parse(rawResponse);
                            if (tmpResults != null) {
                                if (isS3) {
                                    filterS3(tmpResults);
                                }
                                count = tmpResults.size();
                                if (count > 0) {
                                    /*if (hasProcessingDate && this.parameters.containsKey(CommonParameterNames.CLOUD_COVER)) {
                                        final double clouds = Double.parseDouble(this.parameters.get(CommonParameterNames.CLOUD_COVER).getValue().toString());
                                        tmpResults = tmpResults.stream()
                                                .filter(r -> Double.parseDouble(r.getAttributeValue("cloudCover")) <= clouds)
                                                .collect(Collectors.toList());
                                    }*/
                                    ProductHelper productHelper = null;
                                    for (EOProduct result : tmpResults) {
                                        if (!results.containsKey(result.getName())) {
                                            try {
                                                productHelper = SentinelProductHelper.create(result.getName());
                                            } catch (Exception ex) {
                                                this.logger.warning(String.format("Product %s not supported. Will be ignored", result.getName()));
                                                continue;
                                            }
                                            if (isS2) {
                                                if (result.getAttributeValue("tileId") == null) {
                                                    result.addAttribute("tiles", ((Sentinel2ProductHelper) productHelper).getTileIdentifier());
                                                } else {
                                                    result.addAttribute("tiles", result.getAttributeValue("tileId"));
                                                }
                                            } else if (isS1 && !isS1Aux) {
                                                result.addAttribute("relativeOrbit", getRelativeOrbit(result.getName()));
                                            }
                                            if (hasProcessingDate) {
                                                final String dateString = productHelper.getProcessingDate();
                                                if (dateString != null) {
                                                    try {
                                                        result.setProcessingDate(LocalDateTime.parse(dateString, dateFormat));
                                                    } catch (DateTimeParseException ignored) {
                                                    }
                                                }
                                            }
                                            results.put(result.getName(), result);
                                        }
                                    }
                                }
                                this.logger.info(String.format("Query %s page %d returned %s products", query.getKey(), i, tmpResults.size()));
                            }
                            break;
                        case 401:
                            throw new QueryException("The supplied credentials are invalid!");
                        default:
                            throw new QueryException(String.format("The request was not successful. Reason: %s ('%s')",
                                                                   response.getStatusLine().getReasonPhrase(),
                                                                   rawResponse));
                    }
                } catch (IOException ex) {
                    throw new QueryException(ex);
                }
                i++;
            } while (this.pageNumber == -1 && count > 0 && results.size() < actualLimit);
        }
        this.logger.info(String.format("Query {%s-%s} returned %s products", this.source.getId(), this.sensorName, results.size()));
        return new ArrayList<>(results.values());
    }

    @Override
    public long getCount() {
        final Set<String> mandatoryParams = getMandatoryParams();
        final Map<String, QueryParameter<?>> actualParameters = getParameters();
        final List<String> errors = mandatoryParams.stream()
                .filter(p -> !actualParameters.containsKey(p)).collect(Collectors.toList());
        if (!errors.isEmpty()) {
            QueryException ex = new QueryException("Mandatory parameter(s) not supplied");
            ex.addAdditionalInfo("Missing", String.join(",", errors));
            throw ex;
        }
        errors.addAll(checkDependencies(actualParameters));
        if (!errors.isEmpty()) {
            QueryException ex = new QueryException("Some parameter(s) dependencies are not satisfied");
            ex.addAdditionalInfo("Dependencies", String.join(",", errors));
            throw ex;
        }
        long count = 0;
        Map<String, String> queries = null;
        try {
            queries = buildQueriesParams();
        } catch (ConversionException e) {
            throw new RuntimeException(e);
        }
        //final int size = queries.size();
        final String countUrl = this.source.getProperty("search.url");
        if (countUrl != null) {
            for (Map.Entry<String, String> query : queries.entrySet()) {
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("$top", "0"));
                params.add(new BasicNameValuePair("$filter", "(" + query.getValue() + ")"));
                params.add(new BasicNameValuePair("$count", "True"));
                String queryUrl = countUrl + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
                this.logger.fine(String.format("Executing query %s: %s", query.getKey(), queryUrl));
                try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, this.source.getCredentials())) {
                    switch (response.getStatusLine().getStatusCode()) {
                        case 200:
                            String rawResponse = EntityUtils.toString(response.getEntity());
                            Results results = new ObjectMapper().readerFor(Results.class).readValue(rawResponse);
                            count = results.getCount();
                            this.logger.fine(String.format(QUERY_RESULT_MESSAGE, query.getKey(),
                                                           this.source.getId(), this.sensorName, 1, count));
                            break;
                        case 401:
                            throw new QueryException("The supplied credentials are invalid!");
                        default:
                            throw new QueryException(String.format("The request was not successful. Reason: %s",
                                                                   response.getStatusLine().getReasonPhrase()));
                    }
                } catch (IOException ex) {
                    throw new QueryException(ex);
                }
            }
        }
        return count;
    }

    @Override
    public String defaultId() { return "CreoDiasQuery"; }

    private Map<String, String> buildQueriesParams() throws ConversionException {
        Map<String, String> queries = new HashMap<>();
        final boolean isS1Aux = "Sentinel1-orbit-files".equals(this.sensorName);
        if (!this.parameters.containsKey(CommonParameterNames.PLATFORM)) {
            addParameter(CommonParameterNames.PLATFORM, this.dataSourceParameters.get(CommonParameterNames.PLATFORM).getDefaultValue());
        }
        String[] footprints = new String[0];
        final boolean isS1 = "Sentinel-1".equals(this.parameters.get(CommonParameterNames.PLATFORM).getValue()) && !isS1Aux;
        final boolean canUseTileParameter = this.parameters.containsKey(CommonParameterNames.TILE) && !isS1;
        if (this.parameters.containsKey(CommonParameterNames.TILE) && !isS1) {
            final QueryParameter<?> tileParameter = this.parameters.get(CommonParameterNames.TILE);
            final Object value = tileParameter.getValue();
            if (value != null) {
                if (value.getClass().isArray()) {
                    footprints = new String[Array.getLength(value)];
                    for (int i = 0; i < footprints.length; i++) {
                        Polygon2D polygon = Polygon2D.fromPath2D(Sentinel2TileExtent.getInstance().getTileExtent(Array.get(value, i).toString()));
                        footprints[i] = polygon.toWKT();
                    }
                } else {
                    String strVal = tileParameter.getValueAsString();
                    if (strVal.startsWith("[") && strVal.endsWith("]")) {
                        String[] values = strVal.substring(1, strVal.length() - 1).split(",");
                        footprints = new String[values.length];
                        for (int i = 0; i < values.length; i++) {
                            Polygon2D polygon = Polygon2D.fromPath2D(Sentinel2TileExtent.getInstance().getTileExtent(strVal));
                            footprints[i] = polygon.toWKT();
                        }
                    } else {
                        Polygon2D polygon = Polygon2D.fromPath2D(Sentinel2TileExtent.getInstance().getTileExtent(strVal));
                        if (polygon != null) {
                            footprints = new String[]{polygon.toWKT()};
                        } else {
                            this.logger.warning(String.format("No extent found for tile '%s'. Maybe it is not a Sentinel-2 product",
                                                              strVal));
                        }
                    }
                }
            }
        }
        if (this.parameters.containsKey(CommonParameterNames.FOOTPRINT) && footprints.length == 0) {
            Polygon2D polygon = (Polygon2D) this.parameters.get(CommonParameterNames.FOOTPRINT).getValue();
            footprints = polygon.toWKTArray();
        }
        StringBuilder query = new StringBuilder();
        if (this.parameters.containsKey(CommonParameterNames.PRODUCT)) {
            String productName = this.parameters.get(CommonParameterNames.PRODUCT).getValueAsString();
            if (!productName.endsWith(".SAFE") && !isS1Aux) {
                productName += ".SAFE";
            }
            query.append(buildPropertyFilter(getRemoteName(CommonParameterNames.PRODUCT), productName, Condition.EQ));
            queries.put(UUID.randomUUID().toString(), query.toString());
            query.setLength(0);
        } else {
            if (footprints.length == 0) {
                footprints = new String[] { "" };
            }
            for (String footprint : footprints) {
                int idx = 0;
                for (Map.Entry<String, QueryParameter<?>> entry : this.parameters.entrySet()) {
                    QueryParameter parameter = entry.getValue();
                    if (!parameter.isOptional() && !parameter.isInterval() && parameter.getValue() == null) {
                        throw new QueryException(String.format("Parameter [%s] is required but no value is supplied", parameter.getName()));
                    }
                    /*if (parameter.getName().equalsIgnoreCase(CommonParameterNames.PRODUCT)) {
                        query.append(buildPropertyFilter(getRemoteName(CommonParameterNames.PRODUCT),
                                                         parameter, Condition.EQ));
                        break;
                    }*/
                    if (parameter.isOptional() &
                            ((!parameter.isInterval() & parameter.getValue() == null) |
                                    (parameter.isInterval() & parameter.getMinValue() == null & parameter.getMaxValue() == null))) {
                        continue;
                    }
                    if (idx > 0 && query.length() > 0) {
                        query.append(" and ");
                    }
                    String value;
                    switch (parameter.getName()) {
                        case CommonParameterNames.PLATFORM:
                            //if (!isS1Aux) {
                                query.append(isS1Aux
                                             ? buildAttributeFilter(getRemoteName(CommonParameterNames.PLATFORM), parameter, Condition.EQ)
                                             : buildPropertyFilter(getRemoteName(CommonParameterNames.PLATFORM), parameter, Condition.EQ));
                            ;
                            //}
                            break;
                        case CommonParameterNames.START_DATE:
                            query.append(buildPropertyFilter(getRemoteName(CommonParameterNames.START_DATE),
                                                             parameter, Condition.GTE));
                            break;
                        case CommonParameterNames.END_DATE:
                            query.append(buildPropertyFilter(getRemoteName(CommonParameterNames.END_DATE),
                                                             parameter, Condition.LT));
                            break;
                        case CommonParameterNames.FOOTPRINT:
                            if (!isS1Aux) {
                                if (!canUseTileParameter) {
                                    try {
                                        QueryParameter<Polygon2D> fakeParam = new QueryParameter<>(Polygon2D.class,
                                                                                                   "footprint",
                                                                                                   Polygon2D.fromWKT(footprint));
                                        query.append(getParameterValue(fakeParam));
                                    } catch (ConversionException e) {
                                        throw new QueryException(e.getMessage());
                                    }
                                } else {
                                    // remove the last " AND " because parameter is skipped
                                    if (query.length() >= 5 && " and ".equals(query.substring(query.length() - 5))) {
                                        query.setLength(query.length() - 5);
                                    }
                                    idx--;
                                }
                            }
                            break;
                        case CommonParameterNames.TILE:
                            value = parameter.getValueAsString();
                            if (value.startsWith("[") && value.endsWith("]")) {
                                String[] values = value.substring(1, value.length() - 1).split(",");
                                query.append("(");
                                for (int i = 0; i < values.length; i++) {
                                    if (isS1) {
                                        query.append(buildAttributeFilter("relativeOrbitNumber", values[i], Condition.EQ));
                                    } else {
                                        query.append("contains(Name, '").append(values[i]).append("')");
                                    }
                                    if (i < values.length - 1) {
                                        query.append(" or ");
                                    }
                                }
                                query.append(")");
                            } else {
                                if (isS1) {
                                    query.append(buildAttributeFilter("relativeOrbitNumber", value, Condition.EQ));
                                } else {
                                    query.append("contains(Name, '").append(value).append("')");
                                }
                            }
                            break;
                        case CommonParameterNames.PRODUCT_TYPE:
                            value = parameter.getValueAsString();
                            final String paramTypeName = getRemoteName(parameter.getName());
                            if (value.startsWith("[") && value.endsWith("]")) {
                                String[] values = value.substring(1, value.length() - 1).split(",");
                                query.append("(");
                                for (int i = 0; i < values.length; i++) {
                                    query.append(buildTypeFilter(values[i]));
                                    if (i < values.length - 1) {
                                        query.append(" or ");
                                    }
                                }
                                query.append(")");
                            } else {
                                query.append(buildTypeFilter(value));
                            }
                            break;
                        case "productSize":
                            // productSize filter will be applied after results are returned
                            query.setLength(query.length() - 5);
                            break;
                        default:
                            if (parameter.getType().isArray()) {
                                query.append("(");
                                Object values = parameter.getValue();
                                int length = Array.getLength(values);
                                for (int i = 0; i < length; i++) {
                                    query.append(buildAttributeFilter(getRemoteName(parameter.getName()),
                                                                      Array.get(values, i),
                                                                      Condition.EQ));
                                    if (i < length - 1) {
                                        query.append(" or ");
                                    }
                                }
                                if (length > 1) {
                                    query = new StringBuilder(query.substring(0, query.length() - 4));
                                }
                                query.append(")");
                            } else {
                                query.append(buildAttributeFilter(getRemoteName(entry.getKey()),
                                                                  parameter,
                                                                  String.class.equals(parameter.getType())
                                                                  ? Condition.EQ
                                                                  : Condition.LTE));
                            }
                            break;
                    }
                    idx++;
                }
                final String strQuery = query.toString();
                queries.put(UUID.randomUUID().toString(), strQuery.endsWith(" and ") ? strQuery.substring(0, strQuery.length() - 5) : strQuery);
                query.setLength(0);
            }
        }
        return queries;
    }

    private String getRelativeOrbit(String productName) {
        final String value;
        final Matcher matcher = S1Pattern.matcher(productName);
        if (matcher.matches()) {
            int absOrbit = Integer.parseInt(matcher.group(10));
            value = String.format("%03d",
                                  matcher.group(1).endsWith("A")
                                  ? ((absOrbit - 73) % 175) + 1
                                  : ((absOrbit - 27) % 175) + 1);
        } else {
            value = null;
        }
        return value;
    }

    private void filterS3(List<EOProduct> results) {
        if (this.parameters.containsKey("productSize")) {
            String productSize = this.parameters.get("productSize").getValueAsString();
            if ("STRIPE".equalsIgnoreCase(productSize)) {
                results.removeIf(p -> !p.getName().contains("_____"));
            } else if ("FRAME".equalsIgnoreCase(productSize)) {
                results.removeIf(p -> p.getName().contains("_____"));
            }
        }
    }

    private String buildPropertyFilter(String name, QueryParameter<?> parameter, Condition condition) throws ConversionException {
        boolean shouldQuote = !Number.class.isAssignableFrom(parameter.getType())
                && !LocalDateTime.class.isAssignableFrom(parameter.getType())
                && !unquotableTypes.contains(parameter.getType());
        String cond = condition.name().length() == 3
                      ? condition.name().toLowerCase().replace("t", "")
                      : condition.name().toLowerCase();
        return name + " " + cond + " " +
                (shouldQuote ? "'" : "") + getParameterValue(parameter) + (shouldQuote ? "'" : "");
    }

    private String buildPropertyFilter(String name, String value, Condition condition) {
        String cond = condition.name().length() == 3
                      ? condition.name().toLowerCase().replace("t", "")
                      : condition.name().toLowerCase();
        return name + " " + cond + " '" + value + "'";
    }

    private String buildAttributeFilter(String name, QueryParameter<?> parameter, Condition condition) throws ConversionException {
        Class<?> paramType = parameter.getType();
        boolean shouldQuote = !Number.class.isAssignableFrom(paramType) && !unquotableTypes.contains(paramType);
        String cond = condition.name().length() == 3
                      ? condition.name().toLowerCase().replace("t", "")
                      : condition.name().toLowerCase();
        String oDataType = "OData.CSC.";
        if (paramType.equals(Integer.class) || paramType.equals(int.class)) {
            oDataType += "Integer";
        } else if (paramType.equals(Double.class) || paramType.equals(double.class)) {
            oDataType += "Double";
        } else if (paramType.equals(String.class)) {
            oDataType += "String";
        } else if (paramType.equals(LocalDateTime.class)) {
            oDataType += "DateTimeOffset";
        }
        oDataType += "Attribute";
        return "Attributes/" + oDataType + "/any(att:att/Name eq '" + name +
                "' and att/" + oDataType + "/Value " + cond + " " +
                (shouldQuote ? "'" : "") + getParameterValue(parameter) + (shouldQuote ? "')" : ")");
    }

    private String buildAttributeFilter(String name, Object value, Condition condition) throws ConversionException {
        Class<?> paramType = value.getClass();
        boolean shouldQuote = !Number.class.isAssignableFrom(paramType) && !unquotableTypes.contains(paramType);
        String cond = condition.name().length() == 3
                      ? condition.name().toLowerCase().replace("t", "")
                      : condition.name().toLowerCase();
        String oDataType = "OData.CSC.";
        if (paramType.equals(Integer.class) || paramType.equals(int.class)) {
            oDataType += "Integer";
        } else if (paramType.equals(Double.class) || paramType.equals(double.class)) {
            oDataType += "Double";
        } else if (paramType.equals(String.class)) {
            oDataType += "String";
        } else if (paramType.equals(LocalDateTime.class)) {
            oDataType += "DateTimeOffset";
        }
        oDataType += "Attribute";
        return "Attributes/" + oDataType + "/any(att:att/Name eq '" + name +
                "' and att/" + oDataType + "/Value " + cond + " " +
                (shouldQuote ? "'" : "") + value + (shouldQuote ? "')" : ")");
    }

    private String buildTypeFilter(Object value) throws ConversionException {
        String stringVal = value.toString();
        List<String> tokens = new ArrayList<>();
        if (stringVal.contains("-")) {
            tokens.addAll(Arrays.stream(stringVal.split("-")).collect(Collectors.toList()));
        } else {
            tokens.add(stringVal);
        }
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        final int size = tokens.size();
        for (int i = 0; i < size; i++) {
            builder.append("contains(Name,'");
            if (i > 0) {
                builder.append("_");
            }
            builder.append(tokens.get(i)).append("')");
            if (i < size - 1 && i >= 0) {
                builder.append(" and ");
            }
        }
        builder.append(")");
        return builder.toString();
    }
}
