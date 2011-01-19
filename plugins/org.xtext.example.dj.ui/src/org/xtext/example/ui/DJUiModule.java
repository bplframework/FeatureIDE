/*

 * generated by Xtext
 */
package org.xtext.example.ui;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.xtext.ui.editor.contentassist.XtextContentAssistProcessor;
import org.xtext.example.ui.outline.DJTransformer;

import com.google.inject.Binder;
import com.google.inject.name.Names;

/**
 * Use this class to register components to be used within the IDE.
 */
public class DJUiModule extends org.xtext.example.ui.AbstractDJUiModule {
	public DJUiModule(AbstractUIPlugin plugin) {
		super(plugin);
	}
	
	@Override
	public void configure(Binder binder) {
		super.configure(binder);
		binder.bind(String.class).annotatedWith(Names.named(XtextContentAssistProcessor.
				                                COMPLETION_AUTO_ACTIVATION_CHARS)).toInstance( ".+");
	}
	@Override
	public Class<? extends org.eclipse.xtext.ui.editor.IXtextEditorCallback> bindIXtextEditorCallback() {
		return org.eclipse.xtext.ui.editor.IXtextEditorCallback.NullImpl.class;
	}
	/* (non-Javadoc)
	 * @see org.xtext.example.ui.AbstractDJUiModule#bindISemanticModelTransformer()
	 */
	@Override
	public Class<? extends org.eclipse.xtext.ui.editor.outline.transformer.ISemanticModelTransformer> bindISemanticModelTransformer() {
		return DJTransformer.class;
	}
}
