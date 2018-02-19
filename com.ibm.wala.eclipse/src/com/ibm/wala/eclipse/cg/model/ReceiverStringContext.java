package com.ibm.wala.eclipse.cg.model;

import com.ibm.wala.eclipse.cg.model.KObjectSensitiveContextSelector;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

/**
 *
 * @author Mohsen Vakilian
 * @author Stas Negara
 *
 *
 */
public class ReceiverStringContext implements Context {
	private final ReceiverString receiverString;

	public ReceiverStringContext(ReceiverString receiverString) {
		if (receiverString == null) {
			throw new IllegalArgumentException("null receiverString");
		}
		this.receiverString = receiverString;
	}

	public InstanceKey getReceiver() {
		return receiverString.getReceiver();
	}

	@Override
	public boolean equals(Object o) {
		return (o instanceof ReceiverStringContext) && ((ReceiverStringContext) o).receiverString.equals(receiverString);
	}

	@Override
	public int hashCode() {
		return receiverString.hashCode();
	}

	@Override
	public String toString() {
		return "ReceiverStringContext: " + receiverString.toString();
	}

	public ContextItem get(ContextKey name) {
		if (KObjectSensitiveContextSelector.RECEIVER_STRING.equals(name)) {
			return receiverString;
		} else {
			return null;
		}
	}
}