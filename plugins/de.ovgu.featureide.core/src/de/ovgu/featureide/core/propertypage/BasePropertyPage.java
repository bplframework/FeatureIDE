package de.ovgu.featureide.core.propertypage;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.dialogs.PropertyPage;

/**
 * First FeatureIDE property page containing all other property 
 * pages at the sub tree
 * 
 * @author Jens Meinicke
 */
@SuppressWarnings("restriction")
public class BasePropertyPage extends PropertyPage {

	private static final String DESCRIPTION = null;
	private IProject project;

	@Override
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.verticalSpacing = 9;
		composite.setLayout(layout);
		
		if (!getProject()) {
			Label label = new Label(composite, SWT.NULL);
			label.setText("No resource selected");	
			return null;
		}

		Label label = new Label(composite, SWT.NULL);
		label.setText("&Project: " + project.getName());		
		
		return composite;
	}
	
	/**
	 * @return
	 */
	private boolean getProject() {
		IAdaptable resource = getElement();
		if (resource instanceof JavaProject) {
			JavaProject javaProject = (JavaProject)resource;
			project  = javaProject.getProject();
		} else if (resource instanceof IProject){
			project = (IProject)resource;
		} else if (resource instanceof IFile) {
			project = ((IFile)resource).getProject();
		} else {
			return false;
		}
		return true;
	}
	
	@Override
	public String getDescription() {
		return DESCRIPTION;
	}

}
