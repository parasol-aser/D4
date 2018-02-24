package edu.tamu.aser.tide.views;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;

public class SaveExcludeAction extends Action implements IWorkbenchAction{

	private static final String saveID = "edu.tamu.aser.tide.views.SaveExcludeAction";

	public SaveExcludeAction() {
		setId(saveID);
	}

	public void run() {
		Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		String dialogBoxTitle = "Message!";
		String message = "You clicked something!";
		MessageDialog.openInformation(shell, dialogBoxTitle, message);
	};

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

}
