package edu.tamu.aser.tide.trace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import edu.tamu.aser.tide.plugin.ChangedItem;

public class ReporterChanges {

	public HashMap<ChangedItem, DetailChanges> item_change_mapping = new HashMap<>();
	//lock -> 1; thread -> 2; method -> 3
	//check when lock/thread/method stmts: 0 -> not change; 1 -> new added; -1 -> new del; 2 -> objchange;

	public ReporterChanges() {
		// TODO Auto-generated constructor stub
	}

	public Set<ChangedItem> getChangedItems(){
		return item_change_mapping.keySet();
	}

	public void add(ChangedItem item, int detail, String expr, int change){
		DetailChanges detailChanges = item_change_mapping.get(item);
		if(detailChanges == null){
			detailChanges = new DetailChanges();
		}
		detailChanges.add(detail, expr, change);
		item_change_mapping.put(item, detailChanges);
	}

	public DetailChanges getDetail(ChangedItem item){
		return item_change_mapping.get(item);
	}

	public void clear(){
		item_change_mapping.clear();
	}


}
