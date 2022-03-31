package ro.cs.tao.stac;

import ro.cs.tao.services.interfaces.StorageService;
import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.workspaces.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipOutputStream;

public class STACStorageService implements StorageService<byte[], InputStream> {
    private static final String PROTOCOL = "stac";

    @Override
    public boolean isIntendedFor(String protocol) {
        return PROTOCOL.equalsIgnoreCase(protocol);
    }

    @Override
    public Path createFolder(Repository workspace, String relativePath, boolean userOnly) throws IOException {
        return null;
    }

    @Override
    public void storeUserFile(byte[] object, Repository workspace, String relativeFolder, String description) throws Exception {

    }

    @Override
    public void storeFile(InputStream stream, long length, Repository workspace, String relativeFolder, String description) throws Exception {

    }

    @Override
    public void storePublicFile(byte[] object, Repository workspace, String relativeFolder, String description) throws Exception {

    }

    @Override
    public boolean exists(Repository workspace, String path) throws Exception {
        return false;
    }

    @Override
    public void remove(Repository workspace, String path) throws IOException {

    }

    @Override
    public void move(Repository workspace, String source, String destination) throws IOException {

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
        return null;
    }

    @Override
    public List<FileObject> listFiles(Repository workspace, String fromPath, Set<String> exclusions, String lastItem) throws IOException {
        return null;
    }

    @Override
    public List<FileObject> listTree(Repository workspace, String fromPath) throws IOException {
        return null;
    }

    @Override
    public List<FileObject> getWorkflowResults(long workflowId) throws IOException {
        return null;
    }

    @Override
    public List<FileObject> getJobResults(long jobId) throws IOException {
        return null;
    }

    @Override
    public InputStream download(Path path) throws IOException {
        return null;
    }

    @Override
    public void streamToZip(Path zipRoot, ZipOutputStream stream) throws IOException {

    }
}
