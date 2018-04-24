package edu.tamu.aser.tide.plugin.handlers;

import java.util.HashSet;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.internal.core.SourceField;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import edu.tamu.aser.tide.engine.ITIDEBug;
import edu.tamu.aser.tide.engine.TIDEDeadlock;
import edu.tamu.aser.tide.engine.TIDEEngine;
import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.views.DeadlockNode;
import edu.tamu.aser.tide.views.EchoRaceView;
import edu.tamu.aser.tide.views.EchoReadWriteView;
import edu.tamu.aser.tide.views.RWRelationNode;
import edu.tamu.aser.tide.views.RaceNode;

/**
 * do not consider this variable with this sig any more, as well as all other bugs involving this sig and variable.
 * @author Bozhen
 *
 */
public class IgnoreVariableHandler extends AbstractHandler {

	//set the echoview to remove selected bugs
	public ConvertHandler cHandler;
	public EchoRaceView echoRaceView;
	public EchoReadWriteView echoRWView;
	public TIDEEngine engine;

	public IgnoreVariableHandler() {
		super();
		cHandler = edu.tamu.aser.tide.plugin.Activator.getDefault().getConvertHandler();
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		boolean ignore = false;
		String command = "";
		try {
			command = event.getCommand().getName();
		} catch (NotDefinedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(command.equals("Consider This Variable")){
			ignore = false;
		}else if(command.equals("Ignore This Variable")){//ignore this variable
			ignore = true;
		}else{
			return null;
		}

		if(cHandler == null){
			cHandler = edu.tamu.aser.tide.plugin.Activator.getDefault().getConvertHandler();
			if(cHandler == null)
				return null;
		}
		echoRaceView = cHandler.getEchoRaceView();
		echoRWView = cHandler.getEchoReadWriteView();
		engine = cHandler.getCurrentModel().getBugEngine();
		//get the node
		ISelection sel = HandlerUtil.getActiveMenuSelection(event);
		TreeSelection treeSel = (TreeSelection) sel;
		//ifile:
		TreePath path = treeSel.getPaths()[0];
		IPath ipath = ((SourceType)path.getSegment(0)).getParent().getPath();
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(ipath);
		Object element = treeSel.getFirstElement();
		HashSet<ITIDEBug> all = new HashSet<>();
		//option from echoviews
		if(element instanceof RaceNode){
			RaceNode racenode = (RaceNode) element;
			TIDERace race = racenode.getRace();
			String excludedSig = race.sig;
			if(ignore){//ignore this variable
				HashSet<TIDERace> others = engine.excludeThisSigForRace(race, excludedSig);
				all.addAll(others);
				all.add(race);
			}else{//consider this variable
				HashSet<ITIDEBug> others = engine.considerThisSigForRace(race, excludedSig);
				all.addAll(others);
				all.add(race);
			}
		}else if(element instanceof DeadlockNode){
			DeadlockNode dlnode = (DeadlockNode) sel;
			TIDEDeadlock deadlock = dlnode.getDeadlock();
			//continue ...
			//remove the marker from editor
			//remove the bug from echoview
		}else if(element instanceof RWRelationNode){
			RWRelationNode relation = (RWRelationNode) element;
			String excludedSig = relation.getSig();
			HashSet<TIDERace> races = relation.getRaces();
			if(ignore){//ignore this variable
				engine.excludeThisSigForRace(races, excludedSig);
				all.addAll(races);
			}else{//consider this variable
				engine.considerThisSigForRace(races, excludedSig);
				all.addAll(races);
			}
		}
		//option from outline:
		else if(element instanceof SourceField){
			//The type 'SourceField' is not API
			//(restriction on required library '/Applications/Eclipse.app/Contents/Eclipse/plugins/org.eclipse.jdt.core_3.11.2.v20160128-0629.jar')
			SourceField field = (SourceField) element;
			String name = field.getElementName();
			String classname = field.getParent().getElementName();
			String packagename = field.getParent().getParent().getParent().getElementName() + "/";
			if(packagename.length() == 1){
				packagename = "";
			}
			String excludedSig = packagename +classname + "." + name;
			if(ignore){//ignore this variable
				HashSet<TIDERace> related = engine.excludeThisSigForRace(excludedSig);
				all.addAll(related);
			}else{//consider this variable
				HashSet<ITIDEBug> related = engine.considerThisSigForRace(excludedSig);
				all.addAll(related);
			}
		}
		if(ignore){//ignore this variable
			//remove the marker from editor
			cHandler.getCurrentModel().removeBugMarkersForIgnore(all);
			//remove the bug from echoview
			echoRaceView.ignoreBugs(all);
			echoRWView.ignoreBugs(all);
			System.out.println("Ignore variable bugs removed ");
		}else{
			try {
				cHandler.getCurrentModel().addBugMarkersForConsider(all, file);
			} catch (CoreException e) {
				e.printStackTrace();
			}
			echoRaceView.considerBugs(all);
			echoRWView.considerBugs(all);
			System.out.println("Consider variable bugs back ");
		}
		return null;
	}

}
