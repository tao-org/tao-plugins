package ro.cs.tao.aws;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import ro.cs.tao.security.SessionStore;
import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.services.storage.BaseStorageService;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.workspaces.Repository;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class S3StorageService extends BaseStorageService<byte[], S3ObjectInputStream> {
    private static final String PROTOCOL = "s3";
    public static final String AWS_REGION = "aws.region";
    public static final String AWS_ACCESS_KEY = "aws.access.key";
    public static final String AWS_SECRET_KEY = "aws.secret.key";
    public static final String AWS_BUCKET = "aws.bucket";
    public static final String AWS_PAGE_LIMIT = "page.limit";
    private String region;
    private String accessKey;
    private String secretKey;
    private AmazonS3 client;
    private int pageLimit = 1000;
    private final Logger logger = Logger.getLogger(S3StorageService.class.getName());

    public S3StorageService() {
        super();
    }

    @Override
    public boolean isIntendedFor(String protocol) {
        return PROTOCOL.equalsIgnoreCase(protocol);
    }

    @Override
    public void associate(Repository repository) {
        super.associate(repository);
        final LinkedHashMap<String, String> parameters = repository.getParameters();
        if (parameters == null) {
            throw new IllegalArgumentException("[parameters] Cannot be null");
        }
        String value = parameters.get(AWS_REGION);
        if (StringUtilities.isNullOrEmpty(value)) {
            throw new IllegalArgumentException("[parameters] '" + AWS_REGION + "' cannot be empty");
        }
        this.region = value;
        value = parameters.get(AWS_ACCESS_KEY);
        if (StringUtilities.isNullOrEmpty(value) || value.trim().isEmpty()) {
            //throw new IllegalArgumentException("[parameters] " + AWS_ACCESS_KEY + " cannot be empty");
            value = null;
        }
        this.accessKey = value;
        value = parameters.get(AWS_SECRET_KEY);
        if (StringUtilities.isNullOrEmpty(value) || value.trim().isEmpty()) {
            //throw new IllegalArgumentException("[parameters] " + AWS_SECRET_KEY + " cannot be empty");
            value = null;
        }
        this.secretKey = value;
        value = parameters.get(AWS_BUCKET);
        if (StringUtilities.isNullOrEmpty(value)) {
            throw new IllegalArgumentException("[parameters] " + AWS_BUCKET + " cannot be empty");
        }
        value = parameters.get(AWS_PAGE_LIMIT);
        if (!StringUtilities.isNullOrEmpty(value)) {
            this.pageLimit = Integer.parseInt(value);
        }
    }

    @Override
    public Path createFolder(String folderRelativePath, boolean userOnly) throws IOException {
        Repository repository = repository();
        if (!folderRelativePath.endsWith("/")) {
            folderRelativePath += "/";
        }
        Path path = Paths.get(repository.resolve(folderRelativePath));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket (including the bucket name)");
        }
        // Since "folders" do not exist in S3, there has to be a dummy file uploaded in order to create the "folder"
        final FileObject fakeObject = emptyFolderItem();
        path = path.resolve(fakeObject.getRelativePath());
        if (this.client == null) {
            this.client = buildClient();
        }
        final String bucket = repository.bucketName();
        final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/");
        final String contents = fakeObject.getAttributes().get(CONTENTS_ATTRIBUTE);
        try (ByteArrayInputStream is = new ByteArrayInputStream(contents.getBytes())) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contents.length());
            PutObjectRequest request = new PutObjectRequest(bucket, relativePath, is, metadata);
            request.setStorageClass(StorageClass.Standard);
            request.setCannedAcl(CannedAccessControlList.BucketOwnerFullControl);
            this.client.putObject(request);
            return path;
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void storeUserFile(byte[] object, String relativeFolder, String description) throws Exception {
        if (object == null || object.length == 0) {
            throw new IllegalArgumentException("[object] Content cannot be null or zero");
        }
        Repository repository = repository();
        final Path path = Paths.get(repository.resolve(relativeFolder));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[relativeFolder] Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        final String bucket = repository.bucketName();
        final String relativePath = repository.relativizeToBucket(relativeFolder);
        try (InputStream is = new ByteArrayInputStream(object)) {
            ensureBucketExists(bucket);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.addUserMetadata("description", description);
            metadata.addUserMetadata("copied_by", SessionStore.currentContext().getPrincipal().getName());
            metadata.setContentLength(object.length);
            PutObjectRequest request = new PutObjectRequest(bucket, relativePath, is, metadata);
            request.setStorageClass(StorageClass.Standard);
            request.setCannedAcl(CannedAccessControlList.BucketOwnerFullControl);
            request.withGeneralProgressListener(new ProgressListener(this.progressListener, description, object.length));
            this.client.putObject(request);
        } catch (AmazonServiceException e) {
            throw new IOException(e);
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
        if (this.client == null) {
            this.client = buildClient();
        }
        final String bucket = repository.bucketName();
        final String relativePath = repository.relativizeToBucket(relativeFolder);
        try {
            ensureBucketExists(bucket);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.addUserMetadata("description", description);
            metadata.addUserMetadata("copied_by", SessionStore.currentContext().getPrincipal().getName());
            if (length > 0) {
                metadata.setContentLength(length);
            }
            PutObjectRequest request = new PutObjectRequest(bucket, relativePath, stream, metadata);
            request.setStorageClass(StorageClass.Standard);
            request.setCannedAcl(CannedAccessControlList.BucketOwnerFullControl);
            if (length > 0) {
                request.withGeneralProgressListener(new ProgressListener(this.progressListener, description, length));
            }
            this.client.putObject(request);
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean exists(String path) throws Exception {
        Repository repository = repository();
        final Path filePath = Paths.get(repository.resolve(path));
        if (filePath.isAbsolute()) {
            throw new IllegalArgumentException("Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        try {
            final String bucket = repository.bucketName();
            final String relativePath = repository.relativizeToBucket(path);
            GetObjectRequest request = new GetObjectRequest(bucket, relativePath);
            final S3Object object = this.client.getObject(request);
            if (object != null) {
                object.close();
                return true;
            }
        } catch (AmazonServiceException e) {
            return false;
        }
        return false;
    }

    @Override
    public void remove(String name) throws IOException {
        Repository repository = repository();
        final Path path = Paths.get(repository.resolve(name));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        final String bucket = repository.bucketName();
        final String relativePath = repository.relativizeToBucket(name);
        try {
            if (name.endsWith("/")) {
                final List<FileObject> children = new ArrayList<>();
                traverse(bucket, relativePath, children, true);
                String childPath;
                for (FileObject child : children) {
                    if (!child.isFolder()) {
                        childPath = repository.relativizeToBucket(child.getRelativePath());
                        this.client.deleteObject(bucket, childPath);
                        logger.finest("Deleted " + childPath);
                    }
                }
            }
            this.client.deleteObject(bucket, relativePath);
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void move(String source, String destination) throws IOException {
        Repository repository = repository();
        final Path sourcePath = Paths.get(repository.resolve(source));
        if (sourcePath.isAbsolute()) {
            throw new IllegalArgumentException("[source] Path should be relative to the S3 bucket (including the bucket name)");
        }
        final Path destPath = Paths.get(repository.resolve(destination));
        if (destPath.isAbsolute()) {
            throw new IllegalArgumentException("[destination] Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        final String bucket = repository.bucketName();
        try {
            final String srcPath = repository.relativizeToBucket(source);
            final String dstPath = repository.relativizeToBucket(destination);;
            CopyObjectRequest request = new CopyObjectRequest(bucket, srcPath, bucket, dstPath);
            request.setStorageClass(StorageClass.Standard);
            request.setCannedAccessControlList(CannedAccessControlList.BucketOwnerFullControl);
            ObjectMetadata newMetadata = new ObjectMetadata();
            newMetadata.addUserMetadata("old_location", source);
            newMetadata.addUserMetadata("copied_by", SessionStore.currentContext().getPrincipal().getName());
            request.setNewObjectMetadata(newMetadata);
            request.withGeneralProgressListener(e -> {
                if (S3StorageService.this.progressListener != null) {
                    S3StorageService.this.progressListener.notifyProgress((double) e.getBytesTransferred() / (double) e.getBytes());
                }
            });
            this.client.copyObject(request);
            remove(source);
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<FileObject> listUserWorkspace() throws IOException {
        if (this.client == null) {
            this.client = buildClient();
        }
        try {
            final String bucketName = repository().bucketName();
            ensureBucketExists(bucketName);
            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(bucketName);
            request.setDelimiter("/");
            request.setMarker("/");
            request.setMaxKeys(this.pageLimit);
            List<FileObject> list = list(request);
            final FileObject root = list.stream().filter(f -> StringUtilities.isNullOrEmpty(f.getRelativePath())).findFirst().orElse(null);
            if (root == null) {
                list.add(0, repositoryRootNode(repository()));
            } else {
                root.setRelativePath("/");
                root.setDisplayName(ROOT_TITLE);
            }
            return list;
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<FileObject> listFiles(String fromPath, Set<String> exclusions, String lastItem, int depth) throws IOException {
        if (this.client == null) {
            this.client = buildClient();
        }
        Repository repository = repository();
        try {
            ListObjectsRequest request = new ListObjectsRequest();
            final String bucket = repository.bucketName();
            ensureBucketExists(bucket);
            request.setBucketName(bucket);
            request.setDelimiter("/");
            if (exclusions != null) {
                final String excPath = exclusions.iterator().next();
                request.setMarker(repository.relativizeToBucket(excPath) + "/");
            }
            String relativePath = repository.relativizeToBucket(fromPath);
            if (!relativePath.isEmpty() && !relativePath.endsWith("/")) {
                relativePath += "/";
            }
            request.setPrefix(relativePath);
            if (lastItem != null) {
                request.setMarker(repository.relativizeToBucket(lastItem));
            }
            request.setMaxKeys(this.pageLimit);
            List<FileObject> list = exclusions == null ? list(request) : singlePage(request);
            if (repository.isRoot(fromPath)) {
                final FileObject root = list.stream().filter(f -> StringUtilities.isNullOrEmpty(f.getRelativePath())).findFirst().orElse(null);
                if (root == null) {
                    list.add(0, repositoryRootNode(repository()));
                } else {
                    root.setRelativePath("/");
                    root.setDisplayName(ROOT_TITLE);
                }
            }
            return list;
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<FileObject> listTree(String fromPath) throws IOException {
        if (this.client == null) {
            this.client = buildClient();
        }
        Repository repository = repository();
        try {
            final List<FileObject> files = new ArrayList<>();
            if (repository.isRoot(fromPath)) {
                files.add(repositoryRootNode(repository));
            }
            traverse(repository.bucketName(), repository.relativizeToBucket(fromPath), files, false);
            return files;
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<FileObject> getWorkflowResults(long workflowId) throws IOException {
        throw new UnsupportedOperationException("Not implemented for AWS S3");
    }

    @Override
    public List<FileObject> getJobResults(long jobId) throws IOException {
        throw new UnsupportedOperationException("Not implemented for AWS S3");
    }

    @Override
    public S3ObjectInputStream download(String strPath) throws IOException {
        Path path = Paths.get(strPath);
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        try {
            final String bucket = path.getName(0).toString();
            final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/");
            GetObjectRequest request = new GetObjectRequest(bucket, relativePath);
            final ProgressListener listener = new ProgressListener(this.progressListener, relativePath);
            request.withGeneralProgressListener(listener);
            final S3Object object = this.client.getObject(request);
            listener.setTotal(object.getObjectMetadata().getContentLength());
            return object.getObjectContent();
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void streamToZip(String rootPath, ZipOutputStream stream) throws IOException {
        Path root = Paths.get(rootPath);
        if (root.isAbsolute()) {
            throw new IllegalArgumentException("Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        final String bucket = root.getName(0).toString();
        final List<FileObject> keys = new ArrayList<>();
        traverse(bucket,
                 root.getNameCount() > 1
                         ? root.subpath(1, root.getNameCount()).toString().replace("\\", "/")
                         : root.toString(), keys, false);
        ZipEntry entry;
        final double total = keys.stream().mapToDouble(FileObject::getSize).sum();
        for (FileObject current : keys) {
            final double size = current.getSize();
            final Path currentPath = Paths.get(current.getRelativePath());
            if (!current.isFolder()) {
                final String relativePath = currentPath.subpath(1, currentPath.getNameCount()).toString().replace("\\", "/");
                GetObjectRequest request = new GetObjectRequest(bucket, relativePath);
                final S3Object object = this.client.getObject(request);
                if (isNotFolderPlaceholder(object)) {
                    String zipPath = root.relativize(currentPath).toString();
                    entry = new ZipEntry(zipPath);
                    entry.setSize(current.getSize());
                    entry.setTime(System.currentTimeMillis());
                    stream.putNextEntry(entry);
                    FileUtilities.appendToStream(object.getObjectContent(), stream);
                    stream.closeEntry();
                }
                object.close();
            } else {
                if (!currentPath.equals(root)) {
                    String zipPath = root.relativize(currentPath) + File.separator;
                    entry = new ZipEntry(zipPath);
                    stream.putNextEntry(entry);
                    stream.closeEntry();
                }
            }
            if (this.progressListener != null) {
                this.progressListener.notifyProgress(root.toString(), size / total);
            }
        }
    }

    @Override
    public String readAsText(S3ObjectInputStream resource, int lines, int skipLines) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource));
             Stream<String> stream = reader.lines()) {
            return stream.skip(skipLines).limit(lines).collect(Collectors.joining());
        }
    }

    @Override
    public S3StorageService clone() throws CloneNotSupportedException {
        final S3StorageService clone = new S3StorageService();
        clone.region = this.region;
        clone.accessKey = this.accessKey;
        clone.secretKey = this.secretKey;
        return clone;
    }

    private AmazonS3 buildClient() {
        if (this.region == null) { // || this.accessKey == null || this.secretKey == null) {
            throw new IllegalArgumentException("S3 client not configured");
        }
        return this.accessKey != null && this.secretKey != null
                ? AmazonS3ClientBuilder.standard()
                                        .withRegion(Regions.fromName(this.region))
                                        .withCredentials(new AWSStaticCredentialsProvider(
                                            new BasicAWSCredentials(this.accessKey, this.secretKey)))
                                        .build()
                : AmazonS3ClientBuilder.standard()
                                        .withRegion(Regions.fromName(this.region))
                                        .build();
    }

    private void ensureBucketExists(String name) {
        if (StringUtilities.isNullOrEmpty(name)) {
            throw new IllegalArgumentException("[name] Bucket name cannot be empty");
        } else if (name.length() < 3 || name.length() > 63) {
            throw new IllegalArgumentException("[name] Bucket name length must be between 3 and 63 characters");
        } else if (name.contains("_") || name.contains("..") || name.contains("-.")) {
            throw new IllegalArgumentException("[name] Bucket name contains unsupported characters");
        } else if (name.endsWith("/")) {
            throw new IllegalArgumentException("[name] Bucket name should not end with a dash");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        if (!this.client.doesBucketExistV2(name)) {
            CreateBucketRequest request = new CreateBucketRequest(name);
            request.setCannedAcl(CannedAccessControlList.PublicRead);
            this.client.createBucket(name);
        }
    }

    private List<FileObject> list(ListObjectsRequest request) {
        final List<FileObject> files = new ArrayList<>();
        ObjectListing objects = this.client.listObjects(request);
        Repository repository = repository();
        String prefix = repository.getUrlPrefix();
        String bucketName = repository.bucketName();
        String filter = repository.root().replaceFirst(bucketName, "");
        if (filter.startsWith("/")) {
            filter = filter.substring(1);
        }
        final int offset = filter.length();
        if (objects != null) {
            for (String commonPrefix : objects.getCommonPrefixes()) {
                if (!commonPrefix.equals(repository.getUserName() + "/")) {
                    final FileObject fileObject = new FileObject(PROTOCOL, repository.relativizeToRoot(commonPrefix.substring(offset)), true, 0);
                    fileObject.addAttribute(REMOTE_PATH_ATTRIBUTE, prefix + "://" + repository.resolve(fileObject.getRelativePath()));
                    files.add(fileObject);
                }
            }
            if (objects.getObjectSummaries() != null) {
                while (true) {
                    for (S3ObjectSummary summary : objects.getObjectSummaries()) {
                        if (isNotFolderPlaceholder(summary)) {
                            final FileObject fileObject = new FileObject(PROTOCOL, repository.relativizeToRoot(summary.getKey().substring(offset)), false, summary.getSize());
                            if (summary.getLastModified() != null) {
                                fileObject.setLastModified(Timestamp.from(summary.getLastModified().toInstant()).toLocalDateTime());
                            }
                            fileObject.addAttribute(REMOTE_PATH_ATTRIBUTE, prefix + "://" + repository.resolve(fileObject.getRelativePath()));
                            files.add(fileObject);
                        }
                    }
                    if (!objects.isTruncated()) {
                        break;
                    }
                    objects = this.client.listNextBatchOfObjects(objects);
                }
            }
        }
        return files;
    }

    private List<FileObject> singlePage(ListObjectsRequest request) {
        ObjectListing objects = this.client.listObjects(request);
        final List<FileObject> results = new ArrayList<>();
        Repository repository = repository();
        String prefix = repository.getUrlPrefix();
        String bucketName = repository.bucketName();
        String filter = repository.root().replaceFirst(bucketName, "");
        if (filter.startsWith("/")) {
            filter = filter.substring(1);
        }
        final int offset = filter.length();
        if (objects != null) {
            for (String commonPrefix : objects.getCommonPrefixes()) {
                final FileObject fileObject = new FileObject(PROTOCOL, repository.relativizeToRoot(commonPrefix.substring(offset)), true, 0);
                fileObject.addAttribute(REMOTE_PATH_ATTRIBUTE, prefix + "://" + repository.resolve(fileObject.getRelativePath()));
                results.add(fileObject);
            }
            final List<S3ObjectSummary> objectSummaries = objects.getObjectSummaries();
            if (objectSummaries != null) {
                for (int i = 0; i < objectSummaries.size(); i++) {
                    final S3ObjectSummary summary = objectSummaries.get(i);
                    if (isNotFolderPlaceholder(summary)) {
                        final FileObject fileObject = new FileObject(PROTOCOL, repository.relativizeToRoot(summary.getKey().substring(offset)), false, summary.getSize());
                        if (summary.getLastModified() != null) {
                            fileObject.setLastModified(Timestamp.from(summary.getLastModified().toInstant()).toLocalDateTime());
                        }
                        fileObject.addAttribute(REMOTE_PATH_ATTRIBUTE, prefix + "://" + repository.resolve(fileObject.getRelativePath()));
                        if (objects.isTruncated() && i == objectSummaries.size() - 1) {
                            fileObject.addAttribute("nextMarker", objects.getNextMarker());
                        }
                        results.add(fileObject);
                    }
                }
            }
        }
        return results;
    }

    private void traverse(String bucketName, String fromPath, List<FileObject> files, boolean includeHidden) {
        ListObjectsRequest request = new ListObjectsRequest();
        request.setBucketName(bucketName);
        request.setDelimiter("/");
        request.setPrefix(fromPath);
        Repository repository = repository();
        String filter = repository.root().replaceFirst(bucketName, "");
        if (filter.startsWith("/")) {
            filter = filter.substring(1);
        }
        final int offset = filter.length();
        ObjectListing objects = this.client.listObjects(request);
        if (objects != null) {
            for (String commonPrefix : objects.getCommonPrefixes()) {
                final FileObject folder = new FileObject(PROTOCOL, repository.relativizeToRoot(commonPrefix.substring(offset)), true, 0);
                folder.addAttribute(REMOTE_PATH_ATTRIBUTE, PROTOCOL + "://" + repository.resolve(folder.getRelativePath()));
                files.add(folder);
                traverse(bucketName, commonPrefix, files, includeHidden);
            }
            if (objects.getObjectSummaries() != null) {
                while (true) {
                    for (S3ObjectSummary summary : objects.getObjectSummaries()) {
                        if (includeHidden || isNotFolderPlaceholder(summary)) {
                            FileObject file = new FileObject(PROTOCOL, repository.relativizeToRoot(summary.getKey().substring(offset)), false, summary.getSize());
                            file.addAttribute(REMOTE_PATH_ATTRIBUTE, PROTOCOL + "://" + repository.resolve(file.getRelativePath()));
                            files.add(file);
                        }
                    }
                    if (!objects.isTruncated()) {
                        break;
                    }
                    objects = this.client.listNextBatchOfObjects(objects);
                }
            }
        }
    }

    private boolean isNotFolderPlaceholder(S3ObjectSummary summary) {
        return !summary.getKey().endsWith(AVLFOLDER);
    }

    private boolean isNotFolderPlaceholder(S3Object object) {
        return !object.getKey().endsWith(AVLFOLDER);
    }

    private static class ProgressListener implements com.amazonaws.event.ProgressListener {

        private final ro.cs.tao.utils.executors.monitoring.ProgressListener parent;
        private final String task;
        private double current;
        private double total;

        ProgressListener(ro.cs.tao.utils.executors.monitoring.ProgressListener parent, String task) {
            this.parent = parent;
            this.task = task;
            this.current = 0.0;
        }

        ProgressListener(ro.cs.tao.utils.executors.monitoring.ProgressListener parent, String task, double total) {
            this.parent = parent;
            this.task = task;
            this.total = total;
            this.current = 0.0;
        }

        public void setTotal(double total) {
            this.total = total;
        }

        @Override
        public void progressChanged(ProgressEvent progressEvent) {
            if (this.parent != null && Double.compare(total, 0.0) != 0) {
                current += (double) progressEvent.getBytesTransferred();
                this.parent.notifyProgress(task, current / total);
            }
        }
    }
}
