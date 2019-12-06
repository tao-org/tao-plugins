package ro.cs.tao.datasource.remote.aws.download;

import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.aws.AWSDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.HttpMethod;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Properties;

public abstract class AWSStrategy extends DownloadStrategy {

    private static final String START_MESSAGE = "(%s,%s) %s [size: %skB]";
    private static final String COMPLETE_MESSAGE = "(%s,%s) %s [elapsed: %ss]";
    private static final String ERROR_MESSAGE = "Cannot download %s: %s";

    AWSStrategy(AWSDataSource dataSource, String targetFolder, Properties properties) {
        super(dataSource, targetFolder, properties);
    }

    AWSStrategy(AWSStrategy other) {
        super(other);
    }

    public abstract AWSStrategy clone();

    protected abstract Path fetchImpl(EOProduct product) throws IOException;

    protected abstract String getMetadataUrl(EOProduct descriptor);

    protected Path downloadFile(String remoteUrl, Path file) throws IOException, InterruptedException {
        checkCancelled();
        String subActivity = remoteUrl.substring(remoteUrl.lastIndexOf(URL_SEPARATOR) + 1);
        if ("$value".equals(subActivity)) {
            subActivity = file.getFileName().toString();
        }
        HttpURLConnection connection = null;
        try {
            logger.fine(String.format("Begin download for %s", subActivity));
            subActivityStart(subActivity);
            connection = ((AWSDataSource) this.dataSource).buildS3Connection(HttpMethod.GET, remoteUrl);
            long remoteFileLength = connection.getContentLengthLong();
            if (currentProductProgress.needsAdjustment()) {
                currentProductProgress.adjust(remoteFileLength);
            }
            long localFileLength = 0;
            checkCancelled();
            if (Files.exists(file)) {
                localFileLength = Files.size(file);
                if (localFileLength != remoteFileLength) {
                    if (FetchMode.RESUME.equals(this.fetchMode)) {
                        connection.disconnect();
                        connection = ((AWSDataSource) this.dataSource).buildS3Connection(HttpMethod.GET, remoteUrl);
                        connection.setRequestProperty("Range", "bytes=" + localFileLength + "-");
                    } else {
                        Files.delete(file);
                    }
                    logger.fine(String.format("Remote file size: %s. Local file size: %s. File " +
                                    (FetchMode.OVERWRITE.equals(this.fetchMode) ?
                                            "will be downloaded again." :
                                            "download will be resumed."),
                            remoteFileLength,
                            localFileLength));
                }
            }
            checkCancelled();
            if (localFileLength != remoteFileLength) {
                int kBytes = (int) (remoteFileLength >> 10);
                logger.fine(String.format(START_MESSAGE, currentProduct.getName(), currentStep, file.getFileName(), kBytes));
                long start = System.currentTimeMillis();
                logger.fine(String.format("Local temporary file %s created", file.toString()));
                try (InputStream inputStream = connection.getInputStream();
                     SeekableByteChannel outputStream = Files.newByteChannel(file, EnumSet.of(StandardOpenOption.CREATE,
                             StandardOpenOption.APPEND,
                             StandardOpenOption.WRITE))) {
                    outputStream.position(localFileLength);
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    //int totalRead = 0;
                    logger.fine("Begin reading from input stream");
                    while (!isCancelled() && (read = inputStream.read(buffer)) != -1) {
                        outputStream.write(ByteBuffer.wrap(buffer, 0, read));
                        currentProductProgress.add(read);
                    }
                    logger.fine("End reading from input stream");
                    checkCancelled();
                    logger.fine(String.format(COMPLETE_MESSAGE, currentProduct.getName(), currentStep, file.getFileName(), (System.currentTimeMillis() - start) / 1000));
                }
                logger.fine(String.format("End download for %s", remoteUrl));
            } else {
                logger.fine("File already downloaded");
                logger.fine(String.format(COMPLETE_MESSAGE, currentProduct.getName(), currentStep, file.getFileName(), 0));
                currentProductProgress.add(remoteFileLength);
            }
        } catch (FileNotFoundException fnex) {
            logger.warning(String.format(ERROR_MESSAGE, remoteUrl, "No such file"));
            file = null;
        } catch (InterruptedIOException iioe) {
            logger.severe("Operation timed out");
            throw new IOException("Operation timed out");
        } catch (Exception ex) {
            ex.printStackTrace();
            String errMsg = String.format(ERROR_MESSAGE, remoteUrl, ex.getMessage());
            logger.severe(errMsg);
            throw new IOException(errMsg);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            subActivityEnd(subActivity);
        }
        return FileUtilities.ensurePermissions(file);
    }

}
