package ro.cs.tao.ftp;

import com.github.robtimus.filesystems.ftp.FTPEnvironment;

/**
 * A service class for handling FTP-based file storage operations. This class extends the
 * {@link FTPBaseService} and provides FTP-specific protocol definition and environment configuration.
 *
 * The {@code FTPStorageService} implements the following key functionalities:
 * - Defining the protocol as "ftp" for FTP-based operations.
 * - Instantiating a new FTP environment configuration specific to the service requirements.
 *
 * This class inherits common FTP file operations from {@link FTPBaseService}, such as
 * file storage, deletion, downloading, and folder management. These operations are tailored
 * for communication with FTP servers.
 *
 * Responsibilities of this class:
 * - Specify the FTP protocol to distinguish it from other file storage protocols.
 * - Provide the necessary FTP environment configuration for establishing connections.
 */
public class FTPStorageService extends FTPBaseService {

    @Override
    protected String protocol() {
        return "ftp";
    }

    @Override
    protected FTPEnvironment newEnvironment() {
        return new FTPEnvironment();
    }
}
