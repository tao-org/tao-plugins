package ro.cs.tao.aws;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.services.interfaces.StorageService;
import ro.cs.tao.services.model.FileObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class S3Client implements StorageService<byte[]> {
    private String region;
    private String accessKey;
    private String secretKey;
    private String defaultBucket;
    private AmazonS3 client;

    public S3Client() {
        final ConfigurationProvider cfgProvider = ConfigurationManager.getInstance();
        this.region = cfgProvider.getValue("aws.region", null);
        this.accessKey = cfgProvider.getValue("aws.access.key", null);
        this.secretKey = cfgProvider.getValue("aws.secret.key", null);
        this.defaultBucket = cfgProvider.getValue("aws.bucket", null);
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public void setDefaultBucket(String defaultBucket) {
        this.defaultBucket = defaultBucket;
    }

    @Override
    public Path createFolder(String folderRelativePath, boolean userOnly) throws IOException {
        return null;
    }

    @Override
    public void storeUserFile(byte[] object, String relativeFolder, String description) throws Exception {

    }

    @Override
    public void storePublicFile(byte[] object, String relativeFolder, String description) throws Exception {

    }

    @Override
    public void remove(String name) throws IOException {

    }

    @Override
    public void move(String source, String destination) throws IOException {

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
        return null;
    }

    @Override
    public List<FileObject> listUserWorkspace(String userName) throws IOException {
        if (this.client == null) {
            this.client = buildClient();
        }
        try {
            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(this.defaultBucket != null ? this.defaultBucket : userName);
            request.setDelimiter("/");
            request.setMarker("/");
            return list(request);
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<FileObject> listFiles(Path fromPath, Set<Path> exclusions) throws IOException {
        if (fromPath.isAbsolute()) {
            throw new IllegalArgumentException("Path should be relative to the S3 bucket (including the bucket name)");
        }
        if (this.client == null) {
            this.client = buildClient();
        }
        try {
            ListObjectsRequest request = new ListObjectsRequest();
            final Path bucket = fromPath.getName(0);
            request.setBucketName(bucket.toString());
            request.setDelimiter("/");
            final String relativePath = bucket.relativize(fromPath).toString().replace("\\", "/") + "/";
            request.setPrefix(relativePath);
            return list(request);
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
    public S3Client clone() throws CloneNotSupportedException {
        final S3Client clone = new S3Client();
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

    private List<FileObject> list(ListObjectsRequest request) {
        final List<FileObject> files = new ArrayList<>();
        ObjectListing objects = this.client.listObjects(request);
        if (objects != null) {
            for (String commonPrefix : objects.getCommonPrefixes()) {
                final FileObject fileObject = new FileObject(request.getBucketName() + "/" + commonPrefix, true, 0);
                fileObject.addAttribute("remotePath", "s3://" + fileObject.getRelativePath());
                files.add(fileObject);
            }
            if (objects.getObjectSummaries() != null) {
                while (true) {
                    for (S3ObjectSummary summary : objects.getObjectSummaries()) {
                        final FileObject fileObject = new FileObject(request.getBucketName() + "/" + summary.getKey(), false, summary.getSize());
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
}
