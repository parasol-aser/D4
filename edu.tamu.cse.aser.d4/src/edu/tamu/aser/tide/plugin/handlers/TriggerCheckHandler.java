package edu.tamu.aser.tide.plugin.handlers;


import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.IJavaProject;

import edu.tamu.aser.tide.plugin.Activator;
import edu.tamu.aser.tide.plugin.MyJavaElementChangeCollector;

public class TriggerCheckHandler extends AbstractHandler{

	private MyJavaElementChangeCollector collector;

	public TriggerCheckHandler() {
		super();
		Activator.getDefault().setTHandler(this);
		if (collector == null) {
			collector = Activator.getDefaultCollector();
		}
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		if (collector == null) {
			collector = Activator.getDefaultCollector();
		}
		if(!collector.collectedChanges.isEmpty()){
			ConvertHandler cHandler = Activator.getDefault().getConvertHandler();
			IJavaProject javaProject = cHandler.getCurrentProject();
			cHandler.handleMethodChanges(javaProject, collector.sigFiles, collector.sigChanges);
		}else{
			System.out.println("No Changes to Check.");
		}

		return null;
	}

}
