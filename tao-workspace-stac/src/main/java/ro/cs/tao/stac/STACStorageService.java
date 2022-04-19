package ro.cs.tao.stac;

import ro.cs.tao.services.interfaces.StorageService;
import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.stac.client.STACClient;
import ro.cs.tao.stac.model.*;
import ro.cs.tao.workspaces.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

public class STACStorageService implements StorageService<byte[], InputStream> {
    private static final String PROTOCOL = "stac";
    private static final Set<String> filteredLinks = new HashSet<String>() {{
        add("self"); add("search"); add("root");
    }};
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
    public List<FileObject> listFiles(String fromPath, Set<String> exclusions, String lastItem) throws IOException {
        final List<FileObject> files = new ArrayList<>();
        final STACClient client = new STACClient(this.repository.bucketName());
        // First we try to see if the link points to a catalog
        Catalogs catalogs = client.listCollections(this.repository.isRoot(fromPath) ? null : fromPath);
        if (catalogs != null) {
            List<Link> links = catalogs.getLinks();
            links.removeIf(l -> filteredLinks.contains(l.getRel()));
            files.addAll(links.stream().map(this::toFileObject).collect(Collectors.toList()));
        } else {
            // The link is a catalog and should have only items
            List<Feature> features = client.listItems(this.repository.resolve(fromPath));
            files.addAll(features.stream().map(this::toFileObject).collect(Collectors.toList()));
        }
        return files;
    }

    @Override
    public List<FileObject> listTree(String fromPath) throws IOException {
        throw new UnsupportedEncodingException();
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
    public InputStream download(Path path) throws IOException {
        return null;
    }

    @Override
    public void streamToZip(Path zipRoot, ZipOutputStream stream) throws IOException {

    }

    private FileObject toFileObject(Link link) {
        final FileObject fileObject = new FileObject();
        fileObject.setDisplayName(link.getTitle() != null ? link.getTitle() : link.getHref());
        fileObject.setFolder(true);
        fileObject.setProtocol("stac");
        fileObject.setRelativePath(link.getHref().replace(this.repository.root(), ""));
        fileObject.addAttribute("description", link.getDescription());
        return fileObject;
    }

    private FileObject toFileObject(Feature feature) {
        final FileObject fileObject = new FileObject();
        Map<String, Object> properties = feature.getProperties().getAdditionalProperties();
        fileObject.setDisplayName(properties.get("title").toString());
        fileObject.setFolder(feature.getAssets() == null);
        fileObject.setProtocol("stac");
        fileObject.setProductName(fileObject.getDisplayName());
        fileObject.setRelativePath(feature.getLinks().stream().filter(l -> l.getRel().equals("self")).map(Link::getHref).findFirst().get().replace(this.repository.root(), ""));
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!entry.getValue().getClass().isArray()) {
                fileObject.addAttribute(entry.getKey(), entry.getValue().toString());
            }
        }
        return fileObject;
    }

    private FileObject toFileObject(Feature feature, Asset asset) {
        final FileObject fileObject = new FileObject();
        Classification classification = asset.getClassification();
        String href = classification.getHref().substring(classification.getHref().lastIndexOf('/') + 1);
        fileObject.setDisplayName(href);
        fileObject.setFolder(false);
        fileObject.setProtocol("stac");
        if (href.lastIndexOf('.') > 0) {
            href = href.substring(0, href.lastIndexOf('.'));
        }
        fileObject.setProductName(href);
        Geometry geometry = feature.getGeometry();
        if (geometry != null) {
            StringBuilder builder = new StringBuilder();
            builder.append(geometry.getType().toUpperCase());
            List<List<List<Float>>> coordinates = geometry.getCoordinates();
            final boolean isMultiPolygon = coordinates.get(0).size() > 1;
            if (isMultiPolygon) {
                builder.append("(");
            }
            for (List<List<Float>> polygons : coordinates) {
                builder.append("((");
                for (List<Float> points : polygons) {
                    builder.append(points.get(0)).append(" ").append(points.get(1)).append(",");
                }
                builder.setLength(builder.length() - 1);
                builder.append("))");
                if (isMultiPolygon) {
                    builder.append(",");
                }
            }
            builder.setLength(builder.length() - 1);
            if (isMultiPolygon) {
                builder.append(")");
            }
            fileObject.addAttribute("footprint", builder.toString());
        }
        fileObject.setRelativePath(feature.getLinks().stream().filter(l -> l.getRel().equals("self")).map(Link::getHref).findFirst().get().replace(this.repository.root(), "") + "/" + classification.getTitle());
        fileObject.addAttribute("type", classification.getType());
        fileObject.addAttribute("href", classification.getHref());
        if (classification.getEoBands() != null) {
            int bandIndex = 1;
            for (EOBand eoBand : classification.getEoBands()) {
                fileObject.addAttribute("band " + bandIndex++, eoBand.getName());
            }
        }
        fileObject.addAttribute("projection", "EPSG" + classification.getProjEpsg());
        List<Float> bbox = feature.getBbox();
        if (bbox != null) {
            fileObject.addAttribute("bbox", bbox.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        List<Integer> shape = classification.getProjShape();
        if (shape != null) {
            fileObject.addAttribute("width", shape.get(0).toString());
            fileObject.addAttribute("height", shape.get(1).toString());
        }
        return fileObject;
    }
}
