package ro.cs.tao.smb;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;
import ro.cs.tao.services.interfaces.StorageService;
import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.workspaces.Repository;
import ro.cs.tao.workspaces.RepositoryType;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SMBStorageService implements StorageService<byte[], InputStream>{
    private static final String PROTOCOL = "smb";
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
        this.repository.setRootFunctor(() -> {
            RepositoryType type = SMBStorageService.this.repository.getType();
            LinkedHashMap<String, String> parameters = SMBStorageService.this.repository.getParameters();
            String user = parameters.get(type.userKey());
            String pwd = parameters.get(type.passwordKey());
            String url = parameters.get(type.rootKey());
            if (!url.endsWith("/")) {
                url += "/";
            }
            return PROTOCOL + "://" + (StringUtilities.isNullOrEmpty(user) ? "" : (user + ":" + URLEncoder.encode(pwd, "UTF-8") + "@")) + url;
        });
    }

    @Override
    public Path createFolder(String relativePath, boolean userOnly) throws IOException {
        SmbFile folder = new SmbFile(this.repository.root() + relativePath + (relativePath.endsWith("/") ? "" : "/"));
        if (!folder.exists()) {
            folder.mkdir();
        }
        return Paths.get(folder.getUncPath());
    }

    @Override
    public void storeUserFile(byte[] object, String relativeFolder, String description) throws Exception {
        try (InputStream in = new ByteArrayInputStream(object)) {
            storeFile(in, object.length, relativeFolder, description);
        }
    }

    @Override
    public void storeFile(InputStream stream, long length, String relativeFolder, String description) throws Exception {
        SmbFile smbfile = new SmbFile(this.repository.root() + relativeFolder + "/" + description);
        try (OutputStream out = new SmbFileOutputStream(smbfile)) {
            FileUtilities.copyStream(stream, out);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean exists(String path) throws Exception {
        SmbFile smbPath = new SmbFile(this.repository.root() + path);
        return smbPath.exists();
    }

    @Override
    public void remove(String path) throws IOException {
        SmbFile smbPath = new SmbFile(this.repository.root() + path);
        smbPath.delete();
    }

    @Override
    public void move(String source, String destination) throws IOException {
        SmbFile smbSource = new SmbFile(this.repository.root() + source);
        SmbFile smbDestination = new SmbFile(this.repository.root() + destination);
        smbSource.renameTo(smbDestination);
    }

    @Override
    public List<FileObject> listFiles(String fromPath, Set<String> exclusions, String lastItem) throws IOException {
        String path = this.repository.root() + (this.repository.isRoot(fromPath) ? "" : fromPath);
        if (!path.endsWith("/")) {
            path += "/";
        }
        SmbFile smbPath = new SmbFile(path);
        SmbFile[] smbFiles = smbPath.listFiles();
        final List<FileObject> files = new ArrayList<>();
        if (smbFiles != null) {
            for (SmbFile file : smbFiles) {
                final FileObject fileObject =
                        new FileObject(PROTOCOL, file.getPath().replace(this.repository.root(), ""),
                                       file.isDirectory(), file.length());
                files.add(fileObject);
            }
        }
        return files;
    }

    @Override
    public List<FileObject> listTree(String fromPath) throws IOException {
        final List<FileObject> files = new ArrayList<>();
        traverse(fromPath, files);
        return files;
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
        String host = this.repository.getParameters().get(this.repository.getType().rootKey());
        String strPath = path.toString().replace("\\", "/");
        if (strPath.startsWith(host)) {
            strPath = strPath.substring(host.length() + 1);
        }
        return new SmbFileInputStream(new SmbFile(this.repository.root() + strPath));
    }

    @Override
    public void streamToZip(Path zipRoot, ZipOutputStream stream) throws IOException {
        String folderPath = zipRoot.toString().replace("\\", "/");
        SmbFile root = new SmbFile(this.repository.root() + folderPath);
        if (root.exists() && root.isDirectory()) {
            final List<SmbFile> files = new ArrayList<>();
            traverseSmb(folderPath, files);
            for (SmbFile current : files) {
                if (current.isFile()) {
                    String zipPath = this.repository.relativize(folderPath, this.repository.relativizeToBucket(current.getUncPath()));
                    ZipEntry entry = new ZipEntry(zipPath);
                    entry.setSize(current.length());
                    entry.setTime(System.currentTimeMillis());
                    stream.putNextEntry(entry);
                    FileUtilities.copyStream(new SmbFileInputStream(current), stream);
                    stream.closeEntry();
                } else {
                    if (!current.equals(root)) {
                        String zipPath = this.repository.relativize(folderPath, this.repository.relativizeToBucket(current.getUncPath())) + File.separator;
                        ZipEntry entry = new ZipEntry(zipPath);
                        stream.putNextEntry(entry);
                        stream.closeEntry();
                    }
                }
            }
        } else {
            throw new IOException("Not a folder");
        }
    }

    private void traverse(String fromPath, List<FileObject> files) throws IOException {
        SmbFile path = new SmbFile(this.repository.root() + (this.repository.isRoot(fromPath) ? "" : fromPath));
        SmbFile[] smbFiles = path.listFiles();
        if (smbFiles != null) {
            for (SmbFile file : smbFiles) {
                String filePath = file.getPath();
                String relPath = filePath.replace(this.repository.root(), "");
                final FileObject fileObject =
                        new FileObject(PROTOCOL, relPath, file.isDirectory(), file.length());
                files.add(fileObject);
                if (file.isDirectory()) {
                    traverse(relPath, files);
                }
            }
        }
    }

    private void traverseSmb(String fromPath, List<SmbFile> files) throws IOException {
        SmbFile path = new SmbFile(this.repository.root() + (this.repository.isRoot(fromPath) ? "" : fromPath));
        SmbFile[] smbFiles = path.listFiles();
        if (smbFiles != null) {
            for (SmbFile file : smbFiles) {
                files.add(file);
                if (file.isDirectory()) {
                    traverseSmb(this.repository.relativizeToBucket(fromPath), files);
                }
            }
        }
    }
}
