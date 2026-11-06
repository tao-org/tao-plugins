package ro.cs.tao.ftp;

import com.github.robtimus.filesystems.ftp.FTPEnvironment;
import com.github.robtimus.filesystems.ftp.FTPSEnvironment;
import ro.cs.tao.workspaces.RepositoryType;

/**
 * FTPSStorageService is a concrete implementation of FTPBaseService that provides
 * file storage services using the FTPS protocol. This class customizes the behavior
 * of the base class to work specifically with the FTPS standard.
 *
 * This service handles secure file operations over FTPS, supporting functionalities
 * defined by the base service, such as storing, retrieving, and managing files and
 * directories. It ensures compatibility with repositories that utilize the FTPS protocol.
 *
 * Key responsibilities and behaviors include:
 * - Specifying the FTPS protocol as the intended scheme for the implementation.
 * - Providing an environment configuration (FTPSEnvironment) tailored for FTPS-based interactions.
 *
 * The service relies on initializing an FTPS-specific environment, including credentials,
 * connection timeout settings, and encoding autodetection. The protocol-specific configuration
 * is passed to FTPBaseService for interaction with FTPS-compliant repositories.
 */
public class FTPSStorageService extends FTPBaseService {

    @Override
    public boolean isIntendedFor(RepositoryType repositoryType) {
        return RepositoryType.FTPS.equals(repositoryType);
    }

    @Override
    protected String protocol() {
        return "ftps";
    }

    @Override
    protected FTPEnvironment newEnvironment() {
        return new FTPSEnvironment();
    }
}
