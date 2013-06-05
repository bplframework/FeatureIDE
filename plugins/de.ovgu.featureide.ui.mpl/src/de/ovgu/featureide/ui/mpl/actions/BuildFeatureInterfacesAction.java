/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2013  FeatureIDE team, University of Magdeburg, Germany
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
 * See http://www.fosd.de/featureide/ for further information.
 */
package de.ovgu.featureide.ui.mpl.actions;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;

import de.ovgu.featureide.core.mpl.MPLPlugin;
import de.ovgu.featureide.ui.mpl.wizards.FeatureInterfaceWizard;

/**
 * Action to build interfaces grouped by the feature name.
 * 
 * @author Sebastian Krieter
 * @author Reimar Schroeter
 */
public class BuildFeatureInterfacesAction extends AbstractProjectAction {
	@Override
	protected void action(IProject project) {
		FeatureInterfaceWizard wizard = new FeatureInterfaceWizard("Build Feature Interfaces");
		
		WizardDialog dialog = new WizardDialog(Display.getCurrent().getActiveShell(), wizard);
		if (dialog.open() == Dialog.OK) {
			MPLPlugin.getDefault().buildFeatureInterfaces(project.getName(), wizard.getFolderName(), wizard.getViewName(), 
					wizard.getViewLevel(), wizard.getConfigLimit());
		}
	}
}