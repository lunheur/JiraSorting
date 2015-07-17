import java.util.ArrayList;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFSheet;


public class Issue {
	String key;
	List<String> fields;
	List<SubTask> subTasks;

	public Issue(String k, List<String> f) {
		key = k;
		fields = f;
		subTasks = new ArrayList<SubTask>();
	}
	
	public void addSubTask(SubTask sub){
		subTasks.add(sub);
	}
	
	public void addSubTask(List<SubTask> subs){
		subTasks.addAll(subs);
	}

}
