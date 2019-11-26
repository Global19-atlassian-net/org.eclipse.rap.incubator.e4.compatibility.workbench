/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Tasktop Technologies - fix for bug 327396
 *******************************************************************************/
package org.eclipse.ui.internal;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.rap.rwt.RWT;
import org.eclipse.rap.rwt.service.SettingStore;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.XMLMemento;
import org.eclipse.ui.statushandlers.IStatusAdapterConstants;
import org.eclipse.ui.statushandlers.StatusAdapter;
import org.eclipse.ui.statushandlers.StatusManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleListener;

/**
 * A working set manager stores working sets and provides property change
 * notification when a working set is added or removed. Working sets are
 * persisted whenever one is added or removed.
 *
 * @see IWorkingSetManager
 * @since 2.0
 */
public class WorkingSetManager extends AbstractWorkingSetManager implements
		IWorkingSetManager, BundleListener {

	// Working set persistence
	public static final String WORKING_SET_STATE_FILENAME = "workingsets.xml"; //$NON-NLS-1$

	// RAP [rh]: key for storing state into setting store
	private static final String KEY_WORKING_SET_MANAGER_STATE = WorkingSetManager.class.getName() + "#XMLMemento";

	private boolean restoreInProgress;

	private boolean savePending;

	public WorkingSetManager(BundleContext context) {
		super(context);
	}

	@Override
	public void addRecentWorkingSet(IWorkingSet workingSet) {
		internalAddRecentWorkingSet(workingSet);
		saveState();
	}

	@Override
	public void addWorkingSet(IWorkingSet workingSet) {
		super.addWorkingSet(workingSet);
		saveState();
	}

// RAP [rh]	unused code
//	/**
//	 * Returns the file used as the persistence store, or <code>null</code> if
//	 * there is no available file.
//	 *
//	 * @return the file used as the persistence store, or <code>null</code>
//	 */
//	private File getWorkingSetStateFile() {
//		IPath path = WorkbenchPlugin.getDefault().getDataLocation();
//		if (path == null) {
//			return null;
//		}
//		path = path.append(WORKING_SET_STATE_FILENAME);
//		return path.toFile();
//	}

	@Override
	public void removeWorkingSet(IWorkingSet workingSet) {
		if (internalRemoveWorkingSet(workingSet)) {
			saveState();
		}
	}

	/**
	 * Reads the persistence store and creates the working sets stored in it.
	 */
	public void restoreState() {
		// RAP [rh] reading from state-location does not work with multiple sessions
//				File stateFile = getWorkingSetStateFile();
		SettingStore settingStore = RWT.getSettingStore();
		String state = settingStore.getAttribute(KEY_WORKING_SET_MANAGER_STATE);

//				if (stateFile != null && stateFile.exists()) {
		if (state != null) {
			try {
				// RAP [rh] replaced InputStream/Reader with Reader that reads from SettingStore
				// attribute
//						FileInputStream input = new FileInputStream(stateFile);
//		        BufferedReader reader = new BufferedReader(
//		             new InputStreamReader(input, "utf-8")); //$NON-NLS-1$
				StringReader reader = new StringReader(state);

				IMemento memento = XMLMemento.createReadRoot(reader);
				restoreWorkingSetState(memento);
				restoreMruList(memento);
				reader.close();
				// RAP [rh] unreachable catch-block, since no IOException is thrown
//					} catch (IOException e) {
//						handleInternalError(
//								e,
//								WorkbenchMessages.get().ProblemRestoringWorkingSetState_title,
//								WorkbenchMessages.get().ProblemRestoringWorkingSetState_message);
			} catch (WorkbenchException e) {
				handleInternalError(e, WorkbenchMessages.get().ProblemRestoringWorkingSetState_title,
						WorkbenchMessages.get().ProblemRestoringWorkingSetState_message);
			}
		}
			}

	/**
	 * Saves the working sets in the persistence store
	 */
	private void saveState() {

// RAP [rh]	cannot save state to state-location in multi-session environment
//		File stateFile = getWorkingSetStateFile();
//		if (stateFile == null) {
//			return;
//		}
//		try {
//			saveState(stateFile);
//		} catch (IOException e) {
//			stateFile.delete();
//			MessageDialog.openError((Shell) null,
//					WorkbenchMessages.get().ProblemSavingWorkingSetState_title,
//					WorkbenchMessages.get().ProblemSavingWorkingSetState_message);
//		}

// RAP [rh] copied from AbstractWorkingSetManager#saveState(File)	  
		XMLMemento memento = XMLMemento.createWriteRoot(IWorkbenchConstants.TAG_WORKING_SET_MANAGER);
		saveWorkingSetState(memento);
		saveMruList(memento);
// RAP [rh] store state in SettingStore instead
		try {
			StringWriter writer = new StringWriter();
			memento.save(writer);
			writer.close();
			SettingStore settingStore = RWT.getSettingStore();
			settingStore.setAttribute(KEY_WORKING_SET_MANAGER_STATE, writer.toString());
		} catch (IOException ioe) {
			WorkbenchPlugin.log(WorkbenchMessages.get().ProblemSavingWorkingSetState_title, ioe);
		}
	}

	/**
	 * Persists all working sets and fires a property change event for the
	 * changed working set. Should only be called by
	 * org.eclipse.ui.internal.WorkingSet.
	 *
	 * @param changedWorkingSet
	 *            the working set that has changed
	 * @param propertyChangeId
	 *            the changed property. one of CHANGE_WORKING_SET_CONTENT_CHANGE
	 *            and CHANGE_WORKING_SET_NAME_CHANGE
	 */
	@Override
	public void workingSetChanged(IWorkingSet changedWorkingSet,
			String propertyChangeId, Object oldValue) {
		saveState();
		super.workingSetChanged(changedWorkingSet, propertyChangeId, oldValue);
	}

	/**
	 * Show and Log the exception using StatusManager.
	 */
	private void handleInternalError(Exception exp, String title, String message) {
		Status status = new Status(IStatus.ERROR, WorkbenchPlugin.PI_WORKBENCH,
				message, exp);
		StatusAdapter sa = new StatusAdapter(status);
		sa.setProperty(IStatusAdapterConstants.TITLE_PROPERTY, title);
		StatusManager.getManager().handle(sa,
				StatusManager.SHOW | StatusManager.LOG);
	}

	@Override
	protected void restoreWorkingSetState(IMemento memento) {
		super.restoreWorkingSetState(memento);
		saveState();
	}
}
