package edu.tamu.aser.tide.views;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

public class BugContentProvider implements ITreeContentProvider {


	@Override
	public void dispose() {
	}

	/**
	* Notifies this content provider that the given viewer's input
	* has been switched to a different element.
	* <p>
	* A typical use for this method is registering the content provider as a listener
	* to changes on the new input (using model-specific means), and deregistering the viewer
	* from the old input. In response to these change notifications, the content provider
	* propagates the changes to the viewer.
	* </p>
	*
	* @param viewer the viewer
	* @param oldInput the old input element, or <code>null</code> if the viewer
	*   did not previously have an input
	* @param newInput the new input element, or <code>null</code> if the viewer
	*  does not have an input
	*/
	@Override
	public void inputChanged(Viewer viewer, Object oldinput, Object newinput) {
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		return ((ITreeNode)parentElement).getChildren().toArray();
	}

	@Override
	public Object[] getElements(Object inputElement) {
		return getChildren(inputElement);
	}

	@Override
	public Object getParent(Object element) {
		return ((ITreeNode)element).getParent();
	}

	@Override
	public boolean hasChildren(Object element) {
		return ((ITreeNode)element).hasChildren();
	}

}
