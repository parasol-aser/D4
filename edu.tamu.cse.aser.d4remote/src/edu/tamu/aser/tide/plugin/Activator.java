package edu.tamu.aser.tide.plugin;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import edu.tamu.aser.tide.plugin.handlers.ConvertHandler;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "edu.tamu.aser.tide.plugin"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;
	private static MyJavaElementChangeReporter reporter;
	private static MyJavaElementChangeCollector collector;

//	public static IJavaProject project;
	private ConvertHandler chandler;
	/**
	 * The constructor
	 */
	public Activator() {
        //System.out.println("IN THE CONSTRUCTOR OF ACTIVATOR");
	}

	public MyJavaElementChangeReporter getMyJavaElementChangeReporter(){
		return reporter;
	}

	public MyJavaElementChangeCollector getMyJavaElementChangeCollector(){
		return collector;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		reporter = new MyJavaElementChangeReporter();
		JavaCore.addElementChangedListener(reporter, ElementChangedEvent.POST_CHANGE);//POST_CHANGE ElementChangedEvent.POST_RECONCILE
		//hook with new button
		collector = new MyJavaElementChangeCollector();
		JavaCore.addElementChangedListener(collector, ElementChangedEvent.POST_CHANGE);//POST_CHANGE ElementChangedEvent.POST_RECONCILE
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	public static MyJavaElementChangeReporter getDefaultReporter(){
		return reporter;
	}

	public static MyJavaElementChangeCollector getDefaultCollector(){
		return collector;
	}

	public ConvertHandler getConvertHandler(){
		return this.chandler;
	}


	public void setCHandler(ConvertHandler convertHandler) {

		this.chandler = convertHandler;
	}

	public static ImageDescriptor getImageDescriptor(String name) {
		String iconPath = "icons/";
		try {
			URL installURL = getDefault().getBundle().getEntry("/");
			URL url = new URL(installURL, iconPath + name);
			return ImageDescriptor.createFromURL(url);
		} catch (MalformedURLException e) {
			// should not happen
			return ImageDescriptor.getMissingImageDescriptor();
		}
	}


}
