package ro.cs.tao.topology.openstack;

import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.api.storage.ObjectStorageContainerService;
import org.openstack4j.api.storage.ObjectStorageService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Payloads;
import org.openstack4j.model.storage.object.SwiftContainer;
import org.openstack4j.model.storage.object.SwiftObject;
import org.openstack4j.model.storage.object.options.ContainerListOptions;
import org.openstack4j.model.storage.object.options.CreateUpdateContainerOptions;
import org.openstack4j.model.storage.object.options.ObjectListOptions;
import org.openstack4j.model.storage.object.options.ObjectPutOptions;
import ro.cs.tao.security.SessionStore;
import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.topology.TopologyException;
import ro.cs.tao.topology.openstack.commons.Constants;
import ro.cs.tao.topology.openstack.commons.OpenStackSession;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SwiftService {
    private static final String CONTENTS = "contents";
    private final Logger logger;
    private Map<String, String> parameters;
    private int pageLimit = 1000;
    private String folderPlaceholder;

    public SwiftService() {
        this.logger = Logger.getLogger(SwiftService.class.getName());
        this.folderPlaceholder = ".s3keep";
    }

    public void setFolderPlaceholder(String folderPlaceholder) {
        this.folderPlaceholder = folderPlaceholder;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
        if (parameters != null) {
            String limit = parameters.get(Constants.OPENSTACK_PAGE_LIMIT);
            if (limit != null) {
                this.pageLimit = Integer.parseInt(limit);
            }
        }
    }

    public long getContainerSize(String containerName) throws IOException {
        if (StringUtilities.isNullOrEmpty(containerName)) {
            throw new IllegalArgumentException("[containerName] is null or empty");
        }
        final ObjectStorageService objectStorageService = authenticate();
        try {
            final ObjectStorageContainerService containerService = objectStorageService.containers();
            List<? extends SwiftContainer> list = containerService.list(ContainerListOptions.create().path(containerName));
            if (list == null || list.isEmpty()) {
                logger.warning(() -> String.format("Container %s doesn't exist", containerName));
                createBucket(containerName);
            }
            list = containerService.list(ContainerListOptions.create().path(containerName));
            if (list == null || list.isEmpty()) {
                throw new IOException(String.format("Container %s could not be created", containerName));
            }
            return list.get(0).getTotalSize();
        } catch (Exception ex) {
            throw new IOException(String.format("Cannot create container %s. Reason: %s", containerName, ex.getMessage()));
        }
    }

    public Map<String, Long> getContainerSizes() throws IOException {
        final ObjectStorageService objectStorageService = authenticate();
        try {
            final ObjectStorageContainerService containerService = objectStorageService.containers();
            final List<? extends SwiftContainer> list = containerService.list();
            return list.stream().collect(Collectors.toMap(SwiftContainer::getName, SwiftContainer::getTotalSize));
        } catch (Exception ex) {
            throw new IOException(String.format("Cannot list containers. Reason: %s", ex.getMessage()));
        }
    }

    public void createBucket(String containerName) throws IOException {
        if (StringUtilities.isNullOrEmpty(containerName)) {
            throw new IllegalArgumentException("[containerName] is null or empty");
        }
        final ObjectStorageService objectStorageService = authenticate();
        try {
            final ObjectStorageContainerService containerService = objectStorageService.containers();
            final CreateUpdateContainerOptions options = CreateUpdateContainerOptions.create().accessAnybodyRead().accessWrite("*:*");
            final ActionResponse actionResponse = containerService.create(containerName, options);
            if (actionResponse != null && !actionResponse.isSuccess()) {
                throw new IOException(String.format("Failed to create container %s. Response code: %d; fault: %s",
                                                    containerName, actionResponse.getCode(), actionResponse.getFault()));
            } else {
                logger.finest(String.format("Container %s created", containerName));
            }
            final FileObject fakeObject = emptyFolderItem();
            try (ByteArrayInputStream is = new ByteArrayInputStream(fakeObject.getAttributes().get(CONTENTS).getBytes())) {
                final Map<String, String> metadata = new HashMap<>();
                metadata.put("description", "placeholder");
                objectStorageService.objects().put(containerName, fakeObject.getRelativePath(),
                                                   Payloads.create(is),
                                                   ObjectPutOptions.create().metadata(metadata));
            }
        } catch (Exception ex) {
            throw new IOException(String.format("Cannot create container %s. Reason: %s", containerName, ex.getMessage()));
        }
    }

    public String createFolder(String name, String containerName) throws IOException {
        if (name == null) {
            throw new IllegalArgumentException("[name] is null");
        }
        if (Paths.get(name).isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket");
        }
        if (StringUtilities.isNullOrEmpty(containerName)) {
            throw new IllegalArgumentException("[containerName] is null or empty");
        }
        if (!name.endsWith("/")) {
            name += "/";
        }
        final ObjectStorageService objectStorageService = authenticate();
        // Since "folders" do not exist in S3, there has to be a dummy file uploaded in order to create the "folder"
        final FileObject fakeObject = emptyFolderItem();
        try {
            final ObjectStorageContainerService containerService = objectStorageService.containers();
            try {
            final ActionResponse actionResponse = containerService.create(containerName);
            if (actionResponse != null && !actionResponse.isSuccess()) {
                throw new IOException(String.format("Failed to connect to container %s. Response code: %d; fault: %s",
                                                    containerName, actionResponse.getCode(), actionResponse.getFault()));
            } else {
                logger.finest(String.format("Connected to container %s", containerName));
            }
            } catch (IOException e) {
                logger.fine("Newer API errors when trying to create an existing container: " + e.getMessage());
            }
            String path = containerService.createPath(containerName, name);
            if (path == null) {
                try (ByteArrayInputStream is = new ByteArrayInputStream(fakeObject.getAttributes().get(CONTENTS).getBytes())) {
                    final Map<String, String> metadata = new HashMap<>();
                    metadata.put("description", "placeholder");
                    objectStorageService.objects().put(containerName, name + fakeObject.getRelativePath(),
                                                       Payloads.create(is),
                                                       ObjectPutOptions.create().metadata(metadata));
                    path = name;
                }
            }
            return path;
        } catch (Exception ex) {
            throw new IOException(String.format("Cannot create path %s in container. Reason: %s", name, ex.getMessage()));
        }
    }

    public boolean exists(String path, String container) {
        if (path == null) {
            throw new IllegalArgumentException("[path] is null");
        }
        if (StringUtilities.isNullOrEmpty(container)) {
            throw new IllegalArgumentException("[container] is null or empty");
        }
        final ObjectStorageService objectStorageService = authenticate();
        final SwiftObject object = objectStorageService.objects().get(container, path);
        return object != null;
    }

    public List<? extends SwiftObject> list(String path, String container, String lastItem) {
        if (path == null) {
            throw new IllegalArgumentException("[path] is null");
        }
        if (StringUtilities.isNullOrEmpty(container)) {
            throw new IllegalArgumentException("[container] is null or empty");
        }
        final ObjectStorageService objectStorageService = authenticate();
        final ObjectListOptions options = ObjectListOptions.create().path(path);
        if (lastItem != null) {
            options.marker(lastItem);
        }
        options.limit(this.pageLimit);
        final List<? extends SwiftObject> results = new ArrayList<>(objectStorageService.objects().list(container, options));
        List partial;
        // Some OpenStack REST implementation don't return the full list, even if it is less than the limit set.
        // Hence, at the expense of an extra call if no other result, try to retrieve the rest of the list
        if (!results.isEmpty() && (path.isEmpty() || path.equals("/")) && results.size() < this.pageLimit) {
            SwiftObject lastObject = results.get(results.size() - 1);
            if (lastObject.isDirectory()) {
                options.marker((path.isEmpty() ? "" : (path + "/")) + lastObject.getDirectoryName());
            }
            do {
                partial = objectStorageService.objects().list(container, options);
                final String lastName = lastObject.isDirectory() ? lastObject.getDirectoryName() : lastObject.getName();
                if (partial.stream().anyMatch(r -> {
                    SwiftObject s = (SwiftObject) r;
                    return s.isDirectory() ? s.getDirectoryName().equals(lastName) : s.getName().equals(lastName);
                })) {
                    partial.clear();
                } else {
                    results.addAll(partial);
                }
                if (!partial.isEmpty()) {
                    lastObject = (SwiftObject) partial.get(partial.size() - 1);
                    if (lastObject.isDirectory()) {
                        options.marker((path.isEmpty() ? "" : (path + "/")) + lastObject.getDirectoryName());
                    } else {
                        if (path.isEmpty()) {
                            partial.clear();
                        }
                    }
                }
            } while (!partial.isEmpty() && results.size() <= this.pageLimit);
        }
        if (this.folderPlaceholder != null) {
            results.removeIf(r -> {
                if (r.getName() != null) {
                    final int idx = r.getName().lastIndexOf('/');
                    return idx > 0
                           ? r.getName().substring(idx + 1).equals(this.folderPlaceholder)
                           : r.getName().equals(this.folderPlaceholder);
                }
                return false;
            });
        }
        return results;
    }

    public void upload(String filePath, byte[] contents, String container, String description) {
        if (filePath == null) {
            throw new IllegalArgumentException("[file] is null");
        }
        if (StringUtilities.isNullOrEmpty(container)) {
            throw new IllegalArgumentException("[container] is null or empty");
        }
        final ObjectStorageService objectStorageService = authenticate();
        final Map<String, String> metadata = new HashMap<>();
        metadata.put("description", description);
        metadata.put("copied_by", SessionStore.currentContext().getPrincipal().getName());
        String response = objectStorageService.objects().put(container, filePath,
                                                             Payloads.create(new ByteArrayInputStream(contents)),
                                                             ObjectPutOptions.create().metadata(metadata));
        if (response == null) {
            throw new RuntimeException("Upload failed");
        }
    }

    public void upload(String filePath, InputStream stream, String container, String description) {
        if (filePath == null) {
            throw new IllegalArgumentException("[file] is null");
        }
        if (StringUtilities.isNullOrEmpty(container)) {
            throw new IllegalArgumentException("[container] is null or empty");
        }
        final ObjectStorageService objectStorageService = authenticate();
        final Map<String, String> metadata = new HashMap<>();
        metadata.put("description", description);
        metadata.put("copied_by", SessionStore.currentContext().getPrincipal().getName());
        String response = objectStorageService.objects().put(container, filePath,
                                                             Payloads.create(stream),
                                                             ObjectPutOptions.create().metadata(metadata));
        if (response == null) {
            throw new RuntimeException("Upload failed");
        }
    }

    public InputStream download(String file, String container) {
        if (file == null) {
            throw new IllegalArgumentException("[file] is null");
        }
        if (StringUtilities.isNullOrEmpty(container)) {
            throw new IllegalArgumentException("[container] is null or empty");
        }
        final ObjectStorageService objectStorageService = authenticate();
        return objectStorageService.objects().get(container, file).download().getInputStream();
    }

    public String copy(Path source, String pathInContainer) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("[source] is null");
        }
        if (StringUtilities.isNullOrEmpty(pathInContainer)) {
            throw new IllegalArgumentException("[pathInContainer] is null or empty");
        }
        if (!Files.exists(source)) {
            throw new IOException("[source] not found");
        }
        final ObjectStorageService objectStorageService = authenticate();
        final List<Path> paths;
        if (Files.isDirectory(source)) {
            paths = FileUtilities.listTree(source);
        } else {
            paths = new ArrayList<Path>() {{ add(source); }};
        }
        String relativePath;
        String container = this.parameters.get(Constants.OPENSTACK_BUCKET);
        String containerFolder = "";
        if (container == null) {
            try {
                ActionResponse actionResponse = objectStorageService.containers().create(pathInContainer);
                if (actionResponse != null && !actionResponse.isSuccess()) {
                    throw new IOException(String.format("Failed to connect to container %s. Response code: %d; fault: %s",
                                                        pathInContainer, actionResponse.getCode(), actionResponse.getFault()));
                } else {
                    logger.finest(String.format("Connected to container %s", pathInContainer));
                }
                container = pathInContainer;
            } catch (Exception ex) {
                throw new IOException(String.format("Container %s does not exist", pathInContainer));
            }
        } else {
            containerFolder = pathInContainer + "/";
        }
        final List<String> movedPaths = new LinkedList<>();
        String relativeTargetRoot = source.getName(source.getNameCount() - 1).toString();
        for (Path path : paths) {
            try {
                if (!Files.isDirectory(path)) {
                    relativePath = relativeTargetRoot + "/" + source.relativize(path.getParent()).toString();
                    if (relativePath.endsWith("/")) {
                        relativePath = relativePath.substring(0, relativePath.length() - 1);
                    }
                    relativePath = relativePath.replace("\\", "/");
                    logger.finest(String.format("Copying '%s' to '%s'",
                                                path,
                                                relativePath + path.getFileName().toString()));
                    objectStorageService
                            .objects().put(container,
                                           path.getFileName().toString(),
                                           Files.size(path) > 0 ? Payloads.create(Files.newInputStream(path)) : null,
                                           ObjectPutOptions.create().path(containerFolder + relativePath));
                    movedPaths.add(relativePath);
                }
            } catch (Exception inner) {
                logger.severe(String.format("Uploading '%s' failed: %s", path, inner.getMessage()));
                for (String relPath : movedPaths) {
                    try {
                        objectStorageService.objects().delete(pathInContainer, relPath);
                    } catch (Exception ex) {
                        logger.warning(String.format("Removing '%s' failed: %s", relPath, ex.getMessage()));
                    }
                }
                throw new IOException(inner);
            }
        }
        return Files.isDirectory(source) ? relativeTargetRoot : movedPaths.get(0);
    }

    public String move(Path source, String pathInContainer) throws IOException {
        final String targetPath = copy(source, pathInContainer);
        try (Stream<Path> stream = Files.walk(source, FileVisitOption.FOLLOW_LINKS)) {
            stream.sorted(Comparator.reverseOrder())
                  .map(Path::toFile)
                  .forEach(File::delete);
        }
        return targetPath;
    }

    public int delete(String containerName, String filter) throws IOException {
        if (StringUtilities.isNullOrEmpty(containerName)) {
            throw new IllegalArgumentException("[containerName] is null or empty");
        }
        final ObjectStorageService objectStorageService = authenticate();
        int deleted = 0;
        try {
            final ObjectListOptions options = !StringUtilities.isNullOrEmpty(filter) ?
                    ObjectListOptions.create().startsWith(filter) : null;
            final List<? extends SwiftObject> objects = options != null ?
                    objectStorageService.objects().list(containerName, options):
                    objectStorageService.objects().list(containerName);
            for (SwiftObject object : objects) {
                try {
                    objectStorageService.objects().delete(containerName, object.getName());
                    deleted++;
                } catch (Exception ex) {
                    logger.severe(String.format("Deleting '%s' failed: %s", object.getName(), ex.getMessage()));
                }
            }
        } catch (Exception ex) {
            throw new IOException(ex);
        }
        return deleted;
    }

    private ObjectStorageService authenticate() {
        try {
            return parameters == null || parameters.size() == 1
                   ? OpenStackSession.objectStorageService()
                   : OpenStackSession.objectStorageService(parameters);
        } catch (AuthenticationException ex) {
            throw new TopologyException(String.format("OpenStack authentication failed. Reason: %s", ex.getMessage()));
        }
    }

    private FileObject emptyFolderItem() {
        final FileObject object = new FileObject("", this.folderPlaceholder, false, 0, this.folderPlaceholder);
        object.addAttribute(CONTENTS, ".");
        return object;
    }
}
