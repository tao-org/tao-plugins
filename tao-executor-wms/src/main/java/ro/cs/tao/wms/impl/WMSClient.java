package ro.cs.tao.wms.impl;

import org.apache.commons.io.IOUtils;
import org.geotools.ows.ServiceException;
import org.geotools.ows.wms.*;
import org.geotools.ows.wms.request.GetMapRequest;
import org.geotools.ows.wms.response.GetMapResponse;
import org.geotools.ows.wms.xml.MetadataURL;
import ro.cs.tao.component.WebServiceAuthentication;
import ro.cs.tao.docker.Application;
import ro.cs.tao.docker.Container;
import ro.cs.tao.docker.ContainerType;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.wms.beans.LayerInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WMSClient {
    private final String url;
    private final WebServiceAuthentication authentication;
    private final Principal currentPrincipal;

    public WMSClient(String url, WebServiceAuthentication authentication, Principal principal) {
        this.url = url;
        this.authentication = authentication;
        this.currentPrincipal = principal;
    }

    public Container getCapabilities() throws ServiceException, IOException {
        final WebMapServer service = getService();
        final WMSCapabilities capabilities = service.getCapabilities();
        final Container container = new Container();
        container.setFormat(new HashSet<>() {{ add("WMS"); }});
        container.setId(url);
        container.setApplicationPath(url);
        container.setName(capabilities.getService().getName());
        container.setType(ContainerType.WMS);
        final Layer[] layers = WMSUtils.getNamedLayers(capabilities);
        for (Layer layer : layers) {
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

    public LayerInfo describeLayer(String layerName) throws ServiceException, IOException {
        final WebMapServer service = getService();
        final WMSCapabilities capabilities = service.getCapabilities();
        final List<Layer> layerList = capabilities.getLayerList();
        Layer layer = null;
        for (Layer l : layerList) {
            if (layerName.equals(l.getName())) {
                layer = l;
                break;
            } else if (l.getMetadataURL() != null && l.getMetadataURL().stream().anyMatch(u -> u.getUrl() != null && u.getUrl().toString().equals(layerName))) {
                layer = l;
                break;
            }
        }
        if (layer == null) {
            throw new IOException("Layer " + layerName  + " does not exist");
        }
        LayerInfo info = new LayerInfo();
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
        info.setFormats(capabilities.getRequest().getGetMap().getFormats().toArray(new String[0]));
        info.setWmsVersion(capabilities.getVersion());
        final List<StyleImpl> styles = layer.getStyles();
        if (styles != null) {
            for (StyleImpl style : styles) {
                info.addStyle(style.getName());
            }
        }
        return info;
    }

    public void getMap(String layerName, Map<String, String> parameters, Path destination) throws ServiceException, IOException {
        final WebMapServer service = getService();
        final GetMapRequest mapRequest = service.createGetMapRequest();
        mapRequest.addLayer(layerName, parameters.get("style"));
        mapRequest.setBBox(parameters.get("bbox"));
        mapRequest.setSRS(parameters.get("srs"));
        mapRequest.setFormat(parameters.get("format"));
        mapRequest.setDimensions(parameters.get("width"), parameters.get("height"));
        final GetMapResponse mapResponse = service.issueRequest(mapRequest);
        if (!mapResponse.getContentType().equals("application/xml")) {
            try (InputStream in = mapResponse.getInputStream();
                 OutputStream out = Files.newOutputStream(destination)) {
                FileUtilities.copyStream(in, out);
            }
        } else {
            StringWriter writer = new StringWriter();
            IOUtils.copy(mapResponse.getInputStream(), writer);
            throw new IOException(writer.toString());
        }
    }

    private WebMapServer getService() throws ServiceException, IOException {
        return new WebMapServer(new URL(this.url),
                                new WMSHttpClient(this.authentication),
                                null);
    }
}
