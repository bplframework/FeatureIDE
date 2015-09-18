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
package de.ovgu.featureide.fm.core.base;

import static de.ovgu.featureide.fm.core.functional.Functional.filter;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.eclipse.core.resources.IProject;
import org.prop4j.Node;
import org.prop4j.NodeWriter;

import de.ovgu.featureide.fm.core.ColorList;
import de.ovgu.featureide.fm.core.ColorschemeTable;
import de.ovgu.featureide.fm.core.FMComposerManager;
import de.ovgu.featureide.fm.core.FMPoint;
import de.ovgu.featureide.fm.core.FeatureConnection;
import de.ovgu.featureide.fm.core.FeatureModelAnalyzer;
import de.ovgu.featureide.fm.core.FeatureModelLayout;
import de.ovgu.featureide.fm.core.FeatureStatus;
import de.ovgu.featureide.fm.core.IFMComposerExtension;
import de.ovgu.featureide.fm.core.Operator;
import de.ovgu.featureide.fm.core.RenamingsManager;
import de.ovgu.featureide.fm.core.IGraphicItem.GraphicItem;
import de.ovgu.featureide.fm.core.base.impl.Constraint;
import de.ovgu.featureide.fm.core.filter.ConcreteFeatureFilter;
import de.ovgu.featureide.fm.core.functional.Functional;
import de.ovgu.featureide.fm.core.functional.Functional.IFunction;

/**
 * @author Marcus Pinnecke
 */
public abstract class FeatureUtils {

	public static final ConcreteFeatureFilter CONCRETE_FEATURE_FILTER = new ConcreteFeatureFilter();

	public static final IFunction<IFeature, CharSequence> GET_FEATURE_NAME = new IFunction<IFeature, CharSequence>() {

		@Override
		public CharSequence invoke(IFeature t) {
			return t.getName();
		}
	};

	public static final IFunction<IFeatureStructure, IFeature> STRUCTURE_TO_FEATURE = new IFunction<IFeatureStructure, IFeature>() {

		@Override
		public IFeature invoke(IFeatureStructure t) {
			return t.getFeature();
		}
	};

	public static final IFunction<IFeature, IFeatureStructure> FEATURE_TO_STRUCTURE = new IFunction<IFeature, IFeatureStructure>() {

		@Override
		public IFeatureStructure invoke(IFeature t) {
			return t.getStructure();
		}
	};

	private static final IFunction<IConstraint, Node> CONSTRAINT_TO_NODE = new IFunction<IConstraint, Node>() {

		@Override
		public Node invoke(IConstraint t) {
			return t.getNode();
		}

	};

	/**
	 * Extracts all concrete features from an object that yields features. Basically, an invocation of this method on <b>features</b> will return an iterable
	 * object that
	 * yields a feature <i>f</i> from <b>features</b> if and only if <i>f</i> is concrete. Since the implementation based on iterators, it is a lazy filtering
	 * without
	 * modification of <b>features</b>.
	 * 
	 * <br/>
	 * <br/>
	 * The extraction is done via {@link de.ovgu.featureide.fm.core.functional.Functional#filter(Iterable, de.ovgu.featureide.fm.core.filter.base.IFilter)}
	 * 
	 * @since 2.7.5
	 * @param features An iterable object providing features
	 * @author Marcus Pinnecke
	 * @return An iterable object that yields all concrete features of <b>features</b>
	 */
	public static Iterable<IFeature> extractConcreteFeatures(final Iterable<IFeature> features) {
		return filter(features, CONCRETE_FEATURE_FILTER);
	}

	/**
	 * Extracts all concrete features from a feature model by calling {@link #extractConcreteFeatures(Iterable)} on <code>model.getFeatures()</code>.
	 * 
	 * @since 2.7.5
	 * @param model A feature model
	 * @author Marcus Pinnecke
	 * @return An iterable object that yields all concrete features of <b>features</b>
	 */
	public static Iterable<IFeature> extractConcreteFeatures(final IFeatureModel model) {
		return extractConcreteFeatures(model.getFeatures());
	}

	/**
	 * Extracts all concrete features from a feature model as a list of strings by calling
	 * {@link de.ovgu.featureide.fm.core.functional.Functional#mapToStringList(Iterable)} on the result of {@link #extractConcreteFeatures(IFeatureModel)} using
	 * <code>model.getFeatures()</code>.
	 * 
	 * @since 2.7.5
	 * @param model A feature model
	 * @author Marcus Pinnecke
	 * @return A list of strings that contains the feature names of all concrete features of <b>features</b>
	 */
	public static List<CharSequence> extractConcreteFeaturesAsStringList(IFeatureModel model) {
		return Functional.mapToStringList(FeatureUtils.extractConcreteFeatures(model.getFeatures()));
	}

	public static Iterable<CharSequence> extractFeatureNames(Collection<IFeature> features) {
		return Functional.map(features, GET_FEATURE_NAME);
	}

	public static Iterable<IFeature> convertToFeatureList(List<IFeatureStructure> list) {
		return Functional.map(list, STRUCTURE_TO_FEATURE);
	}

	public static Iterable<IFeatureStructure> convertToFeatureStructureList(List<IFeature> list) {
		return Functional.map(list, FEATURE_TO_STRUCTURE);
	}

	public static Iterable<Node> getPropositionalNodes(Iterable<IConstraint> constraints) {
		return Functional.toList(Functional.map(constraints, CONSTRAINT_TO_NODE));
	}

	public static CharSequence getRelevantConstraintsString(IFeature feature, Collection<IConstraint> constraints) {
		StringBuilder relevant = new StringBuilder();
		for (IConstraint constraint : constraints) {
			for (IFeature f : constraint.getContainedFeatures()) {
				if (f.getName().equals(feature.getName())) {
					relevant.append((relevant.length() == 0 ? " " : "\n ") + constraint.getNode().toString(NodeWriter.logicalSymbols) + " ");
					break;
				}
			}
		}
		return relevant.toString();
	}

	public static void replacePropNode(IFeatureModel featureModel, int index, Node propNode) {
		featureModel.getConstraints().set(index, new Constraint(featureModel, propNode));
	}

	public static void setRelevantConstraints(IFeature bone) {
		List<Constraint> constraintList = new LinkedList<Constraint>();
		for (IConstraint constraint : bone.getFeatureModel().getConstraints()) {
			for (IFeature f : constraint.getContainedFeatures()) {
				if (f.getName().equals(bone.getName())) {
					constraintList.add(new Constraint(bone.getFeatureModel(), constraint.getNode()));
					break;
				}
			}
		}
		bone.getStructure().setRelevantConstraints(constraintList);
	}

	public CharSequence createValidJavaIdentifierFromString(CharSequence s) {
		StringBuilder stringBuilder = new StringBuilder();
		int i = 0;
		for (; i < s.length(); i++) {
			if (Character.isJavaIdentifierStart(s.charAt(i))) {
				stringBuilder.append(s.charAt(i));
				i++;
				break;
			}
		}
		for (; i < s.length(); i++) {
			if (Character.isJavaIdentifierPart(s.charAt(i))) {
				stringBuilder.append(s.charAt(i));
			}
		}
		return stringBuilder.toString();
	}
	
	
	
	
	
	
	
	
	
	public static final CharSequence getDescription(IFeature feature) {
		return feature.getProperty().getDescription();
	}

	public static final void setDescription(IFeature feature, CharSequence description) {
		feature.getProperty().setDescription(description);
	}

	public static final void setNewLocation(IFeature feature, FMPoint newLocation) {
		feature.getGraphicRepresenation().setNewLocation(newLocation);
	}

	public static final FMPoint getLocation(IFeature feature) {
		return feature.getGraphicRepresenation().getLocation();
	}

	public static final boolean isAnd(IFeature feature) {
		return feature.getStructure().isAnd();
	}

	public static final boolean isOr(IFeature feature) {
		return feature.getStructure().isOr();
	}

	public static final boolean isAlternative(IFeature feature) {
		return feature.getStructure().isAlternative();
	}

	public static final void changeToAnd(IFeature feature) {
		feature.getStructure().changeToAnd();
	}

	public static final void changeToOr(IFeature feature) {
		feature.getStructure().changeToOr();
	}

	public static final void changeToAlternative(IFeature feature) {
		feature.getStructure().changeToAlternative();
	}

	public static final void setAND(IFeature feature, boolean and) {
		feature.getStructure().setAND(and);
	}

	public static final boolean isMandatorySet(IFeature feature) {
		return feature.getStructure().isMandatorySet();
	}

	public static final boolean isMandatory(IFeature feature) {
		return feature.getStructure().isMandatory();
	}

	public static final void setMandatory(IFeature feature, boolean mandatory) {
		feature.getStructure().setMandatory(mandatory);
	}

	public static final boolean isHidden(IFeature feature) {
		return feature.getStructure().isHidden();
	}

	public static final void setHidden(IFeature feature, boolean hid) {
		feature.getStructure().setHidden(hid);
	}

	public static final boolean isConstraintSelected(IFeature feature) {
		return feature.getProperty().isConstraintSelected();
	}

	public static final void setConstraintSelected(IFeature feature, boolean selection) {
		feature.getProperty().selectConstraint(selection);
	}

	public static final void setAbstract(IFeature feature, boolean value) {
		feature.getStructure().setAbstract(value);
	}

	public static final Collection<IConstraint> getRelevantConstraints(IFeature feature) {
		return feature.getStructure().getRelevantConstraints();
	}

	public static final CharSequence getRelevantConstraintsString(IFeature feature) {
		return FeatureUtils.getRelevantConstraintsString(feature, feature.getFeatureModel().getConstraints());
	}

	public static final FeatureStatus getFeatureStatus(IFeature feature) {
		return feature.getProperty().getFeatureStatus();
	}

	public static final IFeatureModel getFeatureModel(IFeature feature) {
		return feature.getFeatureModel();
	}

	public static final void setFeatureStatus(IFeature feature, FeatureStatus stat, boolean fire) {
		feature.getProperty().setFeatureStatus(stat, fire);
	}

	public static final boolean isMultiple(IFeature feature) {
		return feature.getStructure().isMultiple();
	}

	public static final void setMultiple(IFeature feature, boolean multiple) {
		feature.getStructure().setMultiple(multiple);
	}

	public static final CharSequence getName(IFeature feature) {
		return feature.getName();
	}

	public static final void setName(IFeature feature, CharSequence name) {
		feature.setName(name);
	}

	public static final boolean hasInlineRule(IFeature feature) {
		return feature.getStructure().hasInlineRule();
	}

	public static final void setParent(IFeature feature, IFeature newParent) {
		feature.getStructure().setParent(newParent.getStructure());
	}

	public static final IFeature getParent(IFeature feature) {
		return feature.getStructure().getParent().getFeature();
	}

	public static final boolean isRoot(IFeature feature) {
		return feature.getStructure().isRoot();
	}

	public static final Iterable<IFeature> getChildren(IFeature feature) {
		return Functional.map(feature.getStructure().getChildren(), STRUCTURE_TO_FEATURE);
	}

	public static final void setChildren(IFeature feature, Iterable<IFeature> children) {
		feature.getStructure().setChildren(Functional.toList(Functional.map(children, FEATURE_TO_STRUCTURE)));
	}

	public static final boolean hasChildren(IFeature feature) {
		return feature.getStructure().hasChildren();
	}

	public static final void addChild(IFeature feature, IFeature newChild) {
		feature.getStructure().addChild(newChild.getStructure());
	}

	public static final void addChildAtPosition(IFeature feature, int index, IFeature newChild) {
		feature.getStructure().addChildAtPosition(index, newChild.getStructure());
	}

	public static final void replaceChild(IFeature feature, IFeature oldChild, IFeature newChild) {
		feature.getStructure().replaceChild(oldChild.getStructure(), newChild.getStructure());
	}

	public static final void removeChild(IFeature feature, IFeature child) {
		feature.getStructure().removeChild(child.getStructure());
	}

	public static final IFeature removeLastChild(IFeature feature) {
		return feature.getStructure().removeLastChild().getFeature();
	}

	public static final Iterable<FeatureConnection> getSourceConnections(IFeature feature) {
		return feature.getStructure().getSourceConnections();
	}

	public static final Iterable<FeatureConnection> getTargetConnections(IFeature feature) {
		return feature.getStructure().getTargetConnections();
	}

	public static final void addTargetConnection(IFeature feature, FeatureConnection connection) {
		feature.getStructure().addTargetConnection(connection);
	}

	public static final boolean removeTargetConnection(IFeature feature, FeatureConnection connection) {
		return feature.getStructure().removeTargetConnection(connection);
	}

	public static final void addListener(IFeature feature, PropertyChangeListener listener) {
		feature.addListener(listener);
	}

	public static final void removeListener(IFeature feature, PropertyChangeListener listener) {
		feature.removeListener(listener);
	}

	public static final boolean isAncestorOf(IFeature feature, IFeature next) {
		return feature.getStructure().isAncestorOf(next.getStructure());
	}

	public static final boolean isFirstChild(IFeature feature, IFeature child) {
		return feature.getStructure().isFirstChild(child.getStructure());
	}

	public static final int getChildrenCount(IFeature feature) {
		return feature.getStructure().getChildrenCount();
	}

	public static final IFeature getFirstChild(IFeature feature) {
		return feature.getStructure().getFirstChild().getFeature();
	}

	public static final IFeature getLastChild(IFeature feature) {
		return feature.getStructure().getLastChild().getFeature();
	}

	public static final boolean isAbstract(IFeature feature) {
		return feature.getStructure().isAbstract();
	}

	public static final boolean isConcrete(IFeature feature) {
		return feature.getStructure().isConcrete();
	}

	public static final boolean isANDPossible(IFeature feature) {
		return feature.getStructure().isANDPossible();
	}

	public static final void fire(IFeature feature, PropertyChangeEvent event) {
		feature.fireEvent(event);
	}

	public static final IFeature clone(IFeature feature) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	public static final IFeature clone(IFeature feature, IFeatureModel featureModel, boolean complete) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	public static final void setAnd(IFeature feature) {
		feature.getStructure().setAnd();
	}

	public static final void setOr(IFeature feature) {
		feature.getStructure().setOr();
	}

	public static final void setAlternative(IFeature feature) {
		feature.getStructure().setAlternative();
	}

	public static final boolean hasHiddenParent(IFeature feature) {
		return feature.getStructure().hasHiddenParent();
	}

	public static final CharSequence toString(IFeature feature) {
		return feature.toString();
	}

	public static final CharSequence getDisplayName(IFeature feature) {
		return feature.getProperty().getDisplayName();
	}

	public static final void propertyChange(IFeature feature, PropertyChangeEvent event) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	public static final CharSequence toString(IFeature feature, boolean writeMarks) {
		if (writeMarks) {
			final String featureName = feature.getName().toString();
			if (featureName.contains(" ") || Operator.isOperatorName(featureName)) {
				return "\"" + feature.getName() + "\"";
			}
			return feature.getName();
		} else {
			return feature.toString();
		}
	}

	public static final ColorList getColorList(IFeature feature) {
		return feature.getGraphicRepresenation().getColorList();
	}

	public static final int hashCode(IFeature feature) {
		return feature.hashCode();
	}

	public static final GraphicItem getItemType(IFeature feature) {
		return feature.getGraphicRepresenation().getItemType();
	}
	
	
	
	
	
	
	
	
	
	
	
	
	public static final FeatureModelAnalyzer createAnalyser(IFeatureModel featureModel) {
		return featureModel.getAnalyser();
	}
	
    public static final FeatureModelAnalyzer getAnalyser(IFeatureModel featureModel) {
		return featureModel.getAnalyser();
    }

    public static final FeatureModelLayout getLayout(IFeatureModel featureModel) {
		return featureModel.getLayout();
    }

	public static final ColorschemeTable getColorschemeTable(IFeatureModel featureModel) {
		return featureModel.getGraphicRepresenation().getColorschemeTable();
	}
	
	public static final FMComposerManager getFMComposerManager(IFeatureModel featureModel, final IProject project) {
		return featureModel.getFMComposerManager(project);
	}

	public static final IFMComposerExtension initFMComposerExtension(IFeatureModel featureModel, final IProject project) {
		return featureModel.initFMComposerExtension(project);
	}

	public static final IFMComposerExtension getFMComposerExtension(IFeatureModel featureModel) {
		return featureModel.getFMComposerExtension();
	}
		
	public static final RenamingsManager getRenamingsManager(IFeatureModel featureModel) {
		return featureModel.getRenamingsManager();
	}
	
	public static final void reset(IFeatureModel featureModel) {
		featureModel.reset();
	}
	
	public static final void createDefaultValues(IFeatureModel featureModel, CharSequence projectName) {
		featureModel.createDefaultValues(projectName);
	}
	
	public static final void setRoot(IFeatureModel featureModel, IFeature root) {
		featureModel.getStructure().setRoot(root.getStructure());
	}
	
	public static final IFeature getRoot(IFeatureModel featureModel) {
		return featureModel.getStructure().getRoot().getFeature();
	}

	public static final void setFeatureTable(IFeatureModel featureModel, final Hashtable<CharSequence, IFeature> featureTable) {
		featureModel.setFeatureTable(featureTable);
	}
	
	public static final boolean addFeature(IFeatureModel featureModel, IFeature feature) {
		return featureModel.addFeature(feature);
	}
	
	public static final Collection<IFeature> getFeatures(IFeatureModel featureModel) {
		return Functional.toList(featureModel.getFeatures());
	}
	
	public static final IFeature getFeature(IFeatureModel featureModel, CharSequence name) {
		return featureModel.getFeature(name.toString());
	}

	@Nonnull
	public static final Collection<IFeature> getConcreteFeatures(IFeatureModel featureModel) {
		return Functional.toList(FeatureUtils.extractConcreteFeatures(featureModel));
	}
	
	@Nonnull
	public static final Iterable<CharSequence> getConcreteFeatureNames(IFeatureModel featureModel) {
		return FeatureUtils.extractConcreteFeaturesAsStringList(featureModel);
	}
	
	public static final Collection<IFeature> getFeaturesPreorder(IFeatureModel featureModel) {
		return featureModel.getStructure().getFeaturesPreorder();
	}

	public static final List<CharSequence> getFeatureNamesPreorder(IFeatureModel featureModel) {
		return Functional.toList(FeatureUtils.extractFeatureNames(featureModel.getStructure().getFeaturesPreorder()));
	}
	
	@Deprecated
	public static final boolean isConcrete(IFeatureModel featureModel, CharSequence featureName) {
		for (IFeature feature : FeatureUtils.extractConcreteFeatures(featureModel))
			if (feature.getName().equals(featureName))
				return true;
		return false;
	}
	
	protected static final Map<CharSequence, IFeature> getFeatureTable(IFeatureModel featureModel) {
		return featureModel.getFeatureTable();
	}
	
	public static final Set<CharSequence> getFeatureNames(IFeatureModel featureModel) {
		return Functional.toSet(FeatureUtils.extractFeatureNames(Functional.toList(featureModel.getFeatures())));
	}
	
	public static final int getNumberOfFeatures(IFeatureModel featureModel) {
		return featureModel.getNumberOfFeatures();
	}

	public static final void deleteFeatureFromTable(IFeatureModel featureModel, IFeature feature) {
		featureModel.deleteFeatureFromTable(feature);
	}

	public static final boolean deleteFeature(IFeatureModel featureModel, IFeature feature) {
		return featureModel.deleteFeature(feature);
	}
	
	public static final void replaceRoot(IFeatureModel featureModel, IFeature feature) {
		featureModel.getStructure().replaceRoot(feature.getStructure());
	}

	public static final void setConstraints(IFeatureModel featureModel, final Iterable<IConstraint> constraints) {
		featureModel.setConstraints(constraints);
	}
	
	public static final void addPropositionalNode(IFeatureModel featureModel, Node node) {
		featureModel.getConstraints().add(new Constraint(featureModel, node));
	}
	
	public static final void addConstraint(IFeatureModel featureModel, IConstraint constraint) {
		featureModel.getConstraints().add(constraint);
	}

	public static final void addPropositionalNode(IFeatureModel featureModel, Node node, int index) {
		featureModel.getConstraints().add(index, new Constraint(featureModel, node));
	}
	
	public static final void addConstraint(IFeatureModel featureModel, IConstraint constraint, int index) {
		featureModel.getConstraints().add(index, constraint);
	}
	
	public static final Iterable<Node> getPropositionalNodes(IFeatureModel featureModel) {
		return Functional.map(featureModel.getConstraints(), CONSTRAINT_TO_NODE);
	}
	
	public static final Node getConstraint(IFeatureModel featureModel, int index) {
		return Functional.toList(getPropositionalNodes(featureModel)).get(index);
	}
	                                                                                                                                                                                                                                                                                                                                                                                                                                                                     
	public static final List<IConstraint> getConstraints(IFeatureModel featureModel) {
		return featureModel.getConstraints();
	}

	public static final int getConstraintIndex(IFeatureModel featureModel, IConstraint constraint) {
		final List<IConstraint> constraints = featureModel.getConstraints();
		for (int i = 0; i < constraints.size(); i++)
			if (constraints.get(i).equals(constraint))
				return i;
		throw new NoSuchElementException();
	}

	public static final void removePropositionalNode(IFeatureModel featureModel, Node node) {
		List<IConstraint> constraints = featureModel.getConstraints();
		int index = -1;
		for (int i = 0; i < constraints.size(); i++)
			if (constraints.get(i).getNode().equals(node)) {
				index = i;
				break;
			}
		tryRemoveConstraint(featureModel, constraints, index);
	}

	public static final void removeConstraint(IFeatureModel featureModel, IConstraint constraint) {
		List<IConstraint> constraints = featureModel.getConstraints();
		int index = getConstraintIndex(featureModel, constraint);
		tryRemoveConstraint(featureModel, constraints, index);
	}

	private static void tryRemoveConstraint(IFeatureModel featureModel, List<IConstraint> constraints, int index) {
		if (index == -1 || index >= constraints.size())
			throw new NoSuchElementException();
		else {
			constraints.remove(index);
			featureModel.setConstraints(constraints);
		}
	}

	public static final void removeConstraint(IFeatureModel featureModel, int index) {
		tryRemoveConstraint(featureModel, featureModel.getConstraints(), index);
	}
	
	public static final int getConstraintCount(IFeatureModel featureModel) {
		return featureModel.getConstraintCount();
	}
	
	public static final List<CharSequence> getAnnotations(IFeatureModel featureModel) {
		return Functional.toList(featureModel.getProperty().getAnnotations());
	}

	public static final void addAnnotation(IFeatureModel featureModel, CharSequence annotation) {
		featureModel.getProperty().addAnnotation(annotation);
	}

	public static final List<CharSequence> getComments(IFeatureModel featureModel) {
		return Functional.toList(featureModel.getProperty().getComments());
	}

	public static final void addComment(IFeatureModel featureModel, CharSequence comment) {
		featureModel.getProperty().addComment(comment);
	}
	
	public static final void addListener(IFeatureModel featureModel, PropertyChangeListener listener) {
		featureModel.addListener(listener);
	}

	public static final void removeListener(IFeatureModel featureModel, PropertyChangeListener listener) {
		featureModel.removeListener(listener);
	}
	
	public static final void handleModelDataLoaded(IFeatureModel featureModel) {
		featureModel.handleModelDataLoaded();
	}

	public static final void handleModelDataChanged(IFeatureModel featureModel) {
		featureModel.handleModelDataChanged();
	}
	
	public static final void handleModelLayoutChanged(IFeatureModel featureModel) {
		featureModel.getGraphicRepresenation().handleModelLayoutChanged();
	}
	
	public static final void handleLegendLayoutChanged(IFeatureModel featureModel) {
		featureModel.getGraphicRepresenation().handleLegendLayoutChanged();
	}
	
	public static final void refreshContextMenu(IFeatureModel featureModel) {
		featureModel.getGraphicRepresenation().refreshContextMenu();
	}
	
	public static final void redrawDiagram(IFeatureModel featureModel) {
		featureModel.getGraphicRepresenation().redrawDiagram();
	}
	
	@Override
	public static final IFeatureModel clone(IFeatureModel featureModel) {
		return featureModel.clone(featureModel, true);
	}
	
	public static final IFeatureModel deepClone(IFeatureModel featureModel) {
	
	}
	
	public static final IFeatureModel deepClone(IFeatureModel featureModel, boolean complete) {
	
	}

	public static final boolean hasMandatoryFeatures(IFeatureModel featureModel) {
	
	}

	public static final boolean hasOptionalFeatures(IFeatureModel featureModel) {
	
	}

	public static final boolean hasAndGroup(IFeatureModel featureModel) {
	
	}

	public static final boolean hasAlternativeGroup(IFeatureModel featureModel) {
		
	}
	
	public static final boolean hasOrGroup(IFeatureModel featureModel) {
	
	}

	public static final boolean hasAbstract(IFeatureModel featureModel) {
	
	}

	public static final boolean hasConcrete(IFeatureModel featureModel) {
	
	}
	
	public static final int numOrGroup(IFeatureModel featureModel) {
	
	}
	
	public static final int numAlternativeGroup(IFeatureModel featureModel) {
	
	}
	
	public static final boolean hasHidden(IFeatureModel featureModel) {
	
	}

	public static final boolean hasIndetHidden(IFeatureModel featureModel) {
	
	}
	
	public static final boolean hasUnsatisfiableConst(IFeatureModel featureModel) {
	
	}
	
	public static final boolean hasTautologyConst(IFeatureModel featureModel) {
	
	}
	
	public static final boolean hasDeadConst(IFeatureModel featureModel) {
	
	}
	
	public static final boolean hasVoidModelConst(IFeatureModel featureModel) {
	
	}
	
	public static final boolean hasRedundantConst(IFeatureModel featureModel) {
	
	}

	public static final boolean hasFalseOptionalFeatures(IFeatureModel featureModel) {
	
	}

	public static final void setUndoContext(IFeatureModel featureModel, Object undoContext) {
	
	}

	public static final Object getUndoContext(IFeatureModel featureModel) {
	
	}

	public static final List<CharSequence> getFeatureOrderList(IFeatureModel featureModel) {
	
	}

	public static final void setFeatureOrderList(IFeatureModel featureModel, final List<CharSequence> featureOrderList) {
	
	}

	public static final boolean isFeatureOrderUserDefined(IFeatureModel featureModel) {
	
	}

	public static final void setFeatureOrderUserDefined(IFeatureModel featureModel, boolean featureOrderUserDefined) {
	
	}

	public static final boolean isFeatureOrderInXML(IFeatureModel featureModel) {
	
	}

	public static final void setFeatureOrderInXML(IFeatureModel featureModel, boolean featureOrderInXML) {
	
	}
	
	public static final CharSequence toString(IFeatureModel featureModel) {
	
	}
	
	public static final boolean equals(IFeatureModel featureModel, Object obj) {
	
	}
	
	public static final int hashCode(IFeatureModel featureModel) {
	
	}

	public static final GraphicItem getItemType(IFeatureModel featureModel) {
	
	}
}
