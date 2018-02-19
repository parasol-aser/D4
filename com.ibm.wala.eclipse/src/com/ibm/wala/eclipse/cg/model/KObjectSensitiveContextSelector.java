package com.ibm.wala.eclipse.cg.model;

import com.ibm.wala.analysis.reflection.JavaTypeContext;
import com.ibm.wala.analysis.typeInference.PointType;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.util.intset.EmptyIntSet;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;

/**
 *
 * @author Mohsen Vakilian
 * @author Stas Negara
 *
 */
public class KObjectSensitiveContextSelector implements ContextSelector {

	private final int objectSensitivityLevel;

	public KObjectSensitiveContextSelector(int objectSensitivityLevel) {
		this.objectSensitivityLevel = objectSensitivityLevel;
	}

	public static final ContextKey RECEIVER_STRING = new ContextKey() {
		@Override
		public String toString() {
			return "RECEIVER_STRING_KEY";
		}
	};


	public Context getCalleeTarget(CGNode caller, CallSiteReference site, IMethod callee, InstanceKey[] actualParameters) {
		if (actualParameters == null || actualParameters.length == 0 || actualParameters[0] == null) {
			// Provide a distinguishing context even when the receiver is null (e.g. in case of an invocation of a static method)
			return caller.getContext();
		}
		InstanceKey receiver = actualParameters[0];
		if (AnalysisUtils.isObjectGetClass(callee)) {
			return createReceiverContext(receiver, caller.getContext());
		} else if (AnalysisUtils.isLibraryClass(callee.getDeclaringClass()) || AnalysisUtils.isJDKClass(callee.getDeclaringClass())) {
			return createTypeContext(receiver);
		} else if (objectSensitivityLevel == 0) {
			return createTypeContext(receiver);
		} else {
			return createReceiverContext(receiver, caller.getContext());
		}
	}

	private Context createReceiverContext(InstanceKey receiver, Context callerContext) {
		ReceiverString receiverString;
		if (!(callerContext instanceof ReceiverStringContext)) {
			receiverString = new ReceiverString(receiver);
		} else {
			ReceiverString callerReceiverString = (ReceiverString) ((ReceiverStringContext) callerContext).get(RECEIVER_STRING);
			receiverString = new ReceiverString(receiver, objectSensitivityLevel, callerReceiverString);
		}
		return new ReceiverStringContext(receiverString);
	}

	private Context createTypeContext(InstanceKey receiver) {
		return new JavaTypeContext(new PointType(receiver.getConcreteType()));
	}

	private static final IntSet receiver = IntSetUtil.make(new int[] { 0 });


	public IntSet getRelevantParameters(CGNode caller, CallSiteReference site) {
		if (site.isDispatch() || site.getDeclaredTarget().getNumberOfParameters() > 0) {
			return receiver;
		} else {
			return EmptyIntSet.instance;
		}
	}

}
