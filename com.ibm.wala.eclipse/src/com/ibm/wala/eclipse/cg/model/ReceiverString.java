package com.ibm.wala.eclipse.cg.model;

import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

/**
 *
 * @author Mohsen Vakilian
 * @author Stas Negara
 *
 */
public class ReceiverString implements ContextItem {

	private final InstanceKey instances[];

	public ReceiverString(InstanceKey instanceKey) {
		this.instances = new InstanceKey[] { instanceKey };
	}

	ReceiverString(InstanceKey instanceKey, int max_length, ReceiverString base) {
		int instancesLength = Math.min(max_length, base.getCurrentLength() + 1);
		instances = new InstanceKey[instancesLength];
		instances[0] = instanceKey;
		System.arraycopy(base.instances, 0, instances, 1, instancesLength - 1);
	}

	private int getCurrentLength() {
		return instances.length;
	}

	public InstanceKey getReceiver() {
		return instances[0];
	}

//	@Override
//	public String toString() {
//		return "[" + Joiner.on(" :: ").join(instances) + "]";
//	}

	@Override
	public int hashCode() {
		int code = 11;
		for (int i = 0; i < instances.length; i++) {
			code *= instances[i].hashCode();
		}
		return code;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ReceiverString) {
			ReceiverString oc = (ReceiverString) o;
			if (oc.instances.length == instances.length) {
				for (int i = 0; i < instances.length; i++) {
					if (!(instances[i].equals(oc.instances[i]))) {
						return false;
					}
				}
				return true;
			}
		}
		return false;
	}

}
