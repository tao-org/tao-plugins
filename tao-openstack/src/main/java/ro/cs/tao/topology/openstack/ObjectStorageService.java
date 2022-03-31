package ro.cs.tao.topology.openstack;

import org.openstack4j.model.storage.object.SwiftObject;
import ro.cs.tao.ListenableInputStream;
import ro.cs.tao.ProgressListener;
import ro.cs.tao.services.interfaces.StorageService;
import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.workspaces.Repository;

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

public class ObjectStorageService implements StorageService<byte[], InputStream> {
    private static final String PROTOCOL = "swift";

    private final SwiftService swiftService;

    private ProgressListener progressListener;

    public ObjectStorageService() {
        this.swiftService = new SwiftService();
    }

    @Override
    public boolean isIntendedFor(String protocol) {
        return PROTOCOL.equalsIgnoreCase(protocol);
    }

    @Override
    public void setup(Map<String, String> parameters) {
        swiftService.setParameters(parameters);
    }

    @Override
    public Path createFolder(Repository workspace, String folderRelativePath, boolean userOnly) throws IOException {
        final Path path = Paths.get(resolve(workspace, folderRelativePath));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket");
        }
        final String bucket = path.getName(0).toString();
        final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/");
        return Paths.get(this.swiftService.createFolder(relativePath, bucket));
    }

    @Override
    public void storeUserFile(byte[] object, Repository workspace, String relativeFolder, String description) throws Exception {
        if (object == null || object.length == 0) {
            throw new IllegalArgumentException("[object] Content cannot be null or zero");
        }
        Path path = Paths.get(resolve(workspace, relativeFolder));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[relativeFolder] Path should be relative to the S3 bucket (including the bucket name)");
        }
        final String bucket = path.getName(0).toString();
        final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/");
        InputStream toPass = this.progressListener != null
                ? new ListenableInputStream(new ByteArrayInputStream(object), this.progressListener) : new ByteArrayInputStream(object);
        this.swiftService.upload(relativePath, toPass, bucket, description);
    }

    @Override
    public void storeFile(InputStream stream, long length, Repository workspace, String relativeFolder, String description) throws Exception {
        if (stream == null) {
            throw new IllegalArgumentException("[stream] Null stream");
        }
        final Path path = Paths.get(resolve(workspace, relativeFolder));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[relativeFolder] Path should be relative to the S3 bucket (including the bucket name)");
        }
        final String bucket = path.getName(0).toString();
        final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/");
        InputStream toPass = this.progressListener != null
                ? new ListenableInputStream(stream, this.progressListener) : stream;
        this.swiftService.upload(relativePath, toPass, bucket, description);
    }

    @Override
    public void storePublicFile(byte[] object, Repository workspace, String relativeFolder, String description) throws Exception {
        storeUserFile(object, workspace, relativeFolder, description);
    }

    @Override
    public boolean exists(Repository workspace, String path) throws Exception {
        final Path filePath = Paths.get(resolve(workspace, path));
        if (filePath.isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket (including the bucket name)");
        }
        final String bucket = filePath.getName(0).toString();
        final String relativePath = filePath.subpath(1, filePath.getNameCount()).toString().replace("\\", "/");
        return this.swiftService.exists(relativePath, bucket);
    }

    @Override
    public void remove(Repository workspace, String name) throws IOException {
        final Path path = Paths.get(resolve(workspace, name));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket (including the bucket name)");
        }
        final String bucket = path.getName(0).toString();
        final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/");
        this.swiftService.delete(bucket, relativePath);
    }

    @Override
    public void move(Repository workspace, String source, String destination) throws IOException {
        throw new UnsupportedOperationException("Not implemented for OpenStack Swift");
    }

    @Override
    public List<FileObject> listUploaded() throws IOException {
        throw new UnsupportedOperationException("Not supported for S3");
    }

    @Override
    public List<FileObject> listPublicWorkspace() throws IOException {
        throw new UnsupportedOperationException("Not supported for S3");
    }

    @Override
    public List<FileObject> listUploaded(String userName) throws IOException {
        return listUserWorkspace(userName);
    }

    @Override
    public List<FileObject> listUserWorkspace(String userName) throws IOException {
        return list(userName, null, null);
    }

    @Override
    public List<FileObject> listFiles(Repository workspace, String fromPath, Set<String> exclusions, String lastItem) throws IOException {
        return list(resolve(workspace, fromPath), exclusions, lastItem);
    }

    @Override
    public List<FileObject> listTree(Repository workspace, String fromPath) throws IOException {
        final List<FileObject> results = new ArrayList<>();
        traverse(resolve(workspace, fromPath), null, results);
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
    public InputStream download(Path path) throws IOException {
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("Path should be relative to the S3 bucket (including the bucket name)");
        }
        final String bucket = path.getName(0).toString();
        final String relativePath = path.subpath(1, path.getNameCount()).toString().replace("\\", "/");
        return this.swiftService.download(relativePath, bucket);
    }

    @Override
    public void streamToZip(Path root, ZipOutputStream stream) throws IOException {
        if (root.isAbsolute()) {
            throw new IllegalArgumentException("Path should be relative to the S3 bucket (including the bucket name)");
        }
        final String bucket = root.getName(0).toString();
        final List<FileObject> keys = new ArrayList<>();
        traverse(root.toString(), null, keys);
        ZipEntry entry = null;
        for (FileObject current : keys) {
            final Path currentPath = Paths.get(current.getRelativePath());
            if (!current.isFolder()) {
                final String relativePath = currentPath.subpath(1, currentPath.getNameCount()).toString().replace("\\", "/");
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
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    private List<FileObject> list(String folderRelativePath, Set<String> exclusions, String lastItem) {
        final Path path = Paths.get(folderRelativePath);
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("[name] Path should be relative to the S3 bucket (including the bucket name)");
        }
        final String bucket = path.getName(0).toString();
        final String relativePath = path.getNameCount() > 1
                ? path.subpath(1, path.getNameCount()).toString().replace("\\", "/")
                : "";
        final List<FileObject> files = new ArrayList<>();
        final List<? extends SwiftObject> objects = this.swiftService.list(relativePath, bucket, lastItem);
        if (objects != null) {
            objects.forEach(object -> {
                final String oPath = bucket + "/" + (object.isDirectory() ? object.getDirectoryName() : object.getName());
                if (exclusions == null || exclusions.stream().noneMatch(oPath::startsWith)) {
                    final FileObject fileObject = new FileObject(PROTOCOL, oPath, object.isDirectory(), object.getSizeInBytes());
                    fileObject.addAttribute("remotePath", "swift://" + fileObject.getRelativePath());
                    files.add(fileObject);
                }
            });
        }
        return files;
    }

    private void traverse(String fromPath, Set<String> exclusions, List<FileObject> files) {
        final Path path = Paths.get(fromPath);
        final String bucket = path.getName(0).toString();
        final String relativePath = path.getNameCount() > 1
                ? path.subpath(1, path.getNameCount()).toString().replace("\\", "/")
                : "";
        List<? extends SwiftObject> objects = this.swiftService.list(relativePath, bucket, null);
        if (objects != null) {
            for (SwiftObject object : objects) {
                final String oPath = bucket + "/" + (object.isDirectory() ? object.getDirectoryName() : object.getName());
                if (exclusions == null || exclusions.stream().noneMatch(oPath::startsWith)) {
                    final FileObject fileObject = new FileObject(PROTOCOL, oPath, object.isDirectory(), object.getSizeInBytes());
                    fileObject.addAttribute("remotePath", "swift://" + fileObject.getRelativePath());
                    files.add(fileObject);
                    if (object.isDirectory()) {
                        traverse(fileObject.getRelativePath(), exclusions, files);
                    }
                }
            }
        }
    }
}
