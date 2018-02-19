package edu.tamu.aser.tide.plugin;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.internal.core.SourceType;

public class MyPropTester extends PropertyTester{

    @Override
    public boolean test(Object receiver, String property, Object[] args,
            Object expectedValue) {

    	System.out.println("I'm in MyPropTester");

        try {

        	ICompilationUnit cu=(ICompilationUnit)receiver;
        	for(IJavaElement e: cu.getChildren())
        	{
        		if(e instanceof SourceType)
        		{
        			SourceType st = (SourceType)e;
        			for (IMethod m: st.getMethods())
        			//if(cu instanceof IMethod)
        			{
        				//IMethod m = (IMethod)cu;
            			if((m.getFlags()&Flags.AccStatic)>0
            					&&(m.getFlags()&Flags.AccPublic)>0
            					&&m.getElementName().equals("main")
            					&&m.getSignature().equals("([QString;)V"))
            			{
                			//System.out.println(m);

            				return true;

            			}
        			}
        		}
        	}

                    return false;


        } catch (CoreException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return false;
    }

}