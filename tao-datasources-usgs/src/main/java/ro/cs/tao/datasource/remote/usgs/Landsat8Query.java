package ro.cs.tao.datasource.remote.usgs;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.result.ResponseParser;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.datasource.remote.usgs.json.ResponseHandler;
import ro.cs.tao.datasource.remote.usgs.parameters.USGSDateConverter;
import ro.cs.tao.datasource.util.HttpMethod;
import ro.cs.tao.datasource.util.NetUtils;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.products.landsat.Landsat8TileExtent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Cosmin Cara
 */
public class Landsat8Query extends DataQuery {

    private static final ConverterFactory converterFactory = ConverterFactory.getInstance();
    private Logger logger;

    static {
        converterFactory.register(USGSDateConverter.class, Date.class);
    }

    Landsat8Query(USGSDataSource source) {
        super(source, "Landsat8");
        this.logger = Logger.getLogger(Landsat8Query.class.getSimpleName());
    }

    @Override
    public String defaultName() {
        return "USGSLandsat8Query";
    }

    @Override
    protected List<EOProduct> executeImpl() throws QueryException {
        List<EOProduct> results = new ArrayList<>();
        List<NameValuePair> params = new ArrayList<>();
        if (!this.parameters.containsKey("satellite_name")) {
            addParameter("satellite_name", this.supportedParams.get("satellite_name").getDefaultValue());
        }
        Set<String> pathRows = null;
        for (QueryParameter parameter : this.parameters.values()) {
            if (Polygon2D.class.equals(parameter.getType())) {
                Polygon2D footprint = (Polygon2D ) parameter.getValue();
                if (footprint != null) {
                    pathRows = Landsat8TileExtent.getInstance().intersectingTiles(footprint.getBounds2D());
                }
            } else {
                try {
                    params.add(new BasicNameValuePair(parameter.getName(),
                                                      converterFactory.create(parameter).stringValue()));
                } catch (ConversionException e) {
                    e.printStackTrace();
                }
            }
        }
        if (this.pageSize > 0) {
            params.add(new BasicNameValuePair("page", String.valueOf(this.pageSize)));
        }
        if (this.pageNumber > 0) {
            params.add(new BasicNameValuePair("skip", String.valueOf(this.pageNumber)));
        }
        if (this.limit > 0) {
            params.add(new BasicNameValuePair("limit", String.valueOf(this.limit)));
        }

        String queryUrl = this.source.getConnectionString() + "?" + URLEncodedUtils.format(params, "UTF-8");
        if (pathRows != null && pathRows.size() > 0) {
            pathRows.forEach(pr -> {
                int path = Integer.parseInt(pr.substring(0, 3));
                int row = Integer.parseInt(pr.substring(3));
                try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET,
                                                                              queryUrl + String.format("&path=%s&row=%s",
                                                                                                       path, row),
                                                                              null)) {
                    switch (response.getStatusLine().getStatusCode()) {
                        case 200:
                            String body = EntityUtils.toString(response.getEntity());
                            ResponseParser<EOProduct> parser = new JsonResponseParser<>(new ResponseHandler());
                            results.addAll(parser.parse(body));
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
            });
        } else {
            try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, null)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case 200:
                        String body = EntityUtils.toString(response.getEntity());
                        ResponseParser<EOProduct> parser = new JsonResponseParser<>(new ResponseHandler());
                        results.addAll(parser.parse(body));
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
        logger.info(String.format("Query returned %s products", results.size()));
        return results;
    }
}
