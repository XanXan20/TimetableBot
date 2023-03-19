package ru.krasilnikov.tgbots.polytech_timetamble_bot.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class XLSFileReader{

    private  XSSFSheet sheet;
    private File file;
    private  Map<Integer, Integer> groupIdToColumn;
    private  ArrayList<Integer> groupIdList;

    public void update() throws IOException{
        this.file = new File("/home/TimetableBot/data/actualTimetable.xls");
        groupIdToColumn = new HashMap<>();
        groupIdList = new ArrayList<>();
        FileInputStream fileInputStream = new FileInputStream("/home/TimetableBot/data/actualTimetable.xls");
        XSSFWorkbook workbook = new XSSFWorkbook(fileInputStream);

        sheet = workbook.getSheet("Лист1");

        XSSFRow row = sheet.getRow(3);

        Iterator<Cell> cellIterator = row.cellIterator();

        while(cellIterator.hasNext()){
            Cell cell = cellIterator.next();

            int cellColumn = cell.getColumnIndex();
            int cellValue = (int)cell.getNumericCellValue();

            if(cellValue == 0.0)
                continue;

            groupIdList.add(cellValue);
            groupIdToColumn.put(cellValue, cellColumn);

        }
        workbook.close();
    }

    public Map<Integer, String> getGroupTimetable(int groupId) {
        Map<Integer, String> groupTimetable = new HashMap<>();

        int groupColumn = groupIdToColumn.get(groupId);

        int lastRow = 19;
        boolean isMonday = false;

        for (int i = 4; i < lastRow; i++) {
            XSSFRow row = sheet.getRow(i);
            try{
                XSSFCell cell = row.getCell(groupColumn);
                int lesionNumber = (int)row.getCell(groupColumn-1).getNumericCellValue();
                String lesionName = cell.getStringCellValue();

                if(!(lesionName.equals("") || lesionName.contains("Классный час"))){
                    boolean isInMegre = false;
                    int mergesCount = sheet.getNumMergedRegions();
                    for(int j = 0; j<mergesCount; j++){
                        CellRangeAddress cellRange = sheet.getMergedRegion(j);
                        if(cellRange.isInRange(cell)){
                            isInMegre = true;
                            int firstMergeRowId = cellRange.getFirstRow();
                            int lastMergedRowId = cellRange.getLastRow();

                            for(int k = firstMergeRowId; k <= lastMergedRowId; k++){
                                groupTimetable.put((int)sheet.getRow(k).getCell(groupColumn-1).getNumericCellValue(), lesionName);
                                //по идее, в понедельник число i будет не 4, а больше (типа отступ) поэтому мб придется поменять
                            }
                        }
                    }
                    if(!isInMegre)
                        groupTimetable.put(lesionNumber, lesionName);
                }
            }catch (NullPointerException e){}
        }
        return groupTimetable;
    }

    public ArrayList<Integer> getGroupIdList() {
        return groupIdList;
    }
    public File getFile(){
        return this.file;
    }
}
