package jackpal.androidterm.util;

import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

import jackpal.androidterm.bookmark.BookmarkData;
import jackpal.androidterm.services.SchedulerService;

/**
 * Created by BusyWeb on 5/20/2017.
 */

public class GeneralHelper {

    public static String RootFolder = Environment.getExternalStorageDirectory().toString() + "/" + "terminalemulator/";
    public static final String RootFolderName = "terminalemulator";
    public static final String FilesFolderName = "files";
    public static final String SchedulerFileName = "scheduler.txt";
    public static final String BookmarkFileName = "bookmarks.txt";

    public static boolean CheckAndCreateAppFolders() {
        boolean retValue = false;
        try {
            String rootFolder = RootFolder;
            File appFolder = new File(rootFolder);
            if (appFolder.exists() == false){
                appFolder.mkdir();
            }

            retValue = CheckAndCreateSubFolder(rootFolder, FilesFolderName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return retValue;
    }
    public static boolean CheckAndCreateSubFolder(String rootFolder, String folderName) {
        boolean retValue = false;
        try {
            File appFolder = new File(rootFolder);
            if (appFolder.exists() == false){
                appFolder.mkdir();
            }

            File newFolder = new File(rootFolder + folderName + "/");
            if (!newFolder.exists()){
                retValue = newFolder.mkdir();
                retValue = true;
            } else {
                retValue = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return retValue;
    }

    public static ArrayList<SchedulerService.SchedulerData> LoadSchedulerData() {
        ArrayList<SchedulerService.SchedulerData> dataList = new ArrayList<SchedulerService.SchedulerData>();
        try {
            File folder = new File(RootFolder + FilesFolderName);
            if (folder.exists()) {
                File file = new File(RootFolder + FilesFolderName + "/" + SchedulerFileName);
                if (file.exists()) {
                    FileInputStream in = new FileInputStream(file);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String line = "";

                    while((line = reader.readLine()) != null) {
                        if (line != null && line.length() > 0) {
                            SchedulerService.SchedulerData data =  SchedulerService.getInstance().new SchedulerData(line);
                            data.RanAlready = false;
                            dataList.add(data);
                        }
                    }
                }
            }

            // debug: add sample data
            if (dataList.size() == 0) {
//                SchedulerService.SchedulerData data = SchedulerService.getInstance().new SchedulerData();
//                data.Name = "sample 1";
//                data.Data = "ls";
//                data.TimeOfDay = GeneralHelper.TimeStringToDate("05:30");
//                dataList.add(data);
//                SchedulerService.SchedulerData data2 = SchedulerService.getInstance().new SchedulerData();
//                data2.Name = "sample 2";
//                data2.Data = "clear";
//                data2.TimeOfDay = GeneralHelper.TimeStringToDate("04:30");
//                dataList.add(data2);
//                SchedulerService.SchedulerData data3 = SchedulerService.getInstance().new SchedulerData();
//                data3.Name = "sample 3";
//                data3.Data = "clear";
//                data3.TimeOfDay = GeneralHelper.TimeStringToDate("07:30");
//                dataList.add(data3);
            }

            if (dataList.size() > 0) {
                Collections.sort(dataList, new Comparator<SchedulerService.SchedulerData>() {
                    @Override
                    public int compare(SchedulerService.SchedulerData o1, SchedulerService.SchedulerData o2) {
                        return Long.valueOf(o1.TimeOfDay.getTime()).compareTo(Long.valueOf(o2.TimeOfDay.getTime()));
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dataList;
    }

    public static void SaveSchedulerData(ArrayList<SchedulerService.SchedulerData> dataList) {
        try {
            CheckAndCreateAppFolders();
            File file = new File(RootFolder + FilesFolderName + "/" + SchedulerFileName);
            if (file.exists()) {
                file.delete();
            }
            file = new File(RootFolder + FilesFolderName + "/" + SchedulerFileName);
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
            for(SchedulerService.SchedulerData data : dataList) {
                out.write((data.toString() + "\n").getBytes());
            }
            if (out != null) {
                out.flush();
                out.close();
                out = null;
            }
            SchedulerService service = SchedulerService.getInstance();
            if (service == null) {
                return;
            }
            service.Restart();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static Date TimeStringToDate(String value) {
        // 00:00 >> date
        Date date = new Date();
        try {
            String[] time = value.split(":");
            date.setYear(2000);
            date.setMonth(0);
            date.setDate(1);
            date.setHours(Integer.parseInt(time[0]));
            date.setMinutes(Integer.parseInt(time[1]));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return date;
    }

    public static String DateToTimeString(Date date) {
        // date >> 00:00
        String time = "00:00";
        try {
            SimpleDateFormat format = new SimpleDateFormat("HH:mm");
            time = format.format(date);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return time;
    }

    public static boolean IsSchedulerDataEquals(SchedulerService.SchedulerData data1, SchedulerService.SchedulerData data2) {
        boolean equal = false;
        try {
            equal = data1.Name.toLowerCase().equals(data2.Name.toLowerCase())
                    && data1.Data.toLowerCase().equals(data2.Data.toLowerCase())
                    && data1.TimeOfDay.getHours() == data2.TimeOfDay.getHours()
                    && data1.TimeOfDay.getMinutes() == data2.TimeOfDay.getMinutes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return equal;
    }


    public static ArrayList<BookmarkData> LoadBookmarks() {
        ArrayList<BookmarkData> dataList = new ArrayList<BookmarkData>();
        ArrayList<BookmarkData> dataListSorted = new ArrayList<BookmarkData>();
        try {
            File folder = new File(RootFolder + FilesFolderName);
            if (folder.exists()) {
                File file = new File(RootFolder + FilesFolderName + "/" + BookmarkFileName);
                if (file.exists()) {
                    FileInputStream in = new FileInputStream(file);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String line = "";

                    while((line = reader.readLine()) != null) {
                        if (line != null && line.length() > 0) {
                            BookmarkData data = new BookmarkData(line);
                            dataList.add(data);
                        }
                    }
                }
            }

            if (dataList.size() > 0) {
                Collections.sort(dataList, new Comparator<BookmarkData>() {
                    @Override
                    public int compare(BookmarkData o1, BookmarkData o2) {
                        return (o1.Data).compareTo(o2.Data);
                    }
                });

               for(BookmarkData item : dataList) {
                   if (item.Favorite) {
                       dataListSorted.add(item);
                   }
               }
                for(BookmarkData item : dataList) {
                    if (!item.Favorite) {
                        dataListSorted.add(item);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dataListSorted;
    }

    public static void SaveBookmarks(ArrayList<BookmarkData> dataList) {
        try {
            CheckAndCreateAppFolders();
            File file = new File(RootFolder + FilesFolderName + "/" + BookmarkFileName);
            if (file.exists()) {
                file.delete();
            }
            file = new File(RootFolder + FilesFolderName + "/" + BookmarkFileName);
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
            for(BookmarkData data : dataList) {
                out.write((data.toString() + "\n").getBytes());
            }
            if (out != null) {
                out.flush();
                out.close();
                out = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
