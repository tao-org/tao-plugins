package ro.cs.tao.aws;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import ro.cs.tao.security.SessionStore;
import ro.cs.tao.services.interfaces.StorageService;
import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class S3StorageService implements StorageService<byte[], S3ObjectInputStream> {
    private static final String PROTOCOL = "s3";
    public static final String AWS_REGION = "aws.region";
    public static final String AWS_ACCESS_KEY = "aws.access.key";
    public static final String AWS_SECRET_KEY = "aws.secret.key";
    public static final String AWS_BUCKET = "aws.bucket";
    public static final String AWS_PAGE_LIMIT = "page.limit";
    private String region;
    private String accessKey;
    private String secretKey;
    private String defaultBucket;
    private AmazonS3 client;
    private int pageLimit = 1000;

    public S3StorageService() {
    }

    @Override
    public boolean isIntendedFor(String protocol) {
        return PROTOCOL.equalsIgnoreCase(protocol);
    }

    @Override
    public void setup(Map<String, String> parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("[parameters] Cannot be null");
        }
        String value = parameters.get(AWS_REGION);
        if (StringUtilities.isNullOrEmpty(value)) {
            throw new IllegalArgumentException("[parameters] '" + AWS_REGION + "' cannot be empty");
        }
        this.region = value;
        value = parameters.get(AWS_ACCESS_KEY);
        if (StringUtilities.isNullOrEmpty(value)) {
            throw new IllegalArgumentException("[parameters] " + AWS_ACCESS_KEY + " cannot be empty");
        }
        this.accessKey = value;
        value = parameters.get(AWS_SECRET_KEY);
        if (StringUtilities.isNullOrEmpty(value)) {
            throw new IllegalArgumentException("[parameters] " + AWS_SECRET_KEY + " cannot be empty");
        }
        this.secretKey = value;
        value = parameters.get(AWS_BUCKET);
        if (StringUtilities.isNullOrEmpty(value)) {
            throw new IllegalArgumentException("[parameters] " + AWS_BUCKET + " cannot be empty");
        }
        this.defaultBucket = value;
        value = parameters.get(AWS_PAGE_LIMIT);
        if (!StringUtilities.isNullOrEmpty(value)) {
            this.pageLimit = Integer.parseInt(value);
        }
    }

    @Override
    public Path createFolder(String folderRelativePath, boolean userOnly) throws IOException {
        if (!folderRelativePath.endsWith("/")) {
            folderRelativePath += "/";
        }
        final Path path = Paths.get(folderRelativePath);
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        final String bucket = path.getName(0).toString();
        final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/");
        try {
            PutObjectRequest request = new PutObjectRequest(bucket, relativePath, (String) null);
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
        final Path path = Paths.get(relativeFolder);
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[relativeFolder] Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        final String bucket = path.getName(0).toString();
        final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/");
        try (InputStream is = new ByteArrayInputStream(object)) {
            ensureBucketExists(bucket);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.addUserMetadata("description", description);
            metadata.addUserMetadata("copied_by", SessionStore.currentContext().getPrincipal().getName());
            metadata.setContentLength(object.length);
            PutObjectRequest request = new PutObjectRequest(bucket, relativePath, is, metadata);
            request.setStorageClass(StorageClass.Standard);
            request.setCannedAcl(CannedAccessControlList.BucketOwnerFullControl);
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
        final Path path = Paths.get(relativeFolder);
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[relativeFolder] Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        final String bucket = path.getName(0).toString();
        final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/");
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
            this.client.putObject(request);
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void storePublicFile(byte[] object, String relativeFolder, String description) throws Exception {
        storeUserFile(object, relativeFolder, description);
    }

    @Override
    public void remove(String name) throws IOException {
        final Path path = Paths.get(name);
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        final String bucket = path.getName(0).toString();
        final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/");
        try {
            this.client.deleteObject(bucket, relativePath);
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void move(String source, String destination) throws IOException {
        final Path sourcePath = Paths.get(source);
        if (sourcePath.isAbsolute()) {
            throw new IllegalArgumentException("[source] Path should be relative to the S3 bucket (including the bucket name)");
        }
        final Path destPath = Paths.get(destination);
        if (destPath.isAbsolute()) {
            throw new IllegalArgumentException("[destination] Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        final String sourceBucket = sourcePath.getName(0).toString();
        final String destBucket = destPath.getName(0).toString();
        try {
            ensureBucketExists(destBucket);
            final String sourceRelativePath = sourcePath.subpath(1, sourcePath.getNameCount()).toString().replace("\\", "/");
            final String destRelativePath = destPath.subpath(1, destPath.getNameCount()).toString().replace("\\", "/");
            CopyObjectRequest request = new CopyObjectRequest(sourceBucket, sourceRelativePath, destBucket, destRelativePath);
            request.setStorageClass(StorageClass.Standard);
            request.setCannedAccessControlList(CannedAccessControlList.BucketOwnerFullControl);
            ObjectMetadata newMetadata = new ObjectMetadata();
            newMetadata.addUserMetadata("old_location", source);
            newMetadata.addUserMetadata("copied_by", SessionStore.currentContext().getPrincipal().getName());
            request.setNewObjectMetadata(newMetadata);
            this.client.copyObject(request);
            remove(source);
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<FileObject> listUploaded() throws IOException {
        return null;
    }

    @Override
    public List<FileObject> listPublicWorkspace() throws IOException {
        return null;
    }

    @Override
    public List<FileObject> listUploaded(String userName) throws IOException {
        return listUserWorkspace(userName);
    }

    @Override
    public List<FileObject> listUserWorkspace(String userName) throws IOException {
        if (this.client == null) {
            this.client = buildClient();
        }
        try {
            final String bucketName = this.defaultBucket != null ? this.defaultBucket : userName;
            ensureBucketExists(bucketName);
            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(bucketName);
            request.setDelimiter("/");
            request.setMarker("/");
            request.setMaxKeys(this.pageLimit);
            return list(request);
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<FileObject> listFiles(Path fromPath, Set<Path> exclusions, Path lastItem) throws IOException {
        if (fromPath.isAbsolute()) {
            throw new IllegalArgumentException("Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        try {
            ListObjectsRequest request = new ListObjectsRequest();
            final String bucket = fromPath.getName(0).toString();
            ensureBucketExists(bucket);
            request.setBucketName(bucket);
            request.setDelimiter("/");
            if (exclusions != null) {
                final Path markerPath = exclusions.iterator().next();
                request.setMarker(markerPath.subpath(1, markerPath.getNameCount()).toString().replace("\\", "/") + "/");
            }
            final String relativePath = fromPath.getNameCount() > 1
                    ? fromPath.subpath(1, fromPath.getNameCount()).toString().replace("\\", "/") + "/"
                    : "";
            request.setPrefix(relativePath);
            if (lastItem != null) {
                request.setMarker(lastItem.subpath(1, lastItem.getNameCount()).toString().replace("\\", "/") + "/");
            }
            request.setMaxKeys(this.pageLimit);
            return exclusions == null ? list(request) : singlePage(request);
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<FileObject> listTree(Path fromPath) throws IOException {
        if (fromPath.isAbsolute()) {
            throw new IllegalArgumentException("Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        try {
            final List<FileObject> files = new ArrayList<>();
            traverse(fromPath.toString(), files);
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
    public S3ObjectInputStream download(Path path) throws IOException {
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
            final S3Object object = this.client.getObject(request);
            return object.getObjectContent();
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void streamToZip(Path root, ZipOutputStream stream) throws IOException {
        if (root.isAbsolute()) {
            throw new IllegalArgumentException("Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        final String bucket = root.getName(0).toString();
        final List<FileObject> keys = new ArrayList<>();
        traverse(root.toString(), keys);
        ZipEntry entry;
        for (FileObject current : keys) {
            final Path currentPath = Paths.get(current.getRelativePath());
            if (!current.isFolder()) {
                final String relativePath = currentPath.subpath(1, currentPath.getNameCount()).toString().replace("\\", "/");
                GetObjectRequest request = new GetObjectRequest(bucket, relativePath);
                final S3Object object = this.client.getObject(request);
                String zipPath = root.relativize(currentPath).toString();
                entry = new ZipEntry(zipPath);
                entry.setSize(current.getSize());
                entry.setTime(System.currentTimeMillis());
                stream.putNextEntry(entry);
                FileUtilities.appendToStream(object.getObjectContent(), stream);
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
    public S3StorageService clone() throws CloneNotSupportedException {
        final S3StorageService clone = new S3StorageService();
        clone.region = this.region;
        clone.accessKey = this.accessKey;
        clone.secretKey = this.secretKey;
        return clone;
    }

    private AmazonS3 buildClient() {
        if (this.region == null || this.accessKey == null || this.secretKey == null) {
            throw new IllegalArgumentException("S3 client not configured");
        }
        return AmazonS3ClientBuilder.standard()
                                    .withRegion(Regions.fromName(this.region))
                                    .withCredentials(new AWSStaticCredentialsProvider(
                                            new BasicAWSCredentials(this.accessKey, this.secretKey)))
                                    .build();
    }

    private void ensureBucketExists(String name) {
        if (StringUtilities.isNullOrEmpty(name)) {
            throw new IllegalArgumentException("[name] Bucket name cannot be empty");
        } else if (name.length() < 3 || name.length() > 63) {
            throw new IllegalArgumentException("[name] Bucket name length must be between 3 and 63 characters");
        } else if (name.contains("_") || name.contains("..") || name.contains("-.")) {
            throw new IllegalArgumentException("[name] Bucket name contains unsupported characters");
        } else if (!name.toLowerCase().equals(name)) {
            throw new IllegalArgumentException("[name] Bucket name cannot contain uppercase characters");
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
        if (objects != null) {
            for (String commonPrefix : objects.getCommonPrefixes()) {
                final FileObject fileObject = new FileObject(PROTOCOL, request.getBucketName() + "/" + commonPrefix, true, 0);
                fileObject.addAttribute("remotePath", "s3://" + fileObject.getRelativePath());
                files.add(fileObject);
            }
            if (objects.getObjectSummaries() != null) {
                while (true) {
                    for (S3ObjectSummary summary : objects.getObjectSummaries()) {
                        final FileObject fileObject = new FileObject(PROTOCOL, request.getBucketName() + "/" + summary.getKey(), false, summary.getSize());
                        fileObject.addAttribute("remotePath", "s3://" + fileObject.getRelativePath());
                        files.add(fileObject);
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
        if (objects != null) {
            for (String commonPrefix : objects.getCommonPrefixes()) {
                final FileObject fileObject = new FileObject(PROTOCOL, request.getBucketName() + "/" + commonPrefix, true, 0);
                fileObject.addAttribute("remotePath", "s3://" + fileObject.getRelativePath());
                results.add(fileObject);
            }
            final List<S3ObjectSummary> objectSummaries = objects.getObjectSummaries();
            if (objectSummaries != null) {
                for (int i = 0; i < objectSummaries.size(); i++) {
                    final S3ObjectSummary summary = objectSummaries.get(i);
                    final FileObject fileObject = new FileObject(PROTOCOL, request.getBucketName() + "/" + summary.getKey(), false, summary.getSize());
                    fileObject.addAttribute("remotePath", "s3://" + fileObject.getRelativePath());
                    if (objects.isTruncated() && i == objectSummaries.size() - 1) {
                        fileObject.addAttribute("nextMarker", objects.getNextMarker());
                    }
                    results.add(fileObject);
                }
            }
        }
        return results;
    }

    private void traverse(String fromPath, List<FileObject> files) {
        ListObjectsRequest request = new ListObjectsRequest();
        final Path path = Paths.get(fromPath);
        request.setBucketName(path.getName(0).toString());
        request.setDelimiter("/");
        final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/") + "/";
        request.setPrefix(relativePath);
        ObjectListing objects = this.client.listObjects(request);
        if (objects != null) {
            for (String commonPrefix : objects.getCommonPrefixes()) {
                final FileObject folder = new FileObject(PROTOCOL, request.getBucketName() + "/" + commonPrefix, true, 0);
                files.add(folder);
                traverse(folder.getRelativePath(), files);
            }
            if (objects.getObjectSummaries() != null) {
                while (true) {
                    for (S3ObjectSummary summary : objects.getObjectSummaries()) {
                        files.add(new FileObject(PROTOCOL, request.getBucketName() + "/" + summary.getKey(), false, summary.getSize()));
                    }
                    if (!objects.isTruncated()) {
                        break;
                    }
                    objects = this.client.listNextBatchOfObjects(objects);
                }
            }
        }
    }
}
