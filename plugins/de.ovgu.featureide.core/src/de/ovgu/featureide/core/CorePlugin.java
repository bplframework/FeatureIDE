/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2015  FeatureIDE team, University of Magdeburg, Germany
 *
 * This file is part of FeatureIDE.
 * 
 * FeatureIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * FeatureIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with FeatureIDE.  If not, see <http://www.gnu.org/licenses/>.
 *
 * See http://featureide.cs.ovgu.de/ for further information.
 */
package de.ovgu.featureide.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.CheckForNull;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.Signature;
import org.osgi.framework.BundleContext;
import org.prop4j.And;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.prop4j.Or;
import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;

import de.ovgu.featureide.core.builder.ComposerExtensionManager;
import de.ovgu.featureide.core.builder.ExtensibleFeatureProjectBuilder;
import de.ovgu.featureide.core.builder.FeatureProjectNature;
import de.ovgu.featureide.core.builder.IComposerExtension;
import de.ovgu.featureide.core.builder.IComposerExtensionClass;
import de.ovgu.featureide.core.internal.FeatureProject;
import de.ovgu.featureide.core.internal.ProjectChangeListener;
import de.ovgu.featureide.core.job.PrintDocumentationJob;
import de.ovgu.featureide.core.listeners.IConfigurationChangedListener;
import de.ovgu.featureide.core.listeners.ICurrentBuildListener;
import de.ovgu.featureide.core.listeners.ICurrentConfigurationListener;
import de.ovgu.featureide.core.listeners.IFeatureFolderListener;
import de.ovgu.featureide.core.listeners.IProjectListener;
import de.ovgu.featureide.core.signature.ProjectSignatures;
import de.ovgu.featureide.core.signature.ProjectSignatures.SignatureIterator;
import de.ovgu.featureide.core.signature.ProjectStructure;
import de.ovgu.featureide.core.signature.base.AbstractClassSignature;
import de.ovgu.featureide.core.signature.base.AbstractFieldSignature;
import de.ovgu.featureide.core.signature.base.AbstractMethodSignature;
import de.ovgu.featureide.core.signature.base.AbstractSignature;
import de.ovgu.featureide.core.signature.documentation.ContextMerger;
import de.ovgu.featureide.core.signature.documentation.FeatureModuleMerger;
import de.ovgu.featureide.core.signature.documentation.SPLMerger;
import de.ovgu.featureide.core.signature.documentation.VariantMerger;
import de.ovgu.featureide.core.signature.filter.ContextFilter;
import de.ovgu.featureide.fm.core.AbstractCorePlugin;
import de.ovgu.featureide.fm.core.FMCorePlugin;
import de.ovgu.featureide.fm.core.FeatureModel;
import de.ovgu.featureide.fm.core.editing.NodeCreator;
import de.ovgu.featureide.fm.core.io.FeatureModelWriterIFileWrapper;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelWriter;

/**
 * The activator class controls the plug-in life cycle.
 * 
 * @author Constanze Adler
 * @author Marcus Leich
 * @author Tom Brosch
 * @author Thomas Thuem
 */
public class CorePlugin extends AbstractCorePlugin {

	public static final String PLUGIN_ID = "de.ovgu.featureide.core";

	private static final String COMPOSERS_ID = PLUGIN_ID + ".composers";

	private static final String BASE_FEATURE = "Base";

	private static CorePlugin plugin;

	private HashMap<IProject, IFeatureProject> featureProjectMap;

	private LinkedList<IProjectListener> projectListeners = new LinkedList<IProjectListener>();

	private LinkedList<ICurrentConfigurationListener> currentConfigurationListeners = new LinkedList<ICurrentConfigurationListener>();

	private LinkedList<IConfigurationChangedListener> configurationChangedListeners = new LinkedList<IConfigurationChangedListener>();

	private LinkedList<IFeatureFolderListener> featureFolderListeners = new LinkedList<IFeatureFolderListener>();

	private LinkedList<ICurrentBuildListener> currentBuildListeners = new LinkedList<ICurrentBuildListener>();

	private LinkedList<IProject> projectsToAdd = new LinkedList<IProject>();

	private Job job = null;

	private int couterAddProjects = 0;

	/**
	 * add ResourceChangeListener to workspace to track project move/rename
	 * events at the moment project refactoring and
	 */
	private IResourceChangeListener listener;

	@Override
	public String getID() {
		return PLUGIN_ID;
	}

	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;

		featureProjectMap = new HashMap<IProject, IFeatureProject>();
		listener = new ProjectChangeListener();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(listener);
		for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			try {
				if (project.isOpen()) {
					// conversion for old projects
					IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor(COMPOSERS_ID);
					for (IConfigurationElement e : config) {
						if (project.hasNature(e.getAttribute("nature"))) {
							changeOldNature(project, e.getAttribute("ID"));
						}
					}
					if (project.hasNature(FeatureProjectNature.NATURE_ID))
						addProject(project);
				}
			} catch (Exception e) {
				CorePlugin.getDefault().logError(e);
			}
		}

	}

	/**
	 * If the given project has the old FeatureIDE nature, it will be replaced with the actual one.
	 * Also sets the composition tool to the given ID.
	 * 
	 * @param project The project
	 * @param composerID The new composer ID
	 * @throws CoreException
	 */
	private static void changeOldNature(IProject project, String composerID) throws CoreException {
		CorePlugin.getDefault().logInfo(
				"Change old nature to '" + FeatureProjectNature.NATURE_ID + "' and composer to '" + composerID + "' in project '" + project.getName() + "'");
		IProjectDescription description = project.getDescription();
		String[] natures = description.getNatureIds();
		for (int i = 0; i < natures.length; i++)
			if (natures[i].startsWith("FeatureIDE_Core."))
				natures[i] = FeatureProjectNature.NATURE_ID;
		description.setNatureIds(natures);
		project.setDescription(description, null);
		project.setPersistentProperty(IFeatureProject.composerConfigID, composerID);
	}

	public void stop(BundleContext context) throws Exception {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(listener);

		listener = null;
		for (IFeatureProject data : featureProjectMap.values())
			data.dispose();
		featureProjectMap = null;

		plugin = null;
		super.stop(context);
	}

	public void addProject(IProject project) {
		if (featureProjectMap.containsKey(project) || !project.isOpen())
			return;

		IFeatureProject data = new FeatureProject(project);
		featureProjectMap.put(project, data);
		logInfo("Feature project " + project.getName() + " added");

		for (IProjectListener listener : projectListeners)
			listener.projectAdded(data);

		final IStatus status = isComposable(project);

		if (status.getCode() != Status.OK) {
			for (IStatus child : status.getChildren()) {
				data.createBuilderMarker(data.getProject(), child.getMessage(), -1, IMarker.SEVERITY_ERROR);
			}
			data.createBuilderMarker(data.getProject(), status.getMessage(), -1, IMarker.SEVERITY_ERROR);
		}
	}

	public IStatus isComposable(IProject project) {
		IProjectDescription description = null;
		try {
			description = project.getDescription();
		} catch (CoreException e) {
			logError(e);
		}
		return isComposable(description);
	}

	public IStatus isComposable(IProjectDescription description) {
		if (description != null) {
			final String composerID = getComposerID(description);
			if (composerID != null) {
				final IComposerExtension composer = ComposerExtensionManager.getInstance().getComposerById(composerID);
				if (composer != null) {
					return composer.isComposable();
				} else {
					return new Status(Status.ERROR, PLUGIN_ID, "No Composer Found for ID " + composerID);
				}
			} else {
				return new Status(Status.ERROR, PLUGIN_ID, "No Composer Found in Description.");
			}
		} else {
			return new Status(Status.ERROR, PLUGIN_ID, "No Project Description Found.");
		}
	}

	@CheckForNull
	public String getComposerID(IProjectDescription description) {
		for (ICommand command : description.getBuildSpec()) {
			//TODO Make Extension Point for additional Builders
			if (ExtensibleFeatureProjectBuilder.BUILDER_ID.equals(command.getBuilderName())
					|| "de.ovgu.featureide.core.mpl.MSPLBuilder".equals(command.getBuilderName())) {
				return command.getArguments().get("composer");
			}
		}
		return null;
	}

	public void removeProject(IProject project) {
		if (!featureProjectMap.containsKey(project))
			return;

		IFeatureProject featureProject = featureProjectMap.remove(project);
		logInfo(project.getName() + " removed");

		for (IProjectListener listener : projectListeners)
			listener.projectRemoved(featureProject);
	}

	public void addCurrentBuildListener(ICurrentBuildListener listener) {
		if (!currentBuildListeners.contains(listener))
			currentBuildListeners.add(listener);
	}

	public void removeCurrentBuildListener(ICurrentBuildListener listener) {
		currentBuildListeners.remove(listener);
	}

	public void fireBuildUpdated(IFeatureProject featureProject) {
		for (ICurrentBuildListener listener : currentBuildListeners)
			listener.updateGuiAfterBuild(featureProject, featureProject.getCurrentConfiguration());
	}

	public void addProjectListener(IProjectListener listener) {
		if (!projectListeners.contains(listener))
			projectListeners.add(listener);
	}

	public void removeProjectListener(IProjectListener listener) {
		projectListeners.remove(listener);
	}

	public void addCurrentConfigurationListener(ICurrentConfigurationListener listener) {
		if (!currentConfigurationListeners.contains(listener))
			currentConfigurationListeners.add(listener);
	}

	public void addConfigurationChangedListener(IConfigurationChangedListener listener) {
		if (!configurationChangedListeners.contains(listener))
			configurationChangedListeners.add(listener);
	}

	public void removeCurrentConfigurationListener(ICurrentConfigurationListener listener) {
		currentConfigurationListeners.remove(listener);
	}

	public void fireCurrentConfigurationChanged(IFeatureProject featureProject) {
		for (ICurrentConfigurationListener listener : currentConfigurationListeners)
			listener.currentConfigurationChanged(featureProject);
	}

	public void fireConfigurationChanged(IFeatureProject featureProject) {
		for (IConfigurationChangedListener listener : configurationChangedListeners)
			listener.configurationChanged(featureProject);
	}

	public void addFeatureFolderListener(IFeatureFolderListener listener) {
		if (!featureFolderListeners.contains(listener))
			featureFolderListeners.add(listener);
	}

	public void removeFeatureFolderListener(IFeatureFolderListener listener) {
		featureFolderListeners.remove(listener);
	}

	public void fireFeatureFolderChanged(IFolder folder) {
		for (IFeatureFolderListener listener : featureFolderListeners)
			listener.featureFolderChanged(folder);
	}

	/**
	 * Setups the projects structure.<br>
	 * Starts composer specific changes of the project structure,
	 * after adding the FeatureIDE nature to a project.
	 */
	public static void setupProject(final IProject project, String compositionToolID, final String sourcePath, final String configPath, final String buildPath) {
		setupFeatureProject(project, compositionToolID, sourcePath, configPath, buildPath, false);

		IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor(COMPOSERS_ID);
		try {
			for (IConfigurationElement e : config) {
				if (e.getAttribute("id").equals(compositionToolID)) {
					final Object o = e.createExecutableExtension("class");
					if (o instanceof IComposerExtensionClass) {

						ISafeRunnable runnable = new ISafeRunnable() {
							public void handleException(Throwable e) {
								getDefault().logError(e);
							}

							public void run() throws Exception {
								runProjectConversion(project, sourcePath, configPath, buildPath, (IComposerExtensionClass) o);
							}
						};
						SafeRunner.run(runnable);
					}
					break;
				}
			}
		} catch (CoreException e) {
			getDefault().logError(e);
		}

	}

	/**
	 * Composer specific changes of the project structure,
	 * after adding the FeatureIDE nature to a project.<br>
	 * Moves the files of the source folder to the features folder(composer specific)<br>
	 * Creates a configuration file, where the base feature is selected, to automatically build the project.
	 */
	protected static void runProjectConversion(IProject project, String sourcePath, String configPath, String buildPath, IComposerExtensionClass composer)
			throws IOException {
		try {
			if (composer.hasSourceFolder() || composer.hasFeatureFolder()) {
				project.getFolder(buildPath).deleteMarkers(null, true, IResource.DEPTH_INFINITE);

				IFolder source = project.getFolder(buildPath);
				IFolder destination = !"".equals(sourcePath) ? project.getFolder(sourcePath).getFolder(BASE_FEATURE) : null;
				if (!composer.postAddNature(source, destination) && !"".equals(sourcePath)) {
					if (!composer.hasFeatureFolder()) {
						/** if project does not use feature folder, use the source path directly **/
						destination = project.getFolder(sourcePath);
					}
					if (!destination.exists()) {
						destination.create(false, true, null);
					}
					/** moves all files of the old source folder to the destination **/
					for (IResource res : source.members()) {
						res.move(destination.getFile(res.getName()).getFullPath(), true, null);
					}
				}
			}
		} catch (CoreException e) {
			CorePlugin.getDefault().logError(e);
		}
		/**
		 * create a configuration to automatically build
		 * the project after adding the FeatureIDE nature
		 **/
		IFile configFile = project.getFolder(configPath).getFile(project.getName().split("[-]")[0] + "." + composer.getConfigurationExtension());
		FileWriter fw = null;
		try {
			fw = new FileWriter(configFile.getRawLocation().toFile());
			fw.write(BASE_FEATURE);

			configFile.create(null, true, null);
			configFile.refreshLocal(IResource.DEPTH_ZERO, null);
		} catch (CoreException e) {
			// Avoid file exist error
			// Has no negative effect
		} finally {
			if (fw != null) {
				fw.close();
			}
		}
	}

	/**
	 * Setups the project.<br>
	 * Creates folders<br>
	 * Adds the compiler(if necessary)<br>
	 * Adds the FeatureIDE nature<br>
	 * Creates the feature model
	 * 
	 * @param addCompiler <code>false</code> if the project already has a compiler
	 */
	public static void setupFeatureProject(final IProject project, String compositionToolID, final String sourcePath, final String configPath,
			final String buildPath, boolean addCompiler) {
		createProjectStructure(project, sourcePath, configPath, buildPath);

		if (addCompiler) {
			try {
				IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor(COMPOSERS_ID);
				for (IConfigurationElement e : config) {
					if (e.getAttribute("id").equals(compositionToolID)) {
						final Object o = e.createExecutableExtension("class");
						if (o instanceof IComposerExtensionClass) {

							ISafeRunnable runnable = new ISafeRunnable() {
								public void handleException(Throwable e) {
									getDefault().logError(e);
								}

								public void run() throws Exception {
									((IComposerExtensionClass) o).addCompiler(project, sourcePath, configPath, buildPath);

									final String path = project.getFolder(configPath).getRawLocation() + "/default."
											+ ((IComposerExtensionClass) o).getConfigurationExtension();
									new File(path).createNewFile();
									project.getFolder(configPath).refreshLocal(IResource.DEPTH_INFINITE, null);
								}
							};
							SafeRunner.run(runnable);
						}
						break;
					}
				}
			} catch (CoreException e) {
				getDefault().logError(e);
			}
		}
		try {
			project.setPersistentProperty(IFeatureProject.composerConfigID, compositionToolID);
			project.setPersistentProperty(IFeatureProject.buildFolderConfigID, buildPath);
			project.setPersistentProperty(IFeatureProject.configFolderConfigID, configPath);
			project.setPersistentProperty(IFeatureProject.sourceFolderConfigID, sourcePath);
		} catch (CoreException e) {
			CorePlugin.getDefault().logError("Could not set persistant property", e);
		}
		addFeatureNatureToProject(project);
	}

	private static void addFeatureNatureToProject(IProject project) {
		try {
			// check if the nature was already added
			if (!project.isAccessible() || project.hasNature(FeatureProjectNature.NATURE_ID)) {
				return;
			}

			// add the FeatureIDE nature
			CorePlugin.getDefault().logInfo("Add Nature (" + FeatureProjectNature.NATURE_ID + ") to " + project.getName());
			IProjectDescription description = project.getDescription();
			String[] natures = description.getNatureIds();
			String[] newNatures = new String[natures.length + 1];
			System.arraycopy(natures, 0, newNatures, 0, natures.length);
			newNatures[natures.length] = FeatureProjectNature.NATURE_ID;
			description.setNatureIds(newNatures);
			project.setDescription(description, null);
		} catch (CoreException e) {
			CorePlugin.getDefault().logError(e);
		}
	}

	public static IFolder createFolder(IProject project, String name) {
		if ("".equals(name)) {
			return null;
		}
		String[] names = name.split("[/]");
		IFolder folder = null;
		for (String folderName : names) {
			if (folder == null) {
				folder = project.getFolder(folderName);
			} else {
				folder = folder.getFolder(folderName);
			}
			try {
				if (!folder.exists()) {
					folder.create(false, true, null);
				}
			} catch (CoreException e) {
				CorePlugin.getDefault().logError(e);
			}
		}
		return folder;
	}

	/**
	 * Creates the source-, features- and build-folder at the given paths.<br>
	 * Also creates the bin folder if necessary.<br>
	 * Creates the default feature model.
	 * 
	 * @param project
	 * @param sourcePath
	 * @param configPath
	 * @param buildPath
	 */
	private static void createProjectStructure(IProject project, String sourcePath, String configPath, String buildPath) {
		try {
			/** just create the bin folder if project has only the FeatureIDE Nature **/
			if (project.getDescription().getNatureIds().length == 1 && project.hasNature(FeatureProjectNature.NATURE_ID)) {
				if ("".equals(buildPath) && "".equals(sourcePath)) {
					createFolder(project, "bin");
				}
			}
		} catch (CoreException e) {
			getDefault().logError(e);
		}
		createFolder(project, sourcePath);
		createFolder(project, configPath);
		createFolder(project, buildPath);
		FeatureModel featureModel = new FeatureModel();
		featureModel.initFMComposerExtension(project);
		featureModel.createDefaultValues(project.getName());
		try {
			new FeatureModelWriterIFileWrapper(new XmlFeatureModelWriter(featureModel)).writeToFile(project.getFile("model.xml"));
		} catch (CoreException e) {
			CorePlugin.getDefault().logError("Error while creating feature model", e);
		}

	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static CorePlugin getDefault() {
		return plugin;
	}

	/**
	 * returns an unmodifiable Collection of all ProjectData items, or <code>null</code> if plugin is not loaded
	 * 
	 * @return
	 */
	public static Collection<IFeatureProject> getFeatureProjects() {
		if (getDefault() == null)
			return null;
		return Collections.unmodifiableCollection(getDefault().featureProjectMap.values());
	}

	/**
	 * returns the ProjectData object associated with the given resource
	 * 
	 * @param res
	 * @return <code>null</code> if there is no associated project, no active
	 *         instance of this plug-in or resource is the workspace root
	 */
	@CheckForNull
	public static IFeatureProject getFeatureProject(IResource res) {
		if (res == null) {
			getDefault().logWarning("No resource given while getting the project data");
			return null;
		}
		IProject prj = res.getProject();
		if (prj == null) {
			return null;
		}
		return getDefault().featureProjectMap.get(prj);
	}

	public static boolean hasProjectData(IResource res) {
		return getFeatureProject(res) != null;
	}

	/**
	 * @return A list of all valid configuration extensions
	 */
	public LinkedList<String> getConfigurationExtensions() {
		LinkedList<String> extensions = new LinkedList<String>();
		extensions.add("config");
		extensions.add("equation");
		extensions.add("expression");
		return extensions;
	}

	/**
	 * A linear job to add a project. This is necessary if many projects will be add at the same time.
	 * 
	 * @param project
	 */
	public void addProjectToList(IProject project) {
		if (projectsToAdd.contains(project)) {
			return;
		}
		if (featureProjectMap.containsKey(project) || !project.isOpen()) {
			return;
		}

		projectsToAdd.add(project);
		if (job == null) {
			job = new Job("Add Project") {
				@Override
				public IStatus run(IProgressMonitor monitor) {
					addProjects(monitor);
					monitor.beginTask("", 1);
					return Status.OK_STATUS;
				}
			};
			job.setPriority(Job.SHORT);
			job.schedule();
		}

		if (job.getState() == Job.NONE) {
			couterAddProjects = 0;
			job.schedule();
		}
	}

	protected void addProjects(IProgressMonitor monitor) {
		if (projectsToAdd.isEmpty()) {
			monitor.done();
			return;
		}

		while (!projectsToAdd.isEmpty()) {
			IProject project = projectsToAdd.getFirst();
			projectsToAdd.remove(project);

			monitor.beginTask("", projectsToAdd.size() + couterAddProjects);
			monitor.worked(++couterAddProjects);
			monitor.subTask("Add project " + project.getName());

			addProject(project);

		}
		addProjects(monitor);
	}

	public List<CompletionProposal> extendedModules_getCompl(IFeatureProject featureProject, String featureName) {
		final LinkedList<CompletionProposal> ret_List = new LinkedList<CompletionProposal>();
		final ProjectSignatures signatures = featureProject.getProjectSignatures();

		if (signatures != null) {
			SignatureIterator it = signatures.iterator();
			it.addFilter(new ContextFilter(featureName, signatures));

			while (it.hasNext()) {
				AbstractSignature curMember = it.next();
				CompletionProposal pr = null;

				if (curMember instanceof AbstractMethodSignature) {
					pr = CompletionProposal.create(CompletionProposal.METHOD_REF, 0);
					final AbstractMethodSignature methSig = (AbstractMethodSignature) curMember;
					final List<String> sig = methSig.getParameterTypes();

					//TODO differentiate between possible types
					char[][] c = new char[][] { {} };
					if (sig.size() > 0) {
						c = new char[sig.size()][];
						int i = 0;
						for (String parameterType : sig) {
							String parameterTypeToChar = "L" + parameterType + ";";
							c[i++] = parameterTypeToChar.toCharArray();
						}
					}

					String returnType = "L" + methSig.getReturnType() + ";";
					pr.setSignature(Signature.createMethodSignature(c, returnType.toCharArray()));
					String declType = "L" + methSig.getFullName().replaceAll("." + methSig.getName(), "") + ";";
					pr.setDeclarationSignature(declType.toCharArray());
				} else if (curMember instanceof AbstractFieldSignature) {
					pr = CompletionProposal.create(CompletionProposal.FIELD_REF, 0);
				} else if (curMember instanceof AbstractClassSignature) {
					pr = CompletionProposal.create(CompletionProposal.TYPE_REF, 0);
					pr.setSignature(Signature.createTypeSignature(curMember.getFullName(), true).toCharArray());
				}

				if (pr != null) {
					pr.setFlags(getFlagOfSignature(curMember));
					pr.setName(curMember.getName().toCharArray());
					pr.setCompletion(curMember.getName().toCharArray());

					ret_List.add(pr);
				}
			}
		}
		return ret_List;
	}

	private int getFlagOfSignature(AbstractSignature element) {
		if (element instanceof AbstractMethodSignature) {
			//TODO add constructor icon
			switch (((AbstractMethodSignature) element).getVisibilty()) {
			case AbstractSignature.VISIBILITY_DEFAULT:
				return Flags.AccDefault;
			case AbstractSignature.VISIBILITY_PRIVATE:
				return Flags.AccPrivate;
			case AbstractSignature.VISIBILITY_PROTECTED:
				return Flags.AccProtected;
			case AbstractSignature.VISIBILITY_PUBLIC:
				return Flags.AccPublic;
			}
		} else if (element instanceof AbstractFieldSignature) {
			switch (((AbstractFieldSignature) element).getVisibilty()) {
			case AbstractSignature.VISIBILITY_DEFAULT:
				return Flags.AccDefault;
			case AbstractSignature.VISIBILITY_PRIVATE:
				return Flags.AccPrivate;
			case AbstractSignature.VISIBILITY_PROTECTED:
				return Flags.AccProtected;
			case AbstractSignature.VISIBILITY_PUBLIC:
				return Flags.AccPublic;
			}
		}
		return 0;
	}

	public ProjectStructure extendedModules_getStruct(final IFeatureProject project, final String featureName) {
		final ProjectSignatures signatures = project.getProjectSignatures();
		if (signatures != null) {
			SignatureIterator it = signatures.iterator();
			//TODO check
			if (featureName != null) {
				it.addFilter(new ContextFilter(featureName, signatures));
			}
			return new ProjectStructure(it);
		}
		return null;
	}

	public void buildContextDocumentation(List<IProject> pl, String options, String featureName) {
		final PrintDocumentationJob.Arguments args = new PrintDocumentationJob.Arguments("Docu_Context_" + featureName, options.split("\\s+"),
				new ContextMerger(), featureName);

		FMCorePlugin.getDefault().startJobs(pl, args, true);
	}

	public void buildVariantDocumentation(List<IProject> pl, String options) {
		final PrintDocumentationJob.Arguments args = new PrintDocumentationJob.Arguments("Docu_Variant", options.split("\\s+"), new VariantMerger(), null);

		FMCorePlugin.getDefault().startJobs(pl, args, true);
	}

	public void buildFeatureDocumentation(List<IProject> pl, String options, String featureName) {
		final PrintDocumentationJob.Arguments args = new PrintDocumentationJob.Arguments("Docu_Feature_" + featureName, options.split("\\s+"),
				new FeatureModuleMerger(), featureName);

		FMCorePlugin.getDefault().startJobs(pl, args, true);
	}

	public void buildSPLDocumentation(List<IProject> pl, String options) {
		final PrintDocumentationJob.Arguments args = new PrintDocumentationJob.Arguments("Docu_SPL", options.split("\\s+"), new SPLMerger(), null);

		FMCorePlugin.getDefault().startJobs(pl, args, true);
	}

	public void removeFeatures(IProject project, IFeatureProject data, Collection<String> features) {
		try {
			removeFeatures(data.getFeatureModel(), features);
		} catch (TimeoutException e) {
			CorePlugin.getDefault().logError(e);
		}
	}

	private static class DeprecatedFeature implements Comparable<DeprecatedFeature> {
		private final String feature;

		private int positiveCount;
		private int negativeCount;

		public DeprecatedFeature(String feature) {
			this.feature = feature;
			positiveCount = 0;
			negativeCount = 0;
		}

		public String getFeature() {
			return feature;
		}

		@Override
		public int compareTo(DeprecatedFeature arg0) {
			return (int) Math.signum(arg0.getClauseCount() - getClauseCount());
		}

		public int getClauseCount() {
			return positiveCount * negativeCount;
		}

		public void incPositive() {
			positiveCount++;
		}

		public void incNegative() {
			negativeCount++;
		}

		public void decPositive() {
			positiveCount--;
		}

		public void decNegative() {
			negativeCount--;
		}

		@Override
		public boolean equals(Object arg0) {
			return (arg0 instanceof DeprecatedFeature) && feature.equals(((DeprecatedFeature) arg0).feature);
		}

		@Override
		public int hashCode() {
			return feature.hashCode();
		}

		@Override
		public String toString() {
			return feature + ": " + getClauseCount();
		}
	}

	private static class DeprecatedFeatureMap {

		private final HashMap<String, DeprecatedFeature> map;

		private int globalMixedClauseCount = 0;

		public DeprecatedFeatureMap(Collection<String> features) {
			map = new HashMap<>(features.size() << 1);
			for (String curFeature : features) {
				map.put(curFeature, new DeprecatedFeature(curFeature));
			}
		}

		public DeprecatedFeature next() {
			final Collection<DeprecatedFeature> values = map.values();

			DeprecatedFeature smallestFeature = null;
			if (!values.isEmpty()) {
				final Iterator<DeprecatedFeature> it = values.iterator();
				smallestFeature = it.next();
				while (it.hasNext()) {
					final DeprecatedFeature next = it.next();
					if (next.compareTo(smallestFeature) > 0) {
						smallestFeature = next;
					}
				}
				return map.remove(smallestFeature.getFeature());
			}
			return new DeprecatedFeature(null);
		}

		public boolean isEmpty() {
			return map.isEmpty();
		}

		public int getSize() {
			return map.size();
		}
		
		public DeprecatedFeature get(Object var) {
			return map.get(var);
		}

		public int getGlobalMixedClauseCount() {
			return globalMixedClauseCount;
		}

		public void incGlobalMixedClauseCount() {
			globalMixedClauseCount++;
		}

		public void decGlobalMixedClauseCount() {
			globalMixedClauseCount--;
		}
	}

	private static class Clause {

		private static final class LiteralComparator implements Comparator<Literal> {
			@Override
			public int compare(Literal arg0, Literal arg1) {
				if (arg0.positive == arg1.positive) {
					return ((String) arg0.var).compareTo((String) arg1.var);
				} else if (arg0.positive) {
					return -1;
				} else {
					return 1;
				}
			}
		}

		private static final LiteralComparator literalComparator = new LiteralComparator();

		private Literal[] literals;
		private int relevance;

		public static Clause createClause(DeprecatedFeatureMap map, Literal[] newLiterals, String curFeature) {
			final HashSet<Literal> literalSet = new HashSet<>(newLiterals.length << 1);
			for (Literal literal : newLiterals) {
				if (!curFeature.equals(literal.var)) {
					final Literal negativeliteral = literal.clone();
					negativeliteral.flip();

					if (literalSet.contains(negativeliteral)) {
						return null;
					} else {
						literalSet.add(literal);
					}
				}
			}

			final Clause clause = new Clause(literalSet.toArray(new Literal[0]));
			clause.computeRelevance(map);
			return clause;
		}

		public static Clause createClause(DeprecatedFeatureMap map, Literal[] newLiterals) {
			final HashSet<Literal> literalSet = new HashSet<>(newLiterals.length << 1);
			for (Literal literal : newLiterals) {
				final Literal negativeliteral = literal.clone();
				negativeliteral.flip();

				if (literalSet.contains(negativeliteral)) {
					return null;
				} else {
					literalSet.add(literal);
				}
			}

			final Clause clause = new Clause(literalSet.toArray(new Literal[0]));
			clause.computeRelevance(map);
			return clause;
		}

		public static Clause createClause(DeprecatedFeatureMap map, Literal newLiteral) {
			final Clause clause = new Clause(new Literal[] { newLiteral });
			final DeprecatedFeature df = map.get(newLiteral.var);
			if (df != null) {
				clause.relevance++;
			}
			return clause;
		}

		private Clause(Literal[] literals) {
			this.literals = literals;
			Arrays.sort(this.literals, literalComparator);
			this.relevance = 0;
		}

		public Literal[] getLiterals() {
			return literals;
		}

		private void computeRelevance(DeprecatedFeatureMap map) {
			for (Literal literal : literals) {
				final DeprecatedFeature df = map.get(literal.var);
				if (df != null) {
					relevance++;
					if (literal.positive) {
						df.incPositive();
					} else {
						df.incNegative();
					}
				}
			}
			if (relevance > 0 && relevance < literals.length) {
				map.incGlobalMixedClauseCount();
			}
		}

		public void delete(DeprecatedFeatureMap map) {
			if (literals != null && literals.length > 1) {
				for (Literal literal : literals) {
					final DeprecatedFeature df = map.get(literal.var);
					if (df != null) {
						if (literal.positive) {
							df.decPositive();
						} else {
							df.decNegative();
						}
					}
				}
				if (relevance > 0 && relevance < literals.length) {
					map.decGlobalMixedClauseCount();
				}
				literals = null;
			}
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(literals);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null || getClass() != obj.getClass())
				return false;
			return Arrays.equals(literals, ((Clause) obj).literals);
		}

		@Override
		public String toString() {
			return "Clause [literals=" + Arrays.toString(literals) + "]";
		}

		public int getRelevance() {
			return relevance;
		}

	}

	public static Node removeFeatures(FeatureModel fm, Collection<String> features) throws TimeoutException {
		Node fmNode = NodeCreator.createNodes(fm).toCNF();
		if (fmNode instanceof And) {
			final Node[] andChildren = fmNode.getChildren();

			// all clauses that have both kinds of literals (remove AND retain)
			final List<Clause> relevantClauseList = new ArrayList<>(andChildren.length);

			// list for all new construct clauses
			final Set<Clause> newClauseSet = new HashSet<>(andChildren.length);
			final Set<Clause> relevantClauseSet = new HashSet<>(relevantClauseList);

			final DeprecatedFeatureMap map = new DeprecatedFeatureMap(features);

			// fill first two lists
			for (int i = 0; i < andChildren.length; i++) {
				Node andChild = andChildren[i];

				final Clause curClause;

				if (andChild instanceof Or) {
					int absoluteValueCount = 0;
					boolean valid = true;

					final Literal[] children = Arrays.copyOf(andChild.getChildren(), andChild.getChildren().length, Literal[].class);
					for (int j = 0; j < children.length; j++) {
						final Literal literal = children[j];

						// sort out obvious tautologies
						if (literal.var.equals(NodeCreator.varTrue)) {
							if (literal.positive) {
								valid = false;
							} else {
								absoluteValueCount++;
								children[j] = null;
							}
						} else if (literal.var.equals(NodeCreator.varFalse)) {
							if (literal.positive) {
								absoluteValueCount++;
								children[j] = null;
							} else {
								valid = false;
							}
						}
					}

					if (valid) {
						if (absoluteValueCount > 0) {
							if (children.length == absoluteValueCount) {
								throw new RuntimeException("Model is void!");
							}
							Literal[] newChildren = new Literal[children.length - absoluteValueCount];
							int k = 0;
							for (int j = 0; j < children.length; j++) {
								final Literal literal = children[j];
								if (literal != null) {
									newChildren[k++] = literal;
								}
							}
							curClause = Clause.createClause(map, newChildren);
						} else {
							curClause = Clause.createClause(map, children);
						}
					} else {
						curClause = null;
					}
				} else {
					final Literal literal = (Literal) andChild;
					if (literal.var.equals(NodeCreator.varTrue)) {
						if (!literal.positive) {
							throw new RuntimeException("Model is void!");
						}
						curClause = null;
					} else if (literal.var.equals(NodeCreator.varFalse)) {
						if (literal.positive) {
							throw new RuntimeException("Model is void!");
						}
						curClause = null;
					} else {
						curClause = Clause.createClause(map, literal);
					}
				}

				if (curClause != null) {
					if (curClause.getRelevance() == 0) {
						if (!newClauseSet.add(curClause)) {
							curClause.delete(map);
						}
					} else {
						if (relevantClauseSet.add(curClause)) {
							relevantClauseList.add(curClause);
						} else {
							curClause.delete(map);
						}
					}
				}
			}
			
			int ii = map.getSize();
			
			while (!map.isEmpty()) {
				ii = map.getSize();
				if(ii % 100 == 0){
					
					System.out.println(ii);
				}
				final String curFeature = map.next().getFeature();
				if (curFeature == null) {
					relevantClauseList.clear();
					relevantClauseSet.clear();
					break;
				}
				//	System.out.println(map.size());

				// ... create list of clauses that contain this feature
				int relevantIndex = 0;
				final byte[] clauseStates = new byte[relevantClauseList.size()];
				for (int i = 0; i < relevantClauseList.size(); i++) {
					final Clause clause = relevantClauseList.get(i);

					Literal curLiteral = null;
					for (Node clauseChildren : clause.getLiterals()) {
						final Literal literal = (Literal) clauseChildren;
						if (literal.var.equals(curFeature)) {
							if (curLiteral == null) {
								curLiteral = literal;
								clauseStates[relevantIndex] = (byte) (curLiteral.positive ? 1 : 2);
								Collections.swap(relevantClauseList, i, relevantIndex++);
							} else if (literal.positive != curLiteral.positive) {
								clauseStates[relevantIndex - 1] = -1;
								break;
							}
						}
					}
				}

				final CNFSolver solver = new CNFSolver(relevantClauseList.subList(0, relevantIndex));

				// ... combine relevant clauses if possible
				for (int i = relevantIndex - 1; i >= 0; i--) {
					final boolean positive;
					switch (clauseStates[i]) {
					case 1:
						positive = true;
						break;
					case 2:
						positive = false;
						break;
					case -1:
					case 0:
					default:
						continue;
					}

					final Literal[] orChildren = relevantClauseList.get(i).getLiterals();

					if (orChildren.length < 2) {
						continue;
					}

					final Literal[] literalList = new Literal[orChildren.length];
					int removeIndex = orChildren.length;
					int retainIndex = -1;

					for (int j = 0; j < orChildren.length; j++) {
						final Literal literal = orChildren[j].clone();
						if (literal.var.equals(curFeature)) {
							literalList[--removeIndex] = literal;
						} else {
							literal.flip();
							literalList[++retainIndex] = literal;
						}
					}

					// test for generalizability
					if (!solver.isSatisfiable(literalList)) {
						Literal[] retainLiterals = new Literal[retainIndex + 1];
						System.arraycopy(literalList, 0, retainLiterals, 0, retainLiterals.length);
						for (Literal retainedLiteral : retainLiterals) {
							retainedLiteral.flip();
						}

						final Clause newClause = Clause.createClause(map, retainLiterals);

						if (newClause != null) {
							if (newClause.getRelevance() == 0) {
								if (!newClauseSet.add(newClause)) {
									newClause.delete(map);
								}
							} else {
								if (relevantClauseSet.add(newClause)) {
									relevantClauseList.add(newClause);
								} else {
									newClause.delete(map);
								}
							}
						}

						// try to combine with other clauses
					} else {
						for (int j = i - 1; j >= 0; j--) {
							if ((positive && clauseStates[j] == 2) || (!positive && clauseStates[j] == 1)) {
								final Node[] children2 = relevantClauseList.get(j).getLiterals();
								final Literal[] newChildren = new Literal[orChildren.length + children2.length];

								System.arraycopy(orChildren, 0, newChildren, 0, orChildren.length);
								System.arraycopy(children2, 0, newChildren, orChildren.length, children2.length);

								final Clause newClause = Clause.createClause(map, newChildren, curFeature);

								if (newClause != null) {
									if (newClause.getRelevance() == 0) {
										if (!newClauseSet.add(newClause)) {
											newClause.delete(map);
										}
									} else {
										if (relevantClauseSet.add(newClause)) {
											relevantClauseList.add(newClause);
										} else {
											newClause.delete(map);
										}
									}
								}
							}
						}
					}
				}
				solver.reset();
				final List<Clause> subList = relevantClauseList.subList(0, relevantIndex);
				relevantClauseSet.removeAll(subList);
				for (Clause clause : subList) {
					clause.delete(map);
				}
				subList.clear();
				if (map.getGlobalMixedClauseCount() == 0) {
					break;
				}
			}

			// create clause that contains all retained features
			final Node[] allLiterals = new Node[fm.getNumberOfFeatures() - features.size() + 1];
			int i = 0;
			for (String featureName : fm.getFeatureNames()) {
				if (!features.contains(featureName)) {
					allLiterals[i++] = new Literal(featureName);
				}
			}
			allLiterals[i] = new Literal(NodeCreator.varTrue);

			// create new clauses list
			final int newClauseSize = newClauseSet.size();
			final Node[] newClauses = new Node[newClauseSize + 3];

			int j = 0;
			for (Clause newClause : newClauseSet) {
				newClauses[j++] = new Or(newClause.getLiterals());
			}

			newClauses[newClauseSize] = new Or(allLiterals);
			newClauses[newClauseSize + 1] = new Literal(NodeCreator.varTrue);
			newClauses[newClauseSize + 2] = new Literal(NodeCreator.varFalse, false);

			fmNode.setChildren(newClauses);

			return fmNode;
		} else if (fmNode instanceof Or) {
			for (Node clauseChildren : fmNode.getChildren()) {
				final Literal literal = (Literal) clauseChildren;
				if (features.contains(literal.var)) {
					return new Literal(NodeCreator.varTrue);
				}
			}
			return fmNode;
		} else {
			return (features.contains(((Literal) fmNode).var)) ? new Literal(NodeCreator.varTrue) : fmNode;
		}
	}

	private static class CNFSolver {

		private final HashMap<Object, Integer> varToInt;
		private final ISolver solver;

		public CNFSolver(Collection<Clause> clauses) {
			varToInt = new HashMap<Object, Integer>();
			for (Clause clause : clauses) {
				for (Literal literal : clause.getLiterals()) {
					final Object var = literal.var;
					if (!varToInt.containsKey(var)) {
						int index = varToInt.size() + 1;
						varToInt.put(var, index);
					}
				}
			}

			solver = SolverFactory.newDefault();
			solver.setTimeoutMs(1000);
			solver.newVar(varToInt.size());

			try {
				for (Clause node : clauses) {
					final Literal[] literals = node.getLiterals();
					int[] clause = new int[literals.length];
					int i = 0;
					for (Literal child : literals) {
						clause[i++] = getIntOfLiteral(child);
					}
					solver.addClause(new VecInt(clause));
				}
			} catch (ContradictionException e) {
				throw new RuntimeException(e);
			}
		}

		private int getIntOfLiteral(Literal node) {
			final int value = varToInt.get(node.var);
			return node.positive ? value : -value;
		}

		public boolean isSatisfiable(Literal[] literals) throws TimeoutException {
			final int[] unitClauses = new int[literals.length];
			int i = 0;
			for (Literal literal : literals) {
				unitClauses[i++] = getIntOfLiteral(literal);
			}
			return solver.isSatisfiable(new VecInt(unitClauses));
		}

		public void reset() {
			solver.reset();
		}

	}

}
