package ro.cs.tao.smb;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;
import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.services.storage.BaseStorageService;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.utils.executors.monitoring.ProgressListener;
import ro.cs.tao.workspaces.Repository;
import ro.cs.tao.workspaces.RepositoryType;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SMBStorageService extends BaseStorageService<byte[], InputStream> {
    private static final String PROTOCOL = "smb";

    public SMBStorageService() {
    }

    @Override
    public boolean isIntendedFor(String protocol) {
        return PROTOCOL.equalsIgnoreCase(protocol);
    }

    @Override
    public void associate(Repository repository) {
        if (!isIntendedFor(repository.getType().prefix())) {
            throw new IllegalArgumentException("Wrong repository type");
        }
        super.associate(repository);
        final Repository internal = repository();
        Callable<String> functor = () -> {
            RepositoryType type = internal.getType();
            LinkedHashMap<String, String> parameters = internal.getParameters();
            String user = parameters.get(type.userKey());
            String pwd = parameters.get(type.passwordKey());
            String host = parameters.get("smb.server");
            return PROTOCOL + "://" + (StringUtilities.isNullOrEmpty(user) ? "" : (user + ":" + URLEncoder.encode(pwd, "UTF-8") + "@")) + host + "/";
        };
        internal.setRootFunctor(() -> {
            RepositoryType type = internal.getType();
            LinkedHashMap<String, String> parameters = internal.getParameters();
            String share = parameters.get(type.rootKey());
            return functor.call() + share + "/";
        });
        internal.setBucketFunctor(functor);
    }

    @Override
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    @Override
    public Path createFolder(String relativePath, boolean userOnly) throws IOException {
        SmbFile folder = new SmbFile(repository().resolve(relativePath) + (relativePath.endsWith("/") ? "" : "/"));
        if (!folder.exists()) {
            folder.mkdir();
        }
        return Paths.get(folder.getUncPath());
    }

    @Override
    public void storeUserFile(byte[] object, String relativeFolder, String description) throws Exception {
        storeFile(new ByteArrayInputStream(object), object.length, relativeFolder, description);
    }

    @Override
    public void storeFile(InputStream stream, long length, String relativeFolder, String description) throws Exception {
        SmbFile smbfile = new SmbFile(repository().resolve(relativeFolder));
    	// make sure that the folder exists.
        try (SmbFile smbParent = new SmbFile(smbfile.getParent())) {
        	if (!smbParent.exists()) {
        		smbParent.mkdirs();
        	}
        }
        try (OutputStream out = new SmbFileOutputStream(smbfile);
             InputStream toPass = wrapStream(stream)) {
            FileUtilities.copyStream(toPass, out);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean exists(String path) throws Exception {
        SmbFile smbPath = new SmbFile(repository().resolve(path));
        return smbPath.exists();
    }

    @Override
    public void remove(String path) throws IOException {
        SmbFile smbPath = new SmbFile(repository().resolve(path));
        smbPath.delete();
    }

    @Override
    public void move(String source, String destination) throws IOException {
        SmbFile smbSource = new SmbFile(repository().resolve(source));
        SmbFile smbDestination = new SmbFile(repository().resolve(destination));
        smbSource.renameTo(smbDestination);
    }

    @Override
    public List<FileObject> listFiles(String fromPath, Set<String> exclusions, String lastItem, int depth) throws IOException {
        final Repository repository = repository();
        String path = repository.isRoot(fromPath)
                ? repository.root()
                : fromPath.startsWith("/")
                    ? repository.resolve(fromPath.substring(1))
                    : repository.resolve(fromPath);
        if (!path.endsWith("/")) {
            path += "/";
        }
        SmbFile smbPath = new SmbFile(path);
        SmbFile[] smbFiles = smbPath.listFiles();
        final List<FileObject> files = new ArrayList<>();
        if (repository.isRoot(fromPath)) {
            files.add(repositoryRootNode(repository));
        }
        if (smbFiles != null) {
            String root = repository.root();
            for (SmbFile file : smbFiles) {
                final FileObject fileObject = new FileObject(PROTOCOL, file.getPath().replace(root, ""), file.isDirectory(), file.length());
                fileObject.setLastModified(LocalDateTime.ofInstant(Instant.ofEpochMilli(file.getLastModified()), ZoneId.systemDefault()));
                fileObject.addAttribute(REMOTE_PATH_ATTRIBUTE, file.getPath());
                files.add(fileObject);
            }
        }
        return files;
    }

    @Override
    public List<FileObject> listTree(String fromPath) throws IOException {
        final List<FileObject> files = new ArrayList<>();
        files.add(repositoryRootNode(repository()));
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
    public InputStream download(String path) throws IOException {
        return new SmbFileInputStream(new SmbFile(repository().resolve(path)));
    }

    @Override
    public void streamToZip(String zipRoot, ZipOutputStream stream) throws IOException {
        final Repository repository = repository();
        SmbFile root = new SmbFile(repository.resolve(zipRoot));
        if (root.exists() && root.isDirectory()) {
            final List<SmbFile> files = new ArrayList<>();
            traverseSmb(zipRoot, files);
            for (SmbFile current : files) {
                if (current.isFile()) {
                    String zipPath = repository.relativize(zipRoot, repository.relativizeToBucket(current.getUncPath()));
                    ZipEntry entry = new ZipEntry(zipPath);
                    entry.setSize(current.length());
                    entry.setTime(System.currentTimeMillis());
                    stream.putNextEntry(entry);
                    FileUtilities.copyStream(new SmbFileInputStream(current), stream);
                    stream.closeEntry();
                } else {
                    if (!current.equals(root)) {
                        String zipPath = repository.relativize(zipRoot, repository.relativizeToBucket(current.getUncPath())) + File.separator;
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

    @Override
    public String readAsText(InputStream resource, int lines, int skipLines) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource));
             Stream<String> stream = reader.lines()) {
            return stream.skip(skipLines).limit(lines).collect(Collectors.joining());
        }
    }

    private void traverse(String fromPath, List<FileObject> files) throws IOException {
        final Repository repository = repository();
        SmbFile path = new SmbFile(repository.resolve(fromPath));
        SmbFile[] smbFiles = path.listFiles();
        if (smbFiles != null) {
            for (SmbFile file : smbFiles) {
                String filePath = file.getPath();
                String relPath = filePath.replace(repository.root(), "");
                final FileObject fileObject = new FileObject(PROTOCOL, relPath, file.isDirectory(), file.length());
                fileObject.setLastModified(LocalDateTime.ofInstant(Instant.ofEpochMilli(file.getLastModified()), ZoneId.systemDefault()));
                fileObject.addAttribute(REMOTE_PATH_ATTRIBUTE, file.getPath());
                files.add(fileObject);
                if (file.isDirectory()) {
                    traverse(relPath, files);
                }
            }
        }
    }

    private void traverseSmb(String fromPath, List<SmbFile> files) throws IOException {
        final Repository repository = repository();
        SmbFile path = new SmbFile(repository.resolve(fromPath));
        SmbFile[] smbFiles = path.listFiles();
        if (smbFiles != null) {
            for (SmbFile file : smbFiles) {
                files.add(file);
                if (file.isDirectory()) {
                    traverseSmb(repository.relativizeToBucket(fromPath), files);
                }
            }
        }
    }
}
