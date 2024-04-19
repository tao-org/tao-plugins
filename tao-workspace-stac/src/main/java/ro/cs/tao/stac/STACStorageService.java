package ro.cs.tao.stac;

import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.services.storage.BaseStorageService;
import ro.cs.tao.stac.core.STACClient;
import ro.cs.tao.stac.core.model.Collection;
import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.model.extensions.ExtensionType;
import ro.cs.tao.stac.core.model.extensions.projection.ProjExtension;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.workspaces.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for accessing in a repository-like fashion the items of a STAC service.
 * Since the items of a STAC search/list are not hierarchically grouped,
 * we have the following internal convention for representing the relative path of an item:
 *      /                         root of the service (translated to a catalog query)
 *      /collection               first level of nodes consist in collections of the catalog
 *      /collection/item          second level of nodes consist in item names of a collection; this would be an EOProduct
 *      /collection/item/asset    last level of nodes consist in item assets (actual files)
 *
 * @author  Cosmin Cara
 * @since   1.3.1
 */
public class STACStorageService extends BaseStorageService<byte[], InputStream> {
    private static final String PROTOCOL = "stac";
    private Repository repository;

    @Override
    public boolean isIntendedFor(String protocol) {
        return PROTOCOL.equalsIgnoreCase(protocol);
    }

    @Override
    public void associate(Repository repository) {
        if (!isIntendedFor(repository.getType().prefix())) {
            throw new IllegalArgumentException("Wrong repository type");
        }
        this.repository = repository;
    }

    @Override
    public Path createFolder(String relativePath, boolean userOnly) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void storeUserFile(byte[] object, String relativeFolder, String description) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void storeFile(InputStream stream, long length, String relativeFolder, String description) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean exists(String path) throws Exception {
        return false;
    }

    @Override
    public void remove(String path) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void move(String source, String destination) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FileObject> listFiles(String fromPath, Set<String> exclusions, String lastItem, int depth) throws IOException {
        final STACClient client = new STACClient(this.repository.root(), null);
        final List<FileObject> files = new ArrayList<>();
        files.add(repositoryRootNode(repository));
        if (StringUtilities.isNullOrEmpty(fromPath) || "/".equals(fromPath)) {
            CollectionList collectionList = client.listCollections();
            List<Collection> collections = collectionList.getCollections();
            if (collections != null) {
                for (Collection collection : collections) {
                    files.add(toFileObjects(collection));
                }
            }
        } else {
            final String[] tokens = fromPath.split("/");
            switch (tokens.length) {
                case 1: // collection
                    // On lastItem we expect the total items returned so far
                    LinkedHashMap<String, String> parameters = this.repository.getParameters();
                    int pageSize = Integer.parseInt(parameters.getOrDefault("page.size", "50"));
                    int page = lastItem == null ? 1 : (Integer.parseInt(lastItem) / pageSize) + 1;
                    ItemCollection itemCollection = client.listItems(tokens[0], page, pageSize);
                    files.addAll(itemCollection.getFeatures().stream().map(i -> toFileObject(tokens[0], i)).collect(Collectors.toList()));
                    break;
                default: // item
                    Item item = client.getItem(String.join("/", Arrays.copyOfRange(tokens, 0, tokens.length - 1)), tokens[tokens.length - 1]);
                    Map<String, Asset> assets = item.getAssets();
                    if (assets != null) {
                        for (Asset asset : assets.values()) {
                            files.add(toFileObject(tokens[0], item, asset));
                        }
                    }
                    break;
            }
        }
        return files;
    }

    @Override
    public List<FileObject> listTree(String fromPath) throws IOException {
        return listFiles(fromPath, null, null, 1);
    }

    @Override
    public List<FileObject> getWorkflowResults(long workflowId) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FileObject> getJobResults(long jobId) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream download(String path) throws IOException {
        if (StringUtilities.isNullOrEmpty(path)) {
            throw new IllegalArgumentException("[path] null");
        }
        final STACClient client = new STACClient(this.repository.root(), null);
        InputStream stream = null;
        if (path.startsWith("http")) {
            return client.download(path);
        } else {
            final String[] tokens = path.split("/");
            if (tokens.length != 3) {
                throw new IllegalArgumentException("Only individual assets can be downloaded like this");
            }
            final Item item = client.getItem(tokens[0], tokens[1]);
            Asset asset = item.getAssets().values().stream().filter(a -> tokens[2].equals(a.getTitle())).findFirst().orElse(null);
            if (asset == null) {
                throw new IOException("No such asset");
            }
            return client.download(asset);
        }
    }

    @Override
    public void streamToZip(String path, ZipOutputStream stream) throws IOException {
        if (StringUtilities.isNullOrEmpty(path)) {
            throw new IllegalArgumentException("[path] null");
        }
        final String[] tokens = path.split("/");
        if (tokens.length != 3) {
            throw new IllegalArgumentException("Only individual assets can be downloaded like this");
        }
        final STACClient client = new STACClient(this.repository.root(), null);
        final Item item = client.getItem(tokens[0], tokens[1]);
        java.util.Collection<Asset> assets = item.getAssets().values();
        ZipEntry entry;
        String href;
        for (Asset current : assets) {
            final InputStream inputStream = client.download(current);
            href = current.getHref();
            String zipPath = tokens[1] + href.substring(href.lastIndexOf('/'));
            entry = new ZipEntry(zipPath);
            entry.setSize(inputStream.available());
            entry.setTime(System.currentTimeMillis());
            stream.putNextEntry(entry);
            FileUtilities.appendToStream(inputStream, stream);
            stream.closeEntry();
        }
    }

    @Override
    public String readAsText(InputStream resource, int lines, int skipLines) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource));
             Stream<String> stream = reader.lines()) {
            return stream.skip(skipLines).limit(lines).collect(Collectors.joining());
        }
    }

    private FileObject toFileObjects(Collection collection) {
        final FileObject file = new FileObject();
        file.setDisplayName(collection.getTitle());
        file.setProtocol(PROTOCOL);
        file.setFolder(true);
        file.setRelativePath(collection.getId() + "/");
        file.addAttribute("id", collection.getId());
        file.addAttribute("type", collection.getType());
        file.addAttribute("description", collection.getDescription());
        file.addAttribute("license", collection.getLicense());
        LocalDateTime[] interval = collection.getExtent().getTemporal().getInterval();
        String value;
        if (interval != null) {
            value = "From " + (interval[0] != null ? interval[0].format(DateTimeFormatter.ISO_DATE) : "n/a")
                            + " to " + (interval[1] != null ? interval[1].format(DateTimeFormatter.ISO_DATE) : "n/a");
            file.addAttribute("temporalExtent", value);
        }
        SpatialExtent spatial = collection.getExtent().getSpatial();
        if (spatial != null && spatial.getBbox() != null) {
            file.addAttribute("spatialExtent", Arrays.stream(spatial.getBbox()).mapToObj(String::valueOf).collect(Collectors.joining(",")));
        }
        String itemsHref = collection.getLinks().stream().filter(l -> "items".equals(l.getRel())).map(Link::getHref).collect(Collectors.joining());
        file.addAttribute("remotePath", itemsHref.substring(0, itemsHref.lastIndexOf('/')));
        return file;
    }

    private FileObject toFileObject(String collectionName, Item feature) {
        final FileObject fileObject = new FileObject();
        final String title = feature.getField("title");
        fileObject.setDisplayName(title != null ? title : feature.getId());
        fileObject.setFolder(feature.getAssets() != null && !feature.getAssets().isEmpty());
        fileObject.setProtocol(PROTOCOL);
        fileObject.setProductName(fileObject.getDisplayName());
        fileObject.setLastModified(feature.getDatetime());
        fileObject.setRelativePath(collectionName + "/" + feature.getId() + "/");
        Map<String, Object> properties = feature.getFields();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().getClass().isArray()) {
                fileObject.addAttribute(entry.getKey(), toString(entry.getValue()));
            }
        }
        Geometry<?> geometry = feature.getGeometry();
        if (geometry != null) {
            StringBuilder builder = new StringBuilder();
            builder.append(geometry.getType().name().toUpperCase());
            Object coords = geometry.getCoordinates();
            switch (geometry.getType()) {
                case Polygon:
                    builder.append("((");
                    double[][][] p = (double[][][]) coords;
                    for (double[][] doubles : p) {
                        for (double[] aDouble : doubles) {
                            builder.append(aDouble[0]).append(" ").append(aDouble[1]).append(",");
                        }
                    }
                    builder.setLength(builder.length() - 1);
                    builder.append("))");
                    break;
                case MultiPolygon:
                    builder.append("(");
                    double[][][][] mp = (double[][][][]) coords;
                    for (double[][][] polygon : mp) {
                        builder.append("((");
                        for (double[][] line : polygon) {
                            for (double[] point : line) {
                                builder.append(point[0]).append(" ").append(point[1]).append(",");
                            }
                        }
                        builder.setLength(builder.length() - 1);
                        builder.append("))");
                    }
                    builder.append(")");
                    break;
            }
            fileObject.addAttribute("footprint", builder.toString());
        }
        double[] bbox = feature.getBbox();
        if (bbox != null) {
            fileObject.addAttribute("bbox", Arrays.stream(bbox).mapToObj(String::valueOf).collect(Collectors.joining(",")));
        }
        Map<ExtensionType, Extension<?>> extensions = feature.getExtensions();
        if (extensions != null) {
            ProjExtension<?> extension = (ProjExtension<?>) extensions.get(ExtensionType.PROJ);
            if (extension != null) {
                fileObject.addAttribute("projection", "EPSG:" + extension.getEpsg());
                double[] shape = extension.getShape();
                if (shape != null) {
                    fileObject.addAttribute("width", String.valueOf(shape[0]));
                    fileObject.addAttribute("height", String.valueOf(shape[1]));
                }
            }

        }
        return fileObject;
    }

    private FileObject toFileObject(String collectionName, Item feature, Asset asset) {
        final FileObject fileObject = new FileObject();
        String href = asset.getHref().substring(asset.getHref().lastIndexOf('/') + 1);
        fileObject.setDisplayName(href);
        fileObject.setFolder(false);
        fileObject.setProtocol(PROTOCOL);
        fileObject.setProductName(feature.getField("title"));
        fileObject.setRelativePath(collectionName + "/" + feature.getId() + "/" + href);
        fileObject.addAttribute("name", asset.getTitle());
        fileObject.addAttribute("type", asset.getType());
        fileObject.addAttribute("description", asset.getDescription());
        fileObject.addAttribute("remotePath", asset.getHref());
        Map<String, Object> fields = asset.getFields();
        if (fields != null) {
            fields.forEach((key, value) -> fileObject.addAttribute(key, toString(value)));
        }
        return fileObject;
    }

    private String toString(Object value) {
        String strVal = "";
        if (value != null) {
            if (value.getClass().isArray()) {
                String[] arr = new String[Array.getLength(value)];
                strVal = "[";
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = Array.get(value, i).toString();
                }
                strVal += String.join(",", arr) + "]";
            }
        }
        return strVal;
    }
}
