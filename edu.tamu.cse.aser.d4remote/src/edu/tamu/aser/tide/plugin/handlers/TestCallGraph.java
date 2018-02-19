package edu.tamu.aser.tide.plugin.handlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jdt.core.IJavaProject;

import com.ibm.wala.cast.java.client.JDTJavaSourceAnalysisEngine;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.client.AbstractAnalysisEngine;
import com.ibm.wala.ide.util.EclipseProjectPath;
import com.ibm.wala.ide.util.JavaEclipseProjectPath;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.io.FileProvider;

import edu.tamu.aser.tide.engine.EclipseProjectAnalysisEngine;

public class TestCallGraph {

	AbstractAnalysisEngine engine;

	public TestCallGraph(IJavaProject javaProject, String exclusionsFile, int objectSensitivityLevel, String mainMethodSignature) throws Exception {

	  openLog();

   		engine = new EclipseProjectAnalysisEngine(javaProject, objectSensitivityLevel, mainMethodSignature);
		engine.setExclusionsFile(exclusionsFile);

//		final EclipseProjectPath ep = JavaEclipseProjectPath.make(javaProject, EclipseProjectPath.AnalysisScopeType.SOURCE_FOR_PROJ);
//   		try {
//	          scope = ep.toAnalysisScope(new JavaSourceAnalysisScope());
//	            InputStream is = TestCallGraph.class.getClassLoader().getResourceAsStream(exclusionsFile);
//	            scope.setExclusions(new FileOfClasses(is));
//
//	        } catch (IOException e) {
//	          Assertions.UNREACHABLE(e.toString());
//	        }



	}

  static PrintWriter writer;

  private void openLog()
  {
    try {
      writer= new PrintWriter("log");
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  private void closeLog()
  {
    try {writer.close();} catch (Exception ex) {//ignore}
    }

  }
  public static void log(String msg)
  {
    writer.println(msg);
  }

	public void buildCallGraph() throws IllegalArgumentException, CancelException, IOException {

		//CallGraph cg	= engine.buildDefaultCallGraph();
		 engine.defaultCallGraphBuilder();//compute call graph internally
		 CallGraph cg	= engine.getCallGraph();
		Collection<CGNode> nodes = cg.getEntrypointNodes();
		for(CGNode n:nodes)
		{
			System.err.println(n.toString());
			Iterator<CGNode> sucs = cg.getSuccNodes(n);
			while(sucs.hasNext())
			{
				System.out.println(sucs.next());
			}
		}
		closeLog();

	}

}
