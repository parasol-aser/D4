package edu.tamu.aser.tide.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

public class FixView extends ViewPart{
	protected Text text;

	public FixView() {
	}

	@Override
	public void createPartControl(Composite parent) {
		text = new Text(parent, SWT.READ_ONLY | SWT.SINGLE | SWT.BORDER);
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
	}

}
