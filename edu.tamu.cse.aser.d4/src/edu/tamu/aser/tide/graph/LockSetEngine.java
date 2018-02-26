
package edu.tamu.aser.tide.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.tamu.aser.tide.tests.ReproduceBenchmarks;
import edu.tamu.aser.tide.trace.DLockNode;
import edu.tamu.aser.tide.trace.DUnlockNode;
import edu.tamu.aser.tide.trace.INode;
import edu.tamu.aser.tide.trace.LockPair;
import edu.tamu.aser.tide.trace.MemNode;
import edu.tamu.aser.tide.trace.WriteNode;

/**
 * Engine for computing the Lockset algorithm
 *
 * @author jeffhuang
 *
 */
public class LockSetEngine
{
	private final static boolean DEBUG = false;
	Vector<DLockNode> locktrace;
	int n_type = 0;
	int N = 0;
	Map<Integer,Integer> lock_types = new HashMap<Integer,Integer>();
	Map<Integer,Integer> type_locks = new HashMap<Integer,Integer>();

	Vector<Object> lock_index = new Vector<Object>();

	private HashMap<String,HashMap<Long,ArrayList<LockPair>>> indexedThreadLockMaps
		= new HashMap<String,HashMap<Long,ArrayList<LockPair>>>();


	public void add(String addr, long tid, LockPair lp) {
		HashMap<Long,ArrayList<LockPair>> threadlockmap = indexedThreadLockMaps.get(addr);
		if(threadlockmap == null){
			threadlockmap = new HashMap<Long,ArrayList<LockPair>>();
			indexedThreadLockMaps.put(addr, threadlockmap);
		}

		ArrayList<LockPair> lockpairs = threadlockmap.get(tid);
		if(lockpairs ==null){
			lockpairs = new ArrayList<LockPair>();
			threadlockmap.put(tid, lockpairs);
		}

		//filter out re-entrant locks for CP
//		while(!lockpairs.isEmpty()){
//			int lastPos = lockpairs.size()-1;
//			LockPair lp2 = lockpairs.get(lastPos);
//			if(lp.lock==null||(lp2.lock!=null&&lp.lock.getGID()<=lp2.lock.getGID()))
//				lockpairs.remove(lastPos);
//			else
//				break;
//		}

		lockpairs.add(lp);
	}

	public void organizeEngine() {
		SHBGraph shb = ReproduceBenchmarks.engine.shb;
//		if(DEBUG){
//			shb = Test.engine.shb;
//		}else{
//			shb = TIDECGModel.bugEngine.shb;
//		}
		for (String sig : indexedThreadLockMaps.keySet()) {
			HashMap<Long, ArrayList<LockPair>> threadlockmap = indexedThreadLockMaps.get(sig);
			for (long tid : threadlockmap.keySet()) {
				ArrayList<LockPair> locks = threadlockmap.get(tid);
				for (LockPair pair : locks) {
					DLockNode lock = ((DLockNode) pair.lock);
					ArrayList<Integer> tids = shb.getTrace(lock.getBelonging()).getTraceTids();
					for (Integer tid_add : tids) {
						ArrayList<LockPair> exists = threadlockmap.get(tid_add);
						if(exists == null){
							exists = new ArrayList<>();
						}
						exists.add(pair);
						indexedThreadLockMaps.get(sig).put((long) tid_add, exists);
					}
				}
			}
		}

	}

	public HashMap<Long,ArrayList<LockPair>> getPairWithSig(String addr){
		HashMap<Long,ArrayList<LockPair>> threadlockmap = indexedThreadLockMaps.get(addr);
		if(threadlockmap.keySet().size() == 0){
			return null;
		}else{
			return threadlockmap;
		}
	}


	//NOTE: it's possible two lockpairs overlap, because we skipped wait nodes
	public boolean hasCommonLock(long tid1, long gid1, long tid2, long gid2){
		Iterator<String> keyIt = indexedThreadLockMaps.keySet().iterator();
		while(keyIt.hasNext()){
			String key = keyIt.next();
			HashMap<Long,ArrayList<LockPair>> threadlockmap = indexedThreadLockMaps.get(key);
			ArrayList<LockPair> lockpairs1 = threadlockmap.get(tid1);
			ArrayList<LockPair> lockpairs2 = threadlockmap.get(tid2);
			if(lockpairs1!=null&&lockpairs2!=null){
				boolean hasLock1 = matchAnyLockPair(lockpairs1,gid1);
				if(hasLock1){
					boolean hasLock2 = matchAnyLockPair(lockpairs2,gid2);
					if(hasLock2)
						return true;
				}
			}
		}
		return false;
	}

	public boolean hasCommonLock(int tid1, MemNode xnode, int tid2, WriteNode wnode) {
		Iterator<String> keyIt = indexedThreadLockMaps.keySet().iterator();
		while(keyIt.hasNext()){
			String key = keyIt.next();
			HashMap<Long,ArrayList<LockPair>> threadlockmap = indexedThreadLockMaps.get(key);
			ArrayList<LockPair> lockpairs1 = threadlockmap.get(tid1);
			ArrayList<LockPair> lockpairs2 = threadlockmap.get(tid2);
			if(lockpairs1!=null&&lockpairs2!=null){
				boolean hasLock1 = matchAnyLockPair(lockpairs1, xnode, tid1);
				if(hasLock1){
					boolean hasLock2 = matchAnyLockPair(lockpairs2, xnode, tid2);
					if(hasLock2)
						return true;
				}
			}
		}
		return false;
	}


//	public boolean isAtomic(long tid1, long gid1a, long gid1b, long tid2, long gid2)
//	{
//		Iterator<HashMap<Long,ArrayList<LockPair>>> threadlockmapIt
//				= indexedThreadLockMaps.values().iterator();
//		while(threadlockmapIt.hasNext())
//		{
//			HashMap<Long,ArrayList<LockPair>> threadlockmap = threadlockmapIt.next();
//			ArrayList<LockPair> lockpairs1 = threadlockmap.get(tid1);
//			ArrayList<LockPair> lockpairs2 = threadlockmap.get(tid2);
//			if(lockpairs1!=null&&lockpairs2!=null)
//			{
//				boolean hasLock2 = matchAnyLockPair(lockpairs2,gid2);
//				if(hasLock2)
//				{
//					boolean hasLock1 = matchAnyLockPair(lockpairs1,gid1a,gid1b);
//					if(hasLock1)
//						return true;
//				}
//			}
//		}
//
//		return false;
//	}
//
//	private boolean matchAnyLockPair(ArrayList<LockPair> lockpair,long gida,long gidb)
//	{
//		int s, e, mid;
//
//		s = 0;
//		e = lockpair.size()-1;
//		while ( s <= e )
//		{
//			mid = ( s + e ) / 2;
//
//			LockPair lp = lockpair.get(mid);
//
//			if(lp.lock==null)
//			{
//				if(gidb<lp.unlock.getGID())
//					return true;
//				else
//				{
//					s = mid+1;
//				}
//			}
//			else if(lp.unlock==null)
//			{
//				if(gida>lp.lock.getGID())
//					return true;
//				else
//				{
//					e = mid - 1;
//				}
//			}
//			else
//			{
//				if(gida>lp.unlock.getGID())
//					s = mid+1;
//				else if(gidb<lp.lock.getGID())
//					e = mid - 1;
//				else if(lp.lock.getGID()<gida&&gidb<lp.unlock.getGID())
//					return true;
//				else
//					return false;
//			}
//		}
//
//		return false;
//	}


	private boolean matchAnyLockPair(ArrayList<LockPair> lockpairs, MemNode xnode, int tid) {
		SHBGraph shb = ReproduceBenchmarks.engine.shb;
//		if(DEBUG){
//			shb = Test.engine.shb;
//		}else{
//			shb = TIDECGModel.bugEngine.shb;
//		}
		for (LockPair lockPair : lockpairs) {
			DLockNode lockNode = (DLockNode) lockPair.lock;
			DUnlockNode unlockNode = (DUnlockNode) lockPair.unlock;
			CGNode xCgNode = xnode.getBelonging();
			if(lockNode.getBelonging().equals(xCgNode)){
				//same trace
				ArrayList<INode> list = shb.getTrace(xCgNode).getContent();
				int idxX = list.indexOf(xnode);
				int idxL = list.indexOf(lockNode);
				if(idxX > idxL){// lock -> xnode
					int idxU = list.indexOf(unlockNode);
					if(idxU > idxX){// xnode -> unlock
						return true;
					}
				}
			}else{
				//reverse traverse the shb edge with tid
				SHBEdge parent = shb.edgeManager.getIncomingEdgeWithTid(xCgNode, tid);
				while(parent != null){
					CGNode cgNode_p = parent.getSource().getBelonging();
					ArrayList<INode> list_p = shb.getTrace(cgNode_p).getContent();
					if(list_p.contains(lockNode)){
						INode insert = parent.getSource();
						int idxI = list_p.indexOf(insert);
						int idxL = list_p.indexOf(lockNode);
						if(idxI > idxL){
							int idxU = list_p.indexOf(unlockNode);
							if(idxI > idxU){
								return true;
							}
						}
					}else{
						//find parent's parent
						parent = shb.edgeManager.getIncomingEdgeWithTid(cgNode_p, tid);
					}
				}
			}
		}

		return false;
	}

	private boolean matchAnyLockPair(ArrayList<LockPair> lockpair,long gid){
		int s, e, mid;
		s = 0;
		e = lockpair.size()-1;
		while ( s <= e ){
			mid = ( s + e ) / 2;
			LockPair lp = lockpair.get(mid);

			if(lp.lock==null){
//				if(gid<lp.unlock.getGID())
//					return true;
//				else{
//					s = mid+1;
//				}
			}else if(lp.unlock==null){
//				if(gid>lp.lock.getGID())
//					return true;
//				else{
//					e = mid - 1;
//				}
			}else{
//				if(gid>lp.unlock.getGID())
//					s = mid+1;
//				else if(gid<lp.lock.getGID())
//					e = mid - 1;
//				else
//					return true;
			}
		}

		return false;
	}



	public ArrayList<LockPair> getPairsInThread(int curTID) {
		ArrayList<LockPair> result = new ArrayList<LockPair>();
		for (String sig : indexedThreadLockMaps.keySet()) {
			HashMap<Long,ArrayList<LockPair>> content = indexedThreadLockMaps.get(sig);
			result.addAll(content.get(curTID));
		}
		return result;
	}



	public void removeAll(int curTID, ArrayList<LockPair> deletedOldPairs) {
		for (String sig : indexedThreadLockMaps.keySet()) {
			HashMap<Long,ArrayList<LockPair>> content = indexedThreadLockMaps.get(sig);
			ArrayList<LockPair> inside = content.get(curTID);
			if(inside != null){
				inside.removeAll(deletedOldPairs);
			}
		}
	}

	public void removePairs(HashMap<Long, ArrayList<LockPair>> old_pairs) {
		for (long tid : old_pairs.keySet()) {
			indexedThreadLockMaps.remove(tid, old_pairs.get(tid));
		}
	}

	public LockPair removeAllPairsWithSigs(HashSet<String> old_sigs, DLockNode locknode) {
		LockPair lp = null;
		//found replace
		for (String old : old_sigs) {
			HashMap<Long,ArrayList<LockPair>> oldpairs = getPairWithSig(old);
			for (long tid : oldpairs.keySet()) {
				ArrayList<LockPair> pairs = oldpairs.get(tid);
				for (LockPair lockPair : pairs) {
					if (lockPair.lock.equals(locknode)) {//?
						//delete
						indexedThreadLockMaps.get(old).get(tid).remove(lockPair);
						lp = lockPair;
					}
				}
			}
		}
		return lp;
	}






}
