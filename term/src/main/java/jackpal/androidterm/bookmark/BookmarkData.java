package jackpal.androidterm.bookmark;

import java.util.Date;

import jackpal.androidterm.services.SchedulerService;
import jackpal.androidterm.util.GeneralHelper;

/**
 * Created by BusyWeb on 5/21/2017.
 */

public class BookmarkData extends Object {

    public Date BookmarkDate;
    public String Name;
    public String Data;
    public boolean Favorite;

    public BookmarkData() {
        BookmarkDate = new Date();
        Name = "";
        Data = "";
    }
    public BookmarkData(String data) {
        try {
            String[] values = data.split("\\|\\|\\|");
            if (values != null && values.length > 0) {
                BookmarkDate = new Date(Long.parseLong(values[0]));
                Name = values[1];
                Data = values[2];
                Favorite = Boolean.parseBoolean(values[3]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String toString() {
        String ret = "";
        try {
            ret += String.valueOf(BookmarkDate.getTime()) + "|||";
            ret += Name + "|||";
            ret += Data + "|||";
            ret += String.valueOf(Favorite);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return  ret;
    }

    @Override
    public BookmarkData clone() {
        BookmarkData cloned = new BookmarkData();
        cloned.BookmarkDate = this.BookmarkDate;
        cloned.Name = this.Name;
        cloned.Data = this.Data;
        cloned.Favorite = this.Favorite;
        return cloned;
    }

}
