package ro.cs.tao.wmts.impl;

import org.geotools.ows.ServiceException;
import org.geotools.ows.wms.CRSEnvelope;
import org.geotools.ows.wms.StyleImpl;
import org.geotools.ows.wms.xml.MetadataURL;
import org.geotools.ows.wmts.WebMapTileServer;
import org.geotools.ows.wmts.model.WMTSCapabilities;
import org.geotools.ows.wmts.model.WMTSLayer;
import org.geotools.ows.wmts.request.GetTileRequest;
import org.geotools.referencing.CRS;
import org.geotools.tile.Tile;
import org.opengis.referencing.FactoryException;
import ro.cs.tao.component.WebServiceAuthentication;
import ro.cs.tao.docker.Application;
import ro.cs.tao.docker.Container;
import ro.cs.tao.docker.ContainerType;
import ro.cs.tao.wmts.beans.WMTSLayerInfo;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WMTSClient {
    private final String url;
    private final WebServiceAuthentication authentication;
    private final Principal currentPrincipal;

    public WMTSClient(String url, WebServiceAuthentication authentication, Principal principal) {
        this.url = url;
        this.authentication = authentication;
        this.currentPrincipal = principal;
    }

    public Container getCapabilities() throws ServiceException, IOException {
        final WebMapTileServer service = getService();
        final WMTSCapabilities capabilities = service.getCapabilities();
        final Container container = new Container();
        container.setFormat(new HashSet<>() {{ add("WMTS"); }});
        container.setId(url);
        container.setApplicationPath(url);
        container.setName(capabilities.getService().getName());
        container.setType(ContainerType.WMS);
        final List<WMTSLayer> layers = capabilities.getLayerList();
        for (WMTSLayer layer : layers) {
            final Application application = new Application();
            application.setName(layer.getName());
            final List<MetadataURL> metadataURLS = layer.getMetadataURL();
            if (metadataURLS != null && !metadataURLS.isEmpty()) {
                application.setPath(metadataURLS.get(0).getUrl().toString());
            }
            container.addApplication(application);
        }
        return container;
    }

    public WMTSLayerInfo describeLayer(String layerName) throws ServiceException, IOException {
        final WebMapTileServer service = getService();
        final WMTSCapabilities capabilities = service.getCapabilities();
        final WMTSLayer layer = capabilities.getLayerList().stream()
                                            .filter(l -> l.getName().equals(layerName) ||
                                                        (l.getMetadataURL() != null && l.getMetadataURL().stream().anyMatch(u -> u.getUrl().toString().equals(layerName))))
                                            .findFirst().orElse(null);
        if (layer == null) {
            throw new IOException("Layer " + layerName  + " does not exist");
        }
        WMTSLayerInfo info = new WMTSLayerInfo();
        info.setName(layer.getName());
        info.setDescription(layer.getTitle());
        final CRSEnvelope box = layer.getLatLonBoundingBox();
        final Set<String> srsList = layer.getSrs();
        String crs = null;
        if (srsList != null) {
            crs = layer.getSrs().iterator().next();
        }
        info.setCrs(crs != null ? crs : "EPSG:4326");
        double[][] bbox = new double[2][];
        bbox[0] = box.getLowerCorner().getCoordinate();
        bbox[1] = box.getUpperCorner().getCoordinate();
        info.setBoundingBox(bbox);
        info.setFormats(capabilities.getRequest().getGetTile().getFormats().toArray(new String[0]));
        info.setWmtsVersion(capabilities.getVersion());
        final List<StyleImpl> styles = layer.getStyles();
        if (styles != null) {
            for (StyleImpl style : styles) {
                info.addStyle(style.getName());
            }
        }
        return info;
    }

    public void getTile(WMTSLayer layer, Map<String, String> parameters, Path destination) throws ServiceException, IOException, FactoryException {
        final WebMapTileServer service = getService();
        final GetTileRequest tileRequest = service.createGetTileRequest(true);
        tileRequest.setLayer(layer);
        //tileRequest.setRequestedBBox(ReferencedEnvelope.create(parameters.get("bbox"));
        tileRequest.setCRS(CRS.decode(parameters.get("srs")));
        tileRequest.setFormat(parameters.get("format"));
        tileRequest.setRequestedWidth(Integer.parseInt(parameters.get("width")));
        tileRequest.setRequestedHeight(Integer.parseInt(parameters.get("height")));
        final Set<Tile> tiles = tileRequest.getTiles();
        for (Tile tile : tiles) {
            ImageIO.write(tile.getBufferedImage(), "tiff", Files.newOutputStream(destination.resolve(tile.getId())));
        }
    }

    private WebMapTileServer getService() throws ServiceException, IOException {
        return new WebMapTileServer(new URL(this.url),
                                    new WMTSHttpClient(this.authentication));
    }
}
