import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.hssf.util.HSSFColor.GREY_25_PERCENT;
import org.apache.poi.ss.usermodel.CellStyle;

public class SubTaskSort {
	File excelInput;
	File excelOutput;
	List<Issue> issues;
	Map<String,String> knownSubTasks;
	List<String> columns;
	int keyCol, subTasksCol;
	CellStyle header;
	
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
				createCell(rowhead, col, header, data.get(col));
				sheet.autoSizeColumn(col);
			} else if(col == keyCol){
				createCell(rowhead, col, header, "Parent");
				sheet.autoSizeColumn(col);
				createCell(rowhead, col + 1, header, data.get(col));
				sheet.autoSizeColumn(col + 1);
			}else{
				createCell(rowhead, col + 1, header, data.get(col));
				sheet.autoSizeColumn(col + 1);
			}
		}
		return ++writeRow;
	}
	
	private void createCell(HSSFRow rowhead, int col, CellStyle style, String value) {
		HSSFCell cell = rowhead.createCell(col);
		cell.setCellStyle(style);
		cell.setCellValue(value);
	}

	private int toExcel(HSSFSheet sheet, Issue issue, int writeRow) {
		HSSFRow rowhead = sheet.createRow(writeRow);
		for(int col = 0; col < issue.fields.size(); col++){
			if(col < keyCol){
				rowhead.createCell(col).setCellValue(issue.fields.get(col));
			} else if(col == keyCol){
				rowhead.createCell(col).setCellValue(issue.key);
				rowhead.createCell(col + 1).setCellValue(issue.fields.get(col));
			}else{
				rowhead.createCell(col + 1).setCellValue(issue.fields.get(col));
			}
		}
		return ++writeRow;
	}
	
	private int toExcel(HSSFSheet sheet, SubTask subTask, int writeRow) {
		HSSFRow rowhead = sheet.createRow(writeRow);
		for(int col = 0; col < subTask.fields.size(); col++){
			if(col < keyCol){
				rowhead.createCell(col).setCellValue(subTask.fields.get(col));
			} else if(col == keyCol){
				rowhead.createCell(col).setCellValue(subTask.parentKey);
				rowhead.createCell(col + 1).setCellValue(subTask.fields.get(col));
			}else{
				rowhead.createCell(col + 1).setCellValue(subTask.fields.get(col));
			}
		}
		return ++writeRow;
	}

	/**Save all issues/subtasks to List<Issue> issues
	 * @param sheet Sheet to search
	 */
	private void saveIssues(HSSFSheet sheet) {
		issues = new ArrayList<Issue>();
		knownSubTasks = new TreeMap<String,String>();
		
		//Reads the "Displaying *XX* issues at XX:XX AM"
		String issuesString = sheet.getRow(2)
				.getCell(0)
				.getStringCellValue()
				.substring(11);
		issuesString = issuesString.substring(0, issuesString.indexOf(' '));
		int issuesDisplayed = Integer.valueOf(issuesString);
		
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
				else
					cellValue = getCellString(cell).trim();

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
			header = cell.getCellStyle();
			
			if (cell == null || 
				cell.getCellType() == HSSFCell.CELL_TYPE_BLANK ||
				cell.getStringCellValue().equals("")
			   ) 
				break;
			String cellValue = cell.getStringCellValue();
			
			if(cellValue.equals("Key")) keyCol = index;
			if(cellValue.equals("Sub-Tasks")) subTasksCol = index;
			columns.add(cellValue);
			index++;
		}
	}

	public static void main(String[] args) {
		String jiraIn = "C:/Users/Victor/Documents/JIRA.xls";
		String jiraOut = "C:/Users/Victor/Documents/JIRA-SubSorted.xls";
		
//		download(jiraIn, jiraOut);
		
		SubTaskSort sorter = new SubTaskSort(jiraIn, jiraOut);
		try {
			sorter.sort();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

//	private static void download(String jiraIn, String jiraOut) throws ClientProtocolException, IOException {
//		HttpClient client = new DefaultHttpClient();
//		HttpPost post = new HttpPost("https://acciodata.atlassian.net/login?");
//		HttpResponse response = null;
//		List<NameValuePair> postFields = new ArrayList<NameValuePair>(2);  
//
//		// Set the post fields
//		postFields.add(new BasicNameValuePair("username", "admin"));
//		postFields.add(new BasicNameValuePair("password", "Ad07pm1!!"));
//		post.setEntity(new UrlEncodedFormEntity(postFields, HTTP.UTF_8));
//
//		// Execute the POST request
//		response = client.execute(post);
//		post = new HttpPost("https://acciodata.atlassian.net/sr/jira.issueviews:searchrequest-excel-all-fields/temp/SearchRequest.xls?jqlQuery=ORDER+BY+key+ASC");
//		 
//		
//		String jiraURLString = "https://acciodata.atlassian.net/sr/jira.issueviews:searchrequest-excel-all-fields/temp/SearchRequest.xls?jqlQuery=ORDER+BY+key+ASC";
//		URL jiraURL;
//		try {
//			jiraURL = new URL(jiraURLString);
//			FileUtils.copyURLToFile(jiraURL, new File(jiraIn));
//		} catch (MalformedURLException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//			return;
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//			return;
//		}
//	}
}
