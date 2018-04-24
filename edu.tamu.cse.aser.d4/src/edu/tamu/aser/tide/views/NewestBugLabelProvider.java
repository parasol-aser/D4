package edu.tamu.aser.tide.views;

import java.util.HashMap;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.graphics.Image;

public class NewestBugLabelProvider extends CellLabelProvider{
	private HashMap<ImageDescriptor, Image> imageCache = new HashMap<>(11);

	@Override
	public void update(ViewerCell cell) {
		cell.setText(((ITreeNode)cell).getName());
		cell.setImage(getImage(cell));
	}

	public String getText(Object element) {
		return ((ITreeNode)element).getName();
	}

	public Image getImage(Object element) {
		ImageDescriptor descriptor = ((ITreeNode)element).getImage();
		//obtain the cached image corresponding to the descriptor
		Image image = (Image)imageCache.get(descriptor);
		if (image == null) {
			image = descriptor.createImage();
			imageCache.put(descriptor, image);
		}
		return image;
	}
	
}
