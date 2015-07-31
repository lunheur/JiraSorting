import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.poi.hssf.usermodel.*;
//import org.apache.poi.hssf.usermodel.HSSFRow;
//import org.apache.poi.hssf.usermodel.HSSFSheet;
//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.hssf.usermodel.HSSFCellStyle;

public class SubTaskSort {
	
	/**Where to find the file
	 * C:/Users/youruser/DIR_IN
	 */
	final static String DIR_IN = "Documents";
	
	/**Where to output the file
	 * C:/Users/youruser/DIR_OUT
	 */
	final static String DIR_OUT = "Documents";
	
	/**Name of file to find
	 * Needs to be .xls
	 */
	final static String REPORT_IN = "JIRA";
	 
	/**Name of file to find
	 * Will be .xls
	 * 
	 * NOTE:
	 * if DIR_OUT is the same as DIR_IN and REPORT_OUT is the same as REPORT_IN, it will be overwritten
	 */
	final static String REPORT_OUT = "JIRA-sorted";
	
	File excelInput;
	File excelOutput;
	List<Issue> issues;
	Map<String,String> knownSubTasks;
	List<String> columns;
	int keyCol, subTasksCol;
	int issuesDisplayed;
	HSSFCellStyle headerIn, headerOut, bodyIn, bodyOut;
	
	public SubTaskSort(String excelIn, String excelOut){
		excelInput = new File(excelIn);
		excelOutput = new File(excelOut);
	}
	
	public void sort() throws IOException{
		FileInputStream streamInput = new FileInputStream(excelInput);
		HSSFWorkbook workbook = new HSSFWorkbook(streamInput); 
		HSSFSheet sheet = workbook.getSheetAt(0);
		
		getColumns(sheet);
		saveIssues(sheet);
		toExcel();
		
		workbook.close();
	}

	private void toExcel() throws IOException {
		HSSFWorkbook workbook = new HSSFWorkbook();
		
		headerOut = workbook.createCellStyle();
		headerOut.cloneStyleFrom(headerIn);
		bodyOut = workbook.createCellStyle();
		bodyOut.cloneStyleFrom(bodyIn);
		
		HSSFSheet sheet = workbook.createSheet();		
		int writeRow = 0;
		writeRow = toExcel(sheet, columns, writeRow);
		
		for(Issue issue : issues){
			writeRow = toExcel(sheet, issue, writeRow);
			for (SubTask subTask : issue.subTasks){
				writeRow = toExcel(sheet, subTask, writeRow);
			}
		}
		
		sheet.autoSizeColumn(0);
		sheet.autoSizeColumn(keyCol);
		sheet.autoSizeColumn(keyCol + 1);
		
		FileOutputStream fileOut = new FileOutputStream(excelOutput);
		workbook.write(fileOut);
		fileOut.close();
		workbook.close();
	}

	private int toExcel(HSSFSheet sheet, List<String> data, int writeRow) {
		HSSFRow rowhead = sheet.createRow(writeRow);
		for(int col = 0; col < data.size(); col++){
			if(col < keyCol){
				createCell(rowhead, col, headerOut, data.get(col));
				sheet.autoSizeColumn(col);
			} else if(col == keyCol){
				createCell(rowhead, col, headerOut, "Parent");
				sheet.autoSizeColumn(col);
				createCell(rowhead, col + 1, headerOut, data.get(col));
				sheet.autoSizeColumn(col + 1);
			}else{
				createCell(rowhead, col + 1, headerOut, data.get(col));
				sheet.autoSizeColumn(col + 1);
			}
		}
		return ++writeRow;
	}
	
	private void createCell(HSSFRow rowhead, int col, HSSFCellStyle style, String value) {
		HSSFCell cell = rowhead.createCell(col);
		cell.setCellStyle(style);
		cell.setCellValue(value);
	}

	private int toExcel(HSSFSheet sheet, Issue issue, int writeRow) {
		HSSFRow rowhead = sheet.createRow(writeRow);
		for(int col = 0; col < issue.fields.size(); col++){
			if(col < keyCol){
				createCell(rowhead, col, bodyOut, issue.fields.get(col));
			} else if(col == keyCol){
				createCell(rowhead, col, bodyOut, issue.key);
				createCell(rowhead, col + 1, bodyOut, "");
			}else{
				createCell(rowhead, col + 1, bodyOut, issue.fields.get(col));
			}
		}
		return ++writeRow;
	}
	
	private int toExcel(HSSFSheet sheet, SubTask subTask, int writeRow) {
		HSSFRow rowhead = sheet.createRow(writeRow);
		for(int col = 0; col < subTask.fields.size(); col++){
			if(col < keyCol){
				createCell(rowhead, col, bodyOut, subTask.fields.get(col));
			} else if(col == keyCol){
				createCell(rowhead, col, bodyOut, subTask.parentKey);
				createCell(rowhead, col + 1, bodyOut, subTask.fields.get(col));
			}else{
				createCell(rowhead, col + 1, bodyOut, subTask.fields.get(col));
			}
		}
		return ++writeRow;
	}

	/**Save all issues/subtasks to List<Issue> issues
	 * @param sheet Sheet to search
	 */
	private void saveIssues(HSSFSheet sheet) {
		boolean oneTime = true;
		issues = new ArrayList<Issue>();
		knownSubTasks = new TreeMap<String,String>();
		
		//Reads the "Displaying *XX* issues at XX:XX AM"
		String issuesString = sheet.getRow(2)
				.getCell(0)
				.getStringCellValue()
				.substring(11);
		issuesString = issuesString.substring(0, issuesString.indexOf(' '));
		issuesDisplayed = Integer.valueOf(issuesString);
		
		int index = 4; //row to start at
		int count = 0; //issues found
		while(count < issuesDisplayed) {
			HSSFRow row = sheet.getRow(index); //get next row
			List<String> fields = new ArrayList<String>();
			String key = "";
			
			for (int col = 0; col < columns.size(); col++) { //iterate over columns
				HSSFCell cell = row.getCell(col);
				String cellValue;

				if (cell == null
						|| cell.getCellType() == HSSFCell.CELL_TYPE_BLANK)
					cellValue = "";
				else{
					cellValue = getCellString(cell).trim();
					if(oneTime && col == 3){
						bodyIn = cell.getCellStyle();
						oneTime = false;
					}
				}

				fields.add(cellValue);
				if (col == keyCol){
					key = cellValue;
				}
			}
			
			String parent = getParent(key);
			
			if (!key.equals("")){
				count++;
				if(parent.equals("")){
					issues.add(new Issue(key, fields));
				} else{
					getIssue(parent).addSubTask(new SubTask(key, parent, fields));
				}
			}
			
			addKnownSubTasks(key, fields.get(subTasksCol));
			
			index++;
		}
		
	}
	
	private void addKnownSubTasks(String parent, String subTaskField) {
		String[] subTasks = subTaskField.split(",");
		for (String sub : subTasks){
			if (!sub.equals(""))
				knownSubTasks.put(sub.trim(), parent);
		}
	}

	/**Searches List<Issue> issues
	 * @param getKey
	 * @return Issue object matching getKey
	 */
	private Issue getIssue(String getKey) {
		for(Issue issue : issues){
			if (issue.key.equals(getKey)){
				return issue;
			}
		}
		return null;
	}

	@SuppressWarnings("unused")
	private void printAllFields(List<String> fields){
		for(int col = 0; col < columns.size(); col++){
			System.out.println(columns.get(col) + ": " + fields.get(col));
		}
	}

	/**Get Cell value as String
	 * @param cell
	 * @return String (if numeric, with ".0"s removed)
	 */
	private String getCellString(HSSFCell cell) {
		String ret = "";
		
		switch(cell.getCellType()){
		case HSSFCell.CELL_TYPE_NUMERIC: //translate double value to String
			double d = cell.getNumericCellValue(); 
			ret = (long) d == d ? "" + (long) d : "" + d;  
			break;

		default: 
			ret = cell.getStringCellValue(); 
			break;
		}
		
		return ret;
	}

	/**Find parent from knownSubTasks tree
	 * @param key subTask to search for
	 * @return key of Parent(plus a space), "" if no parent
	 */
	private String getParent(String key) {
		if(knownSubTasks.containsKey(key))
			return knownSubTasks.remove(key);
		else
			return "";
	}

	/**Get all column headers in JIRA xls
	 * @param sheet Sheet to use
	 */
	private void getColumns(HSSFSheet sheet) {
		columns = new ArrayList<String>();
		HSSFRow colRow = sheet.getRow(3);
		
		int index = 0;
		while(true){
			HSSFCell cell = colRow.getCell(index);
			
			if (cell == null || 
				cell.getCellType() == HSSFCell.CELL_TYPE_BLANK ||
				cell.getStringCellValue().equals("")
			   )
				break;
			String cellValue = cell.getStringCellValue();
			
			if(cellValue.equals("Key")) {keyCol = index; headerIn = cell.getCellStyle();}
			if(cellValue.equals("Sub-Tasks")) subTasksCol = index;
			columns.add(cellValue);
			index++;
		}
	}

	public static void main(String[] args) {
		String jiraIn = System.getProperty("user.home").replace('\\', '/') + "/";
		jiraIn += DIR_IN + "/";
		jiraIn += REPORT_IN + ".xls";
		String jiraOut = System.getProperty("user.home").replace('\\', '/') + "/";
		jiraOut += DIR_OUT + "/";
		jiraOut += REPORT_OUT + ".xls";
				
		SubTaskSort sorter = new SubTaskSort(jiraIn, jiraOut);
		try {
			sorter.sort();
			System.out.println("Done!");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
