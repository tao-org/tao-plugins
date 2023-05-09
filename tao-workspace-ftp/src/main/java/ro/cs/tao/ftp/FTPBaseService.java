package ro.cs.tao.ftp;

import com.github.robtimus.filesystems.ftp.ConnectionMode;
import com.github.robtimus.filesystems.ftp.FTPEnvironment;
import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.services.storage.BaseStorageService;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.workspaces.Repository;
import ro.cs.tao.workspaces.RepositoryType;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class FTPBaseService extends BaseStorageService<byte[], InputStream> {
    protected FileSystem ftpFileSystem;
    protected FTPEnvironment environment;

    protected abstract String protocol();
    protected abstract FTPEnvironment newEnvironment();

    private static final Map<String, FileSystem> fileSystemMap = Collections.synchronizedMap(new HashMap<>());

    @Override
    public boolean isIntendedFor(String protocol) {
        return protocol().equalsIgnoreCase(protocol);
    }

    @Override
    public void associate(Repository repository) {
        if (!isIntendedFor(repository.getType().prefix())) {
            throw new IllegalArgumentException("Wrong repository type");
        }
        super.associate(repository);
        final Repository internal = repository();
        LinkedHashMap<String, String> parameters = internal.getParameters();
        RepositoryType type = internal.getType();
        String user = parameters.get(type.userKey());
        if (StringUtilities.isNullOrEmpty(user)) {
            user = "anonymous";
        }
        String pwd = parameters.get(type.passwordKey());
        String host = parameters.get(type.rootKey());
        internal.setRootFunctor(() -> protocol() + "://" + host);
        internal.setBucketFunctor(() -> "");
        environment = newEnvironment()
                .withCredentials(user, pwd != null ? pwd.toCharArray() : null)
                .withConnectTimeout(500)
                .withConnectionMode(ConnectionMode.PASSIVE)
                .withAutodetectEncoding(true);
        initializeFileSystem();
    }

    @Override
    public Path createFolder(String relativePath, boolean userOnly) throws IOException {
        initializeFileSystem();
        return Files.createDirectory(getPath(repository().resolve(relativePath)));
    }

    @Override
    public void storeUserFile(byte[] object, String relativeFolder, String description) throws Exception {
        initializeFileSystem();
        Files.copy(new ByteArrayInputStream(object),
                   getPath(repository().resolve(relativeFolder + "/" + description)));
    }

    @Override
    public void storeFile(InputStream stream, long length, String relativeFolder, String description) throws Exception {
        initializeFileSystem();
        Files.copy(stream, getPath(repository().resolve(relativeFolder + "/" + description)));
    }

    @Override
    public boolean exists(String path) throws Exception {
        initializeFileSystem();
        return Files.exists(getPath(repository().resolve(path)));
    }

    @Override
    public void remove(String path) throws IOException {
        initializeFileSystem();
        Files.delete(getPath(repository().resolve(path)));
    }

    @Override
    public void move(String source, String destination) throws IOException {
        initializeFileSystem();
        Files.move(getPath(repository().resolve(source)),
                   getPath(repository().resolve(destination)));
    }

    @Override
    public List<FileObject> listFiles(String fromPath, Set<String> exclusions, String lastItem, int depth) throws IOException {
        initializeFileSystem();
        Repository repository = repository();
        final Path root = getPath(repository.bucketName());
        final Path startPath = getPath(repository.resolve(fromPath));
        final List<Path> list = list(startPath, depth);
        final List<FileObject> fileObjects = new ArrayList<>();
        if (repository.isRoot(fromPath)) {
            fileObjects.add(repositoryRootNode(repository));
        }
        long size;
        LocalDateTime lastModified;
        for (Path realPath : list) {
            boolean isFolder;
            lastModified = null;
            size = 0;
            if (realPath.toString().contains(".")) {
                try {
                    BasicFileAttributes attributes = Files.readAttributes(realPath, BasicFileAttributes.class);
                    isFolder = attributes.isDirectory();
                    size = attributes.size();
                    lastModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(attributes.lastModifiedTime().toMillis()),
                                                           ZoneId.systemDefault());
                } catch (IOException e) {
                    size = -1;
                    isFolder = false;
                }
            } else {
                isFolder = true;
            }
            final Path path = root.relativize(realPath);
            String pathToRecord = path.toString().replace("\\", "/");
            if (isFolder) {
                pathToRecord += "/";
            }
            final FileObject fileObject = new FileObject(protocol(), pathToRecord, isFolder, size);
            fileObject.setLastModified(lastModified);
            fileObject.addAttribute(REMOTE_PATH_ATTRIBUTE, realPath.toString());
            fileObjects.add(fileObject);
        }
        return fileObjects;
    }

    @Override
    public List<FileObject> listTree(String fromPath) throws IOException {
        return listFiles(fromPath, null, null, 10);
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
    public InputStream download(String path) throws IOException {
        String p = !path.startsWith(repository().root()) ? repository().resolve(path) : path;
        URLConnection connection = new URL(p).openConnection();
        return connection.getInputStream();
    }

    @Override
    public void streamToZip(String zipRoot, ZipOutputStream stream) throws IOException {
        initializeFileSystem();
        Path root = getPath(zipRoot);
        if (Files.exists(root) && Files.isDirectory(root)) {
            List<Path> paths = FileUtilities.listTree(root);
            for (Path current : paths) {
                if (Files.isRegularFile(current)) {
                    String zipPath = root.relativize(current).toString();
                    ZipEntry entry = new ZipEntry(zipPath);
                    entry.setSize(Files.size(current));
                    entry.setTime(System.currentTimeMillis());
                    stream.putNextEntry(entry);
                    FileUtilities.copyStream(Files.newInputStream(current), stream);
                    stream.closeEntry();
                } else {
                    if (!current.equals(root)) {
                        String zipPath = root.relativize(current) + File.separator;
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
            return lines > 0
                   ? stream.skip(skipLines).limit(lines).collect(Collectors.joining("\n"))
                   : stream.collect(Collectors.joining("\n"));
        }
    }

    private void initializeFileSystem() {
        final String user = (String) environment.get("username");
        final String root = repository().root();
        final String key = root + ":" + user;
        ftpFileSystem = fileSystemMap.get(key);
        if (ftpFileSystem == null || !ftpFileSystem.isOpen()) {
            // Either the fileSystem was not yet created or is closed
            try {
                // First try to get an existing instance
                final URI uri = normalizeURI(URI.create(root), user);
                ftpFileSystem = FileSystems.getFileSystem(uri);
            } catch (FileSystemNotFoundException | URISyntaxException e) {
                try {
                    // No previous instance found, creating a new one
                    ftpFileSystem = FileSystems.newFileSystem(URI.create(root), environment);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
            fileSystemMap.put(key, ftpFileSystem);
        }
    }

    private List<Path> list(Path path, int depth) throws IOException {
        if (depth == 1) {
            try (Stream<Path> stream = Files.list(path)) {
                return stream.collect(Collectors.toList());
            }
        } else {
            try (Stream<Path> stream = Files.walk(path, depth)) {
                return stream.filter(p -> !p.equals(path)).sorted().collect(Collectors.toList());
            }
        }
    }

    private Path getPath(String path) {
        String p = path.replace(repository().root(), "");
        return ftpFileSystem.getPath(p.isEmpty() ? "/" : p);
    }

    private URI normalizeURI(URI uri, String user) throws URISyntaxException {
        return new URI(uri.getScheme(), user, uri.getHost(), uri.getPort(), null, null, null);
    }
}
