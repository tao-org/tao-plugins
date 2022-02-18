package ro.cs.tao.topology.openstack;

import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.api.storage.ObjectStorageContainerService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Payloads;
import org.openstack4j.model.storage.object.SwiftObject;
import org.openstack4j.model.storage.object.options.ObjectListOptions;
import org.openstack4j.model.storage.object.options.ObjectPutOptions;
import ro.cs.tao.security.SessionStore;
import ro.cs.tao.topology.TopologyException;
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

public class SwiftService {
    private final Logger logger;
    private org.openstack4j.api.storage.ObjectStorageService objectStorageService;

    public SwiftService() {
        this.logger = Logger.getLogger(SwiftService.class.getName());
    }

    public String createFolder(String name, String containerName) throws IOException {
        if (name == null) {
            throw new IllegalArgumentException("[name] is null");
        }
        if (!Paths.get(name).isAbsolute()) {
            throw new IllegalArgumentException("[name] must be an absolute path");
        }
        if (StringUtilities.isNullOrEmpty(containerName)) {
            throw new IllegalArgumentException("[containerName] is null or empty");
        }
        authenticate();
        try {
            final ObjectStorageContainerService containerService = this.objectStorageService.containers();
            final ActionResponse actionResponse = containerService.create(containerName);
            if (actionResponse != null && !actionResponse.isSuccess()) {
                throw new IOException(String.format("Failed to connect to container %s. Response code: %d; fault: %s",
                                                    containerName, actionResponse.getCode(), actionResponse.getFault()));
            } else {
                logger.finest(String.format("Connected to container %s", containerName));
            }
            return containerService.createPath(containerName, name);
        } catch (Exception ex) {
            throw new IOException(String.format("Cannot create path %s in container. Reason: %s", name, ex.getMessage()));
        }
    }

    public List<? extends SwiftObject> list(String path, String container) {
        if (path == null) {
            throw new IllegalArgumentException("[path] is null");
        }
        if (StringUtilities.isNullOrEmpty(container)) {
            throw new IllegalArgumentException("[container] is null or empty");
        }
        authenticate();
        return this.objectStorageService.objects().list(container, ObjectListOptions.create().path(path));
    }

    public void upload(String filePath, byte[] contents, String container, String description) {
        if (filePath == null) {
            throw new IllegalArgumentException("[file] is null");
        }
        if (StringUtilities.isNullOrEmpty(container)) {
            throw new IllegalArgumentException("[container] is null or empty");
        }
        authenticate();
        final Map<String, String> metadata = new HashMap<>();
        metadata.put("description", description);
        metadata.put("copied_by", SessionStore.currentContext().getPrincipal().getName());
        this.objectStorageService.objects().put(container, filePath,
                                                Payloads.create(new ByteArrayInputStream(contents)),
                                                ObjectPutOptions.create().metadata(metadata));
    }

    public InputStream download(String file, String container) {
        if (file == null) {
            throw new IllegalArgumentException("[file] is null");
        }
        if (StringUtilities.isNullOrEmpty(container)) {
            throw new IllegalArgumentException("[container] is null or empty");
        }
        authenticate();
        return this.objectStorageService.objects().get(container, file).download().getInputStream();
    }

    public String copy(Path source, String containerName) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("[source] is null");
        }
        if (StringUtilities.isNullOrEmpty(containerName)) {
            throw new IllegalArgumentException("[containerName] is null or empty");
        }
        if (!Files.exists(source)) {
            throw new IOException("[source] not found");
        }
        authenticate();
        final List<Path> paths;
        if (Files.isDirectory(source)) {
            paths = FileUtilities.listTree(source);
        } else {
            paths = new ArrayList<Path>() {{ add(source); }};
        }
        String relativePath;
        try {
            ActionResponse actionResponse = this.objectStorageService.containers().create(containerName);
            if (actionResponse != null && !actionResponse.isSuccess()) {
                throw new IOException(String.format("Failed to connect to container %s. Response code: %d; fault: %s",
                                                    containerName, actionResponse.getCode(), actionResponse.getFault()));
            } else {
                logger.finest(String.format("Connected to container %s", containerName));
            }
        } catch (Exception ex) {
            throw new IOException(String.format("Container %s does not exist", containerName));
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
                    logger.fine(String.format("Copying '%s' to '%s'",
                                              path.toString(),
                                              relativePath + path.getFileName().toString()));
                    this.objectStorageService
                            .objects().put(containerName,
                                           path.getFileName().toString(),
                                           Files.size(path) > 0 ? Payloads.create(Files.newInputStream(path)) : null,
                                           ObjectPutOptions.create().path(relativePath));
                    movedPaths.add(relativePath);
                }
            } catch (Exception inner) {
                logger.severe(String.format("Uploading '%s' failed: %s", path, inner.getMessage()));
                for (String relPath : movedPaths) {
                    try {
                        this.objectStorageService.objects().delete(containerName, relPath);
                    } catch (Exception ex) {
                        logger.warning(String.format("Removing '%s' failed: %s", relPath, ex.getMessage()));
                    }
                }
                throw new IOException(inner);
            }
        }
        return Files.isDirectory(source) ? relativeTargetRoot : movedPaths.get(0);
    }

    public String move(Path source, String containerName) throws IOException {
        final String targetPath = copy(source, containerName);
        Files.walk(source, FileVisitOption.FOLLOW_LINKS)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        return targetPath;
    }

    public int delete(String containerName, String filter) throws IOException {
        if (StringUtilities.isNullOrEmpty(containerName)) {
            throw new IllegalArgumentException("[containerName] is null or empty");
        }
        authenticate();
        int deleted = 0;
        try {
            final ObjectListOptions options = !StringUtilities.isNullOrEmpty(filter) ?
                    ObjectListOptions.create().startsWith(filter) : null;
            final List<? extends SwiftObject> objects = options != null ?
                    this.objectStorageService.objects().list(containerName, options):
                    this.objectStorageService.objects().list(containerName);
            for (SwiftObject object : objects) {
                try {
                    this.objectStorageService.objects().delete(containerName, object.getName());
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

    private void authenticate() {
        if (this.objectStorageService == null) {
            try {
                this.objectStorageService = OpenStackSession.objectStorageService();
            } catch (AuthenticationException ex) {
                throw new TopologyException(String.format("OpenStack authentication failed. Reason: %s", ex.getMessage()));
            }
        }
    }
}
