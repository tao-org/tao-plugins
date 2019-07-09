package ro.cs.tao.quota;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.logging.Logger;

import ro.cs.tao.component.SystemVariable;
import ro.cs.tao.execution.monitor.MemoryUnit;
import ro.cs.tao.messaging.Messaging;
import ro.cs.tao.messaging.Topics;
import ro.cs.tao.persistence.PersistenceManager;
import ro.cs.tao.persistence.exception.PersistenceException;
import ro.cs.tao.security.SessionStore;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.user.User;
import ro.cs.tao.utils.FileUtilities;

/**
 * Quota manager implementation based on database stored user quota
 * 
 * @author Lucian Barbulescu
 *
 */
public class DatabaseQuotaManager implements QuotaManager {

	private PersistenceManager persistenceManager;

	/**
	 * Constructor.
	 */
	public DatabaseQuotaManager() {
		this.persistenceManager = SpringContextBridge.services().getService(PersistenceManager.class);
	}

	@Override
	public boolean checkUserInputQuota(Principal principal) throws QuotaException {
		if (principal == null) {
			throw new IllegalArgumentException("[principal] null");
		}
		String userName = principal.getName();
		User user = this.persistenceManager.findUserByUsername(userName);
		if (user == null) {
			throw new QuotaException(String.format("User '%s' not found", userName));
		}
		final long targetQuota = user.getInputQuota();
		final long actualQuota = user.getActualInputQuota();
		return targetQuota == -1 || targetQuota > actualQuota;
	}

	@Override
	public boolean checkUserProcessingQuota(Principal principal) throws QuotaException {
		if (principal == null) {
			throw new IllegalArgumentException("[principal] null");
		}
		User user = this.persistenceManager.findUserByUsername(principal.getName());
		if (user == null) {
			throw new QuotaException(String.format("User '%s' not found", principal.getName()));
		}
		final long targetQuota = user.getProcessingQuota();
		final long actualQuota = user.getActualProcessingQuota();
		return targetQuota == -1 || targetQuota > actualQuota;
	}

	@Override
	public boolean checkUserProcessingResources(Principal principal) throws QuotaException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updateUserInputQuota(Principal principal, long addedQuota) throws QuotaException {

		final User user = persistenceManager.findUserByUsername(principal.getName());
		if (user == null) {
			throw new QuotaException(String.format("User '%s' not found", principal.getName()));
		}

		// nothing to do if the user has no limit on the input disk quota
		if (user.getInputQuota() == -1) {
			return true;
		}
		try {

			if (addedQuota == 0) {
				// compute and update the quota based on the database information
				// get the location of the shared folder
				final String location = Paths.get(SystemVariable.SHARED_WORKSPACE.value()).toUri().toString();

				// compute the size of all public products assigned to the user
				final long actualInputQuota = persistenceManager.getUserInputProductsSize(principal.getName(), location)
						/ ((long) MemoryUnit.MEGABYTE.value());

				// update the user info
				persistenceManager.updateUserInputQuota(principal.getName(), actualInputQuota);
				
				return checkUserInputQuota(principal);
			}

			// extract the user's input quota (in MB)
			final long[] quotas = persistenceManager.getUserDiskQuotas(principal.getName());
			
			// update the input quota (in MB)
			final long updatedInputQuota = (quotas[1] * (long) MemoryUnit.MEGABYTE.value() + addedQuota) / (long) MemoryUnit.MEGABYTE.value();
			
			if (updatedInputQuota > quotas[0]) {
				
				// create the error message
				final String msg = String.format("You cannot import more product because you have reached "
						+ "your input disk quota (defined quota: %dMB, used quota: %dMB). Please remove some input products "
						+ "to continue", quotas[0], updatedInputQuota);
				
				// publish the message to the ERROR topic and log it
				Messaging.send(SessionStore.currentContext().getPrincipal(), Topics.ERROR, this, msg);
				Logger.getLogger(this.getClass().getName()).info(msg);
				return false;
			} else {
				// update the quota
				persistenceManager.updateUserInputQuota(principal.getName(), updatedInputQuota);
				
				return true;
			}
		} catch (PersistenceException e) {
			throw new QuotaException(String.format("Cannot update input quota for the user '%s'. Reason: %s",
					principal.getName(), e.getMessage()), e);
		}
	}

	@Override
	public boolean updateUserProcessingQuota(Principal principal, long addedQuota) throws QuotaException {

		final User user = persistenceManager.findUserByUsername(principal.getName());
		if (user == null) {
			throw new QuotaException(String.format("User '%s' not found", principal.getName()));
		}

		// nothing to do if the user has no limit on the processing disk quota
		if (user.getProcessingQuota() == -1) {
			return true;
		}
		try {

			if (addedQuota == 0) {
				// compute and update the quota based on the database information
				 final String userWorkspace = SystemVariable.USER_WORKSPACE.value();
				 // compute used space in MB
				final long usedSpace = FileUtilities.folderSize(new File(userWorkspace).toPath()) / ((long) MemoryUnit.MEGABYTE.value());

				// update the user info
				persistenceManager.updateUserProcessingQuota(principal.getName(), usedSpace);
				
				return checkUserProcessingQuota(principal);
			}

			// extract the user's processing quota (in MB)
			final long[] quotas = persistenceManager.getUserDiskQuotas(principal.getName());
			
			// update the user processing quota (in MB)
			final long updatedProcessingQuota = (quotas[3] * (long) MemoryUnit.MEGABYTE.value() + addedQuota) / (long) MemoryUnit.MEGABYTE.value();
			
			if (updatedProcessingQuota > quotas[2]) {
				
				// create the error message
				final String msg = String.format("The workflow execution cannot start because you have reached "
						+ "your processing disk quota (defined quota: %dGB, used quota: %dGB). Please delete some files from your "
						+ "workspace to continue", quotas[2], updatedProcessingQuota);
				
				// publish the message to the ERROR topic and log it
				Messaging.send(SessionStore.currentContext().getPrincipal(), Topics.ERROR, this, msg);
				Logger.getLogger(this.getClass().getName()).info(msg);
				return false;
			} else {
				// update the quota
				persistenceManager.updateUserProcessingQuota(principal.getName(), updatedProcessingQuota);
				
				return true;
			}
		} catch (PersistenceException | IOException e) {
			throw new QuotaException(String.format("Cannot update input quota for the user '%s'. Reason: %s",
					principal.getName(), e.getMessage()), e);
		}
	}

	@Override
	public boolean updateUserProcessingResources(Principal principal, long addedCpu, long addedMemory)
			throws QuotaException {
		// TODO Auto-generated method stub
		return false;
	}
}
