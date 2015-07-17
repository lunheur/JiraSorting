import java.util.List;


public class SubTask {
	String key;
	String parentKey;
	List<String> fields;
	

	public SubTask(String k, String p, List<String> f) {
		key = k;
		fields = f;
		parentKey = p;
	}
	
	public SubTask(Issue issue, String p) {
		key = issue.key;
		fields = issue.fields;
		parentKey = p;
	}
}
