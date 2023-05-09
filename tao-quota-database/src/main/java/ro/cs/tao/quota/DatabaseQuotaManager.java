package ro.cs.tao.quota;

import ro.cs.tao.component.SystemVariable;
import ro.cs.tao.execution.persistence.ExecutionTaskProvider;
import ro.cs.tao.persistence.EOProductProvider;
import ro.cs.tao.persistence.PersistenceException;
import ro.cs.tao.persistence.UserProvider;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.user.User;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.MemoryUnit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.Principal;

/**
 * Quota manager implementation based on database stored user quota
 * 
 * @author Lucian Barbulescu
 *
 */
public class DatabaseQuotaManager implements QuotaManager {

	private final UserProvider userProvider;
	private final ExecutionTaskProvider taskProvider;
	private final EOProductProvider productProvider;

	/**
	 * Constructor.
	 */
	public DatabaseQuotaManager() {
		this.userProvider = SpringContextBridge.services().getService(UserProvider.class);
		this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
		this.productProvider = SpringContextBridge.services().getService(EOProductProvider.class);
	}

	@Override
	public boolean checkUserInputQuota(Principal principal) throws QuotaException {
		final User user = getUser(principal);
		final long targetQuota = user.getInputQuota();
		final long actualQuota = user.getActualInputQuota();
		return targetQuota == -1 || targetQuota > actualQuota;
	}

	@Override
	public boolean checkUserInputQuota(Principal principal, long addedQuota) throws QuotaException {
		final User user = getUser(principal);
		final long targetQuota = user.getInputQuota();
		final long actualQuota = user.getActualInputQuota() + (addedQuota / MemoryUnit.MB.value());
		return targetQuota == -1 || targetQuota > actualQuota;
	}

	
	@Override
	public boolean checkUserProcessingQuota(Principal principal) throws QuotaException {
		final User user = getUser(principal);
		final long targetQuota = user.getProcessingQuota();
		final long actualQuota = user.getActualProcessingQuota();
		return targetQuota == -1 || targetQuota > actualQuota;
	}

	@Override
	public boolean checkUserProcessingMemory(Principal principal, int memory) throws QuotaException {
		final User user = getUser(principal);
		
		// check if the user has any memory quota
		if(user.getMemoryQuota() == -1) {
			return true;
		}
		final int requiredMemory = memory + this.taskProvider.getMemoryForUser(user.getUsername());
		
		return (user.getMemoryQuota() - requiredMemory) > 0;
	}

	@Override
	public int getAvailableCpus(Principal principal) throws QuotaException {
		final User user = getUser(principal);
		
		// check if the user has any CPU quota
		if (user.getCpuQuota() == -1) {
			return -1;
		}
		
		return Math.max(0, user.getCpuQuota() - this.taskProvider.getCPUsForUser(user.getUsername()));
	}

	@Override
	public void updateUserInputQuota(Principal principal) throws QuotaException {
		final User user = getUser(principal);

		// nothing to do if the user has no limit on the input disk quota
		if (user.getInputQuota() == -1) {
			return;
		}
		try {
			// compute and update the quota based on the database information
			// get the location of the shared folder
			final String location = Paths.get(SystemVariable.SHARED_WORKSPACE.value()).toUri().toString();

			// compute the size of all public products assigned to the user
			final long actualInputQuota = this.productProvider.getUserInputProductsSize(principal.getName(), location)
					/ MemoryUnit.MB.value();

			// update the user info
			this.userProvider.updateInputQuota(principal.getName(), (int)actualInputQuota);

		} catch (PersistenceException e) {
			throw new QuotaException(String.format("Cannot update input quota for the user '%s'. Reason: %s",
					principal.getName(), e.getMessage()), e);
		}
	}

	@Override
	public void updateUserProcessingQuota(Principal principal) throws QuotaException {

		final User user = getUser(principal);

		// nothing to do if the user has no limit on the processing disk quota
		if (user.getProcessingQuota() == -1) {
			return;
		}
		try {

			// compute and update the quota based on the database information
			 final String userWorkspace = SystemVariable.USER_WORKSPACE.value();
			 // compute used space in MB
			final long usedSpace = FileUtilities.folderSize(new File(userWorkspace).toPath()) / MemoryUnit.MB.value();

			// update the user info
			this.userProvider.updateProcessingQuota(principal.getName(), (int)usedSpace);
		} catch (PersistenceException | IOException e) {
			throw new QuotaException(String.format("Cannot update input quota for the user '%s'. Reason: %s",
					principal.getName(), e.getMessage()), e);
		}
	}

	/** 
	 * Get he user's details from the database
	 * @param principal the used security data
	 * @return the user structure
	 * @throws QuotaException if the user does not exists in the database
	 */
	private User getUser(Principal principal) throws QuotaException {
		if (principal == null) {
			throw new IllegalArgumentException("[principal] null");
		}
		final String userName = principal.getName();
		final User user = this.userProvider.getByName(userName);
		if (user == null) {
			throw new QuotaException(String.format("User '%s' not found", userName));
		}
		return user;
	}
}
