package ro.cs.tao.topology.openstack;

import org.openstack4j.model.storage.object.SwiftObject;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.services.storage.BaseStorageService;
import ro.cs.tao.topology.openstack.commons.Constants;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.workspaces.Repository;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ObjectStorageService extends BaseStorageService<byte[], InputStream> {
    private static final String PROTOCOL = "swift";

    private final SwiftService swiftService;

    public ObjectStorageService() {
        super();
        this.swiftService = new SwiftService();
        this.swiftService.setFolderPlaceholder(FOLDER_PLACEHOLDER);
    }

    @Override
    public boolean isIntendedFor(String protocol) {
        return PROTOCOL.equalsIgnoreCase(protocol);
    }

    @Override
    public void associate(Repository repository) {
        super.associate(repository);
        final LinkedHashMap<String, String> parameters = repository().getParameters();
        final ConfigurationProvider configurationProvider = ConfigurationManager.getInstance();
        // If swift buckets are in another tenant, the configuration should override the main openstack one
        final Map<String, String> values = configurationProvider.getValues(Constants.OPENSTACK_SWIFT_FILTER);
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                parameters.put(entry.getKey().replace(".swift", ""), entry.getValue());
            }
        }
        swiftService.setParameters(parameters);
    }

    @Override
    public void createRoot(String root) throws IOException {
        swiftService.createBucket(root);
    }

    @Override
    public Path createFolder(String folderRelativePath, boolean userOnly) throws IOException {
        final Repository repository = repository();
        final Path path = Paths.get(repository.resolve(folderRelativePath));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket");
        }
        final String bucket = repository.bucketName();
        final String relativePath = repository.relativizeToBucket(folderRelativePath);
        return Paths.get(this.swiftService.createFolder(relativePath, bucket));
    }

    @Override
    public void storeUserFile(byte[] object, String relativeFolder, String description) throws Exception {
        if (object == null || object.length == 0) {
            throw new IllegalArgumentException("[object] Content cannot be null or zero");
        }
        final Repository repository = repository();
        Path path = Paths.get(repository.resolve(relativeFolder));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[relativeFolder] Path should be relative to the S3 bucket (including the bucket name)");
        }
        final String bucket = repository.bucketName();
        final String relativePath = repository.relativizeToBucket(relativeFolder);
        try (InputStream toPass = wrapStream(new ByteArrayInputStream(object))) {
            this.swiftService.upload(relativePath, toPass, bucket, description);
        }
    }

    @Override
    public void storeFile(InputStream stream, long length, String relativeFolder, String description) throws Exception {
        if (stream == null) {
            throw new IllegalArgumentException("[stream] Null stream");
        }
        Repository repository = repository();
        final Path path = Paths.get(repository.resolve(relativeFolder));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[relativeFolder] Path should be relative to the S3 bucket (including the bucket name)");
        }
        final String bucket = repository.bucketName();
        final String relativePath = repository.relativizeToBucket(relativeFolder);
        try (InputStream toPass = wrapStream(stream)) {
            this.swiftService.upload(relativePath, toPass, bucket, description);
        }
    }

    @Override
    public boolean exists(String path) throws Exception {
        Repository repository = repository();
        final Path filePath = Paths.get(repository.resolve(path));
        if (filePath.isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket (including the bucket name)");
        }
        final String bucket = repository.bucketName();
        final String relativePath = repository.relativizeToBucket(path);
        return this.swiftService.exists(relativePath, bucket);
    }

    @Override
    public void remove(String name) throws IOException {
        Repository repository = repository();
        final Path path = Paths.get(repository.resolve(name));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket (including the bucket name)");
        }
        final String bucket = repository.bucketName();
        final String relativePath = repository.relativizeToBucket(name);
        this.swiftService.delete(bucket, relativePath);
    }

    @Override
    public void move(String source, String destination) throws IOException {
        throw new UnsupportedOperationException("Not implemented for OpenStack Swift");
    }

    @Override
    public List<FileObject> listUserWorkspace() throws IOException {
        List<FileObject> list = list("/", null, null);
        list.add(0, repositoryRootNode(repository()));
        return list;
    }

    @Override
    public List<FileObject> listFiles(String fromPath, Set<String> exclusions, String lastItem, int depth) throws IOException {
        List<FileObject> list = list(repository().resolve(fromPath), exclusions, lastItem);
        if (repository().isRoot(fromPath)) {
            list.add(0, repositoryRootNode(repository()));
        }
        return list;
    }

    @Override
    public List<FileObject> listTree(String fromPath) throws IOException {
        final List<FileObject> results = new ArrayList<>();
        if (repository().isRoot(fromPath)) {
            results.add(repositoryRootNode(repository()));
        }
        traverse(repository().resolve(fromPath), null, results);
        return results;
    }

    @Override
    public List<FileObject> getWorkflowResults(long workflowId) throws IOException {
        throw new UnsupportedOperationException("Not implemented for OpenStack Swift");
    }

    @Override
    public List<FileObject> getJobResults(long jobId) throws IOException {
        throw new UnsupportedOperationException("Not implemented for OpenStack Swift");
    }

    @Override
    public InputStream download(String strPath) throws IOException {
        Path path = Paths.get(strPath);
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("Path should be relative to the S3 bucket (including the bucket name)");
        }
        final String bucket = path.getName(0).toString();
        final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/");
        return this.swiftService.download(relativePath, bucket);
    }

    @Override
    public void streamToZip(String rootPath, ZipOutputStream stream) throws IOException {
        Path root = Paths.get(rootPath);
        if (root.isAbsolute()) {
            throw new IllegalArgumentException("Path should be relative to the S3 bucket");
        }
        Repository repository = repository();
        final String bucket = repository.bucketName();
        final List<FileObject> keys = new ArrayList<>();
        traverse(root.toString(), null, keys);
        ZipEntry entry;
        for (FileObject current : keys) {
            final Path currentPath = Paths.get(current.getRelativePath());
            if (!current.isFolder()) {
                final String relativePath = currentPath.toString().replace("\\", "/");
                final InputStream inputStream = this.swiftService.download(relativePath, bucket);
                String zipPath = root.relativize(currentPath).toString();
                entry = new ZipEntry(zipPath);
                entry.setSize(current.getSize());
                entry.setTime(System.currentTimeMillis());
                stream.putNextEntry(entry);
                FileUtilities.appendToStream(inputStream, stream);
                stream.closeEntry();
            } else {
                if (!currentPath.equals(root)) {
                    String zipPath = root.relativize(currentPath) + File.separator;
                    entry = new ZipEntry(zipPath);
                    stream.putNextEntry(entry);
                    stream.closeEntry();
                }
            }
        }
    }

    @Override
    public String readAsText(InputStream resource, int lines, int skipLines) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource));
             Stream<String> stream = reader.lines()) {
            return stream.skip(skipLines).limit(lines).collect(Collectors.joining());
        }
    }

    @Override
    public String computeHash(String path) throws IOException, NoSuchAlgorithmException {
        final MessageDigest md5Digest = MessageDigest.getInstance("MD5");
        traverseHash(path, md5Digest);
        return StringUtilities.byteArrayToHexString(md5Digest.digest());
    }

    private List<FileObject> list(String folderPath, Set<String> exclusions, String lastItem) {
        final Path path = Paths.get(folderPath);
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket (including the bucket name)");
        }
        Repository repository = repository();
        final String bucket = repository.bucketName();
        final String relativePath = repository.relativizeToBucket(folderPath);
        String prefix = repository().getUrlPrefix();
        final List<FileObject> files = new ArrayList<>();
        final List<? extends SwiftObject> objects = this.swiftService.list(relativePath, bucket, lastItem);
        if (objects != null) {
            objects.forEach(object -> {
                //final String oPath = bucket + "/" + (object.isDirectory() ? object.getDirectoryName() : object.getName());
                final String oPath = (object.isDirectory() ? object.getDirectoryName() : object.getName());
                if (exclusions == null || exclusions.stream().noneMatch(oPath::startsWith)) {
                    final FileObject fileObject = new FileObject(PROTOCOL, repository.relativizeToRoot(oPath), object.isDirectory(), object.getSizeInBytes());
                    if (object.getLastModified() != null) {
                        fileObject.setLastModified(Timestamp.from(object.getLastModified().toInstant()).toLocalDateTime());
                    }
                    fileObject.addAttribute(REMOTE_PATH_ATTRIBUTE, prefix + "://" + repository.resolve(fileObject.getRelativePath()));
                    files.add(fileObject);
                }
            });
        }
        return files;
    }

    private void traverse(String fromPath, Set<String> exclusions, List<FileObject> files) {
        Repository repository = repository();
        final String bucket = repository.bucketName();
        final String relativePath = repository.relativizeToBucket(fromPath);
        String prefix = repository.getUrlPrefix();
        List<? extends SwiftObject> objects = this.swiftService.list(relativePath, bucket, null);
        if (objects != null) {
            for (SwiftObject object : objects) {
                final String oPath = (object.isDirectory() ? object.getDirectoryName() : object.getName());
                if (exclusions == null || exclusions.stream().noneMatch(oPath::startsWith)) {
                    final FileObject fileObject = new FileObject(PROTOCOL, repository.relativizeToRoot(oPath), object.isDirectory(), object.getSizeInBytes());
                    if (object.getLastModified() != null) {
                        fileObject.setLastModified(Timestamp.from(object.getLastModified().toInstant()).toLocalDateTime());
                    }
                    fileObject.addAttribute(REMOTE_PATH_ATTRIBUTE, prefix + "://" + repository.resolve(fileObject.getRelativePath()));
                    files.add(fileObject);
                    if (object.isDirectory()) {
                        traverse(bucket + "/" + oPath, exclusions, files);
                    }
                }
            }
        }
    }

    private void traverseHash(String fromPath, MessageDigest digest) {
        Repository repository = repository();
        final String bucket = repository.bucketName();
        final String relativePath = repository.relativizeToBucket(fromPath);
        String prefix = repository.getUrlPrefix();
        List<? extends SwiftObject> objects = this.swiftService.list(relativePath, bucket, null);
        if (objects != null) {
            for (SwiftObject object : objects) {
                if (object.isDirectory()) {
                    traverseHash(bucket + "/" + object.getDirectoryName(), digest);
                } else {
                    digest.update(StringUtilities.hexStringToByteArray(object.getETag()));
                }
            }
        }
    }
}
