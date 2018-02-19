package edu.tamu.aser.tide.plugin.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.widgets.Event;

import edu.tamu.aser.tide.plugin.Activator;
import edu.tamu.aser.tide.plugin.MyJavaElementChangeCollector;
import edu.tamu.aser.tide.plugin.MyJavaElementChangeReporter;

public class ChooseModeHandler extends AbstractHandler{

	private boolean buttonmode = false;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
			String command = ((Event) event.getTrigger()).widget.toString();
			MyJavaElementChangeReporter reporter = Activator.getDefault().getMyJavaElementChangeReporter();
			MyJavaElementChangeCollector collector = Activator.getDefault().getMyJavaElementChangeCollector();
			if(command.contains("Button Mode")){
				buttonmode = true;
				collector.work(true);
				reporter.work(false);
				System.out.println("Button Mode");
			}else{
				//ctrl+s mode
				collector.work(false);
				reporter.work(true);
				System.out.println("Ctrl+S Mode");
			}
		return null;
	}

	/**
	 * true -> button mode; false -> ctrl+s mode
	 * @return
	 */
	public boolean getECHOMode() {
		return buttonmode;
	}

}
