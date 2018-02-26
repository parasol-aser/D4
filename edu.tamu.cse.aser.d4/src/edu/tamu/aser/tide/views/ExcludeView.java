package edu.tamu.aser.tide.views;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import edu.tamu.aser.tide.plugin.Activator;

public class ExcludeView extends ViewPart{

	protected Text text_default;
	protected Text text_change;
	protected Action monitorSave;
	protected ICompilationUnit cu;
	protected IStructuredSelection selection;
	protected String initial_exclude = "javax\\/.*\njava\\/.*\nsun\\/.*\nsunw\\/.*\ncom\\/sun\\/.*\ncom\\/ibm\\/.*\ncom\\/apple\\/.*\ncom\\/oracle\\/.*\napple\\/.*\norg\\/xml\\/.*\njdbm\\/.*\n";

	public ExcludeView() {
	}

	public String getChangedText(){
		return text_change.getText();
	}

	public String getDefaultText(){
		return text_default.getText();
	}

	@Override
	public void createPartControl(Composite parent) {
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.verticalSpacing = 22;
		layout.marginWidth = 0;
		layout.marginHeight = 2;
		parent.setLayout(layout);

		//text_default
		Label label = new Label(parent, SWT.NULL);
		label.setText("Default Excluded Files: ");

		text_default = new Text(parent, SWT.SINGLE  | SWT.BORDER);
		GridData layoutData0 = new GridData();
		layoutData0.grabExcessHorizontalSpace = true;
		layoutData0.horizontalAlignment = GridData.FILL;
		text_default.setLayoutData(layoutData0);
		text_default.setText(initial_exclude);

		//text_change
		label = new Label(parent, SWT.NULL);
		label.setText("Self-defined Excluded Files: ");

		text_change = new Text(parent, SWT.WRAP
		          | SWT.MULTI
		          | SWT.BORDER
		          | SWT.H_SCROLL
		          | SWT.V_SCROLL);
		GridData layoutData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL);
		layoutData.horizontalSpan = 3;
		layoutData.grabExcessVerticalSpace = true;
		layoutData.grabExcessHorizontalSpace = true;
		layoutData.horizontalAlignment = GridData.FILL;
		text_change.setLayoutData(layoutData);

		createActions();
		createToolbar();
		hookListeners();
	}

	private void createToolbar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		mgr.add(monitorSave);
	}

	private void createActions() {
		monitorSave = new Action() {
			public void run(){
				if(cu == null || selection == null){

				}else{
					//redo whole program analysis after change the exclude file
					edu.tamu.aser.tide.plugin.Activator.getDefault().getConvertHandler().test(cu, selection);
				}
			}
		};
		monitorSave.setImageDescriptor(Activator.getImageDescriptor("refresh.gif"));
	}

	private void hookListeners() {
//		text.addModifyListener(new ModifyListener() {
//			@Override
//			public void modifyText(ModifyEvent event) {
//				handler.test(cu, selection);
//			}
//		});
	}


	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
	}

	public void setProgramInfo(ICompilationUnit cu, IStructuredSelection selection) {
		this.cu = cu;
		this.selection = selection;
	}

}
