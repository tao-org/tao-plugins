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

/**
 * SwiftService is a utility service for interacting with an object storage system.
 * It provides functionality to manage containers (buckets), folders, and files,
 * including uploading, downloading, listing, and deleting objects.
 * This service also supports operations like checking if a file exists,
 * moving or copying files, and fetching container sizes.
 *
 * Fields:
 * - CONTENTS: Placeholder for the internal structure of the file contents.
 * - logger: Logger instance used for logging operations and errors.
 * - parameters: Map containing configuration parameters for the SwiftService.
 * - pageLimit: Integer defining the maximum number of items per page during listings.
 * - folderPlaceholder: String used as a placeholder to represent empty folders.
 *
 * Methods:
 * - SwiftService(): Constructor that initializes a new instance of SwiftService.
 * - setFolderPlaceholder(String folderPlaceholder): Sets the placeholder for empty folders.
 * - getParameters(): Retrieves the configuration parameters for the service.
 * - setParameters(Map<String, String> parameters): Sets the configuration parameters for the service.
 * - getContainerSize(String containerName) throws IOException: Returns the total size (in bytes) of a container.
 * - getContainerSizes() throws IOException: Retrieves the sizes (in bytes) for all available containers.
 * - createBucket(String containerName) throws IOException: Creates a new container (bucket) with the specified name.
 * - createFolder(String name, String containerName) throws IOException: Creates a folder within a specified container.
 * - exists(String path, String container): Checks if a file or folder exists in the specified path and container.
 * - list(String path, String container, String lastItem): Lists the objects within a specific path and container.
 * - upload(String filePath, byte[] contents, String container, String description): Uploads a file using its byte content to the specified container with an optional description
 * .
 * - upload(String filePath, InputStream stream, String container, String description): Uploads a file using an InputStream to the specified container with an optional description
 * .
 * - download(String file, String container): Downloads a file from the specified container and returns it as an InputStream.
 * - copy(Path source, String pathInContainer) throws IOException: Copies a file to the specified path in a container.
 * - move(Path source, String pathInContainer) throws IOException: Moves a file to the specified path in a container.
 * - delete(String containerName, String filter) throws IOException: Deletes files or objects from a specified container based on a filter.
 * - authenticate(): Authenticates with the object storage service. (Private)
 * - emptyFolderItem(): Returns a placeholder object representing an empty folder. (Private)
 */
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

    /**
     * Sets the folder placeholder value for use within the service.
     *
     * @param folderPlaceholder the placeholder value representing a folder
     */
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

    /**
     * Retrieves the total size of a specified container in the object storage.
     * If the specified container does not exist, it will attempt to create it.
     * If the container still cannot be created, an exception is thrown.
     *
     * @param containerName the name of the container whose size is to be retrieved; must not be null or empty
     * @return the total size of the specified container in bytes
     * @throws IllegalArgumentException if the container name is null or empty
     * @throws IOException if an I/O error occurs during the operation, or if the container could not be created
     */
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

    /**
     * Retrieves the sizes of all available containers in the object storage service.
     * Each container is represented as a key-value pair where the key is the container's
     * name, and the value is the total size of the container in bytes.
     *
     * @return a map containing the names of the containers as keys and their corresponding sizes in bytes as values
     * @throws IOException if an error occurs during container listing or authentication
     */
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

    /**
     * Creates a bucket (container) in the object storage service with the specified name.
     * If the bucket creation fails or an exception occurs, an IOException is thrown.
     *
     * @param containerName the name of the bucket to be created; must not be null or empty
     * @throws IllegalArgumentException if the container name is null or empty
     * @throws IOException if an I/O error occurs during the bucket creation process
     */
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

    /**
     * Creates a folder within the specified container in the object storage service.
     * This is achieved by uploading a placeholder object to mimic folder creation
     * as folders do not inherently exist in object storage systems.
     *
     * @param name the relative path of the folder to be created. Must not be null and must be relative to the container.
     *             If it does not end with a "/", it will be automatically appended to the path.
     * @param containerName the name of the container in which the folder is to be created.
     *                      Must not be null or empty.
     * @return the relative path of the created folder.
     * @throws IllegalArgumentException if the folder name is null, if the folder path is absolute,
     *                                  or if the container name is null or empty.
     * @throws IOException if an error occurs during folder creation or when interacting with the container service.
     */
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

    /**
     * Checks if an object exists in the specified container in the object storage service.
     *
     * @param path the relative path of the object to check; must not be null.
     * @param container the name of the container where the object resides; must not be null or empty.
     * @return true if the object exists in the specified container; false otherwise.
     * @throws IllegalArgumentException if the path is null or the container is null or empty.
     */
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

    /**
     * Retrieves a list of objects from a specified container, filtered by a directory path and optional marker
     * for pagination. Implements pagination and ensures that incomplete results are fully retrieved where
     * possible. Removes placeholder objects representing folders if a placeholder value has been set.
     *
     * @param path the directory path to filter the objects; must not be null. An empty or root path ("")
     *             will list all objects in the container.
     * @param container the name of the container to query; must not be null or empty.
     * @param lastItem an optional marker to begin pagination from; can be null. Useful for iterating over
     *                 large datasets.
     * @return a list of objects matching the specified parameters from the container. The returned list
     *         excludes folder placeholder entries if such a placeholder is defined.
     * @throws IllegalArgumentException if the {@code path} is null, or if the {@code container} is null
     *                                  or empty.
     */
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

    /**
     * Uploads a file to the specified container in the object storage service.
     * The uploaded file will include metadata such as a description and the name
     * of the user who initiated the upload. If the upload fails, an exception is thrown.
     *
     * @param filePath the path of the file to be uploaded; must not be null.
     * @param contents the byte array representing the file contents.
     * @param container the name of the container where the file will be uploaded; must not be null or empty.
     * @param description a description for the file to be uploaded.
     * @throws IllegalArgumentException if {@code filePath} is null, or if {@code container} is null or empty.
     * @throws RuntimeException if the upload operation fails.
     */
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

    /**
     * Uploads a file to the specified container in the object storage service using an InputStream.
     * The uploaded file will include metadata such as a description and the name
     * of the user who initiated the upload. If the upload fails, an exception is thrown.
     *
     * @param filePath the path of the file to be uploaded; must not be null.
     * @param stream the InputStream containing the file contents to be uploaded.
     * @param container the name of the container where the file will be uploaded; must not be null or empty.
     * @param description a description for the file to be uploaded.
     * @throws IllegalArgumentException if {@code filePath} is null, or if {@code container} is null or empty.
     * @throws RuntimeException if the upload operation fails.
     */
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

    /**
     * Downloads a file from the specified container in the object storage service.
     *
     * @param file the name of the file to download; must not be null.
     * @param container the name of the container from where the file is to be downloaded; must not be null or empty.
     * @return an InputStream that allows reading the contents of the downloaded file.
     * @throws IllegalArgumentException if the {@code file} is null or if {@code container} is null or empty.
     */
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

    /**
     * Copies the specified file or directory to an object storage container.
     * If the source is a directory, the method recursively copies all its contents.
     *
     * @param source the path of the file or directory to be copied; must not be null
     * @param pathInContainer the name of the target container or the path within the container; must not be null or empty
     * @return the relative path of the copied file or the root directory of copied files
     * @throws IOException if an I/O error occurs, if the source does not exist, or if the copy operation fails
     * @throws IllegalArgumentException if the source is null or if pathInContainer is null or empty
     */
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

    /**
     * Moves a file or directory to a specified path within an object storage container.
     * First, the source file or directory is copied to the target location using the {@code copy} method,
     * and then the original source is deleted recursively.
     *
     * @param source the path of the file or directory to be moved; must not be null
     * @param pathInContainer the target path within the container where the file or directory should be moved; must not be null or empty
     * @return the relative path of the moved file or the root directory of moved files within the container
     * @throws IOException if an I/O error occurs during the operation or if the source does not exist
     * @throws IllegalArgumentException if the source*/
    public String move(Path source, String pathInContainer) throws IOException {
        final String targetPath = copy(source, pathInContainer);
        try (Stream<Path> stream = Files.walk(source, FileVisitOption.FOLLOW_LINKS)) {
            stream.sorted(Comparator.reverseOrder())
                  .map(Path::toFile)
                  .forEach(File::delete);
        }
        return targetPath;
    }

    /**
     * Deletes objects in a specified container, optionally filtered by a prefix.
     *
     * @param containerName the name of the container from which objects are to be deleted.
     *                      Must not be null or empty.
     * @param filter        an optional prefix used to filter objects to delete.
     *                      If null or empty, all objects in the container are deleted.
     * @return the number of objects successfully deleted from the container.
     * @throws IllegalArgumentException if the containerName is null or empty.
     * @throws IOException              if an error occurs during the deletion process.
     */
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

    /**
     * Authenticates and returns an ObjectStorageService instance.
     * The method determines the appropriate authentication logic based on the presence
     * and size of the parameters. If the parameters are null or contain only one element,
     * it uses a default configuration. Otherwise, it uses the provided parameters for authentication.
     * In case of authentication failure, an exception is thrown with a relevant error message.
     *
     * @return an instance of ObjectStorageService after successful authentication.
     * @throws TopologyException if OpenStack authentication fails.
     */
    private ObjectStorageService authenticate() {
        try {
            return parameters == null || parameters.size() == 1
                   ? OpenStackSession.objectStorageService()
                   : OpenStackSession.objectStorageService(parameters);
        } catch (AuthenticationException ex) {
            throw new TopologyException(String.format("OpenStack authentication failed. Reason: %s", ex.getMessage()));
        }
    }

    /**
     * Creates and returns a FileObject representing an empty folder item with
     * predefined attributes and properties.
     *
     * @return a FileObject instance representing an empty folder item
     */
    private FileObject emptyFolderItem() {
        final FileObject object = new FileObject("", this.folderPlaceholder, false, 0, this.folderPlaceholder);
        object.addAttribute(CONTENTS, ".");
        return object;
    }
}
