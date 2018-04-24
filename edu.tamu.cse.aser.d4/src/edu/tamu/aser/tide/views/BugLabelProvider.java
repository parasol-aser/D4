package edu.tamu.aser.tide.views;

import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

public class BugLabelProvider extends LabelProvider implements IColorProvider {
	private HashMap<ImageDescriptor, Image> imageCache = new HashMap<>(11);
	private Color RED = Display.getCurrent().getSystemColor(SWT.COLOR_RED);

	@Override
	public String getText(Object element) {
		return ((ITreeNode)element).getName();
	}

	@Override
	public Image getImage(Object element) {
		ImageDescriptor descriptor = ((ITreeNode)element).getImage();
		//obtain the cached image corresponding to the descriptor
		Image image = (Image)imageCache.get(descriptor);
		if (image == null && descriptor != null) {
			image = descriptor.createImage();
			imageCache.put(descriptor, image);
		}
		return image;
	}
	
	public void dispose() {
		for (Iterator i = imageCache.values().iterator(); i.hasNext();) {
			((Image) i.next()).dispose();
		}
		imageCache.clear();
	}

	@Override
	public Color getBackground(Object element) {
		return null;
	}

	@Override
	public Color getForeground(Object element) {
		boolean isNewest = ((TreeNode) element).isNewest();
		if (isNewest) {
			((TreeNode) element).isNewest = false;
			return RED;
		} else {
			return null; 
		}
	}

}
