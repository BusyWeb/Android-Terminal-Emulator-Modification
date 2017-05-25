package jackpal.androidterm.bookmark;

import java.util.ArrayList;

import jackpal.androidterm.util.GeneralHelper;

/**
 * Created by BusyWeb on 5/21/2017.
 */

public class BookmarkService {

    private static BookmarkService mBookmarkService = null;
    private static ArrayList<BookmarkData> mBookmarkList = new ArrayList<BookmarkData>();

    public interface IBookmarkEvent {
        public void BookmarkAdded(BookmarkData data);
        public void BookmarkRemoved(BookmarkData data);
        public void BookmarkUpdated(BookmarkData data);
    }

    private static IBookmarkEvent mBookmarkEvent;

    public static BookmarkService getInstance() {
        try {
            if (mBookmarkService == null) {
                mBookmarkService = new BookmarkService();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mBookmarkService;
    }

    public static void SetBookmarkEvent(IBookmarkEvent bookmarkEvent) {
        mBookmarkEvent = bookmarkEvent;
    }

    public static ArrayList<BookmarkData> GetBookmarks() {
        mBookmarkList = GeneralHelper.LoadBookmarks();
        return mBookmarkList;
    }

    public static boolean RemoveBookmark(BookmarkData data) {
        try {
            if (data == null) {
                return false;
            }
            for(BookmarkData item : mBookmarkList) {
                if (item.BookmarkDate.equals(data.BookmarkDate)
                        && item.Name.toLowerCase().equalsIgnoreCase(data.Name.toLowerCase())
                        && item.Data.toLowerCase().equalsIgnoreCase(data.Data.toLowerCase())
                        && item.Favorite == data.Favorite) {
                    BookmarkData cloned = item.clone();
                    mBookmarkList.remove(item);
                    if (mBookmarkEvent != null) {
                        mBookmarkEvent.BookmarkRemoved(cloned);
                    }
                    break;
                }
            }
            GeneralHelper.SaveBookmarks(mBookmarkList);
            mBookmarkList = GeneralHelper.LoadBookmarks();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static BookmarkData AddBookmark(BookmarkData data) {
        try {
            if (data == null) {
                return null;
            }
            mBookmarkList.add(data);
            GeneralHelper.SaveBookmarks(mBookmarkList);

            if (mBookmarkEvent != null) {
                mBookmarkEvent.BookmarkAdded(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean UpdateBookmark(BookmarkData data) {
        try {
            if (data == null) {
                return false;
            }
            for(BookmarkData item : mBookmarkList) {
                if (item.BookmarkDate.equals(data.BookmarkDate)
                        && item.Name.toLowerCase().equalsIgnoreCase(data.Name.toLowerCase())
                        && item.Data.toLowerCase().equalsIgnoreCase(data.Data.toLowerCase())) {
                    item.Favorite = data.Favorite;
                    item.BookmarkDate = data.BookmarkDate;
                    item.Name = data.Name;
                    item.Data = data.Data;
                    if (mBookmarkEvent != null) {
                        mBookmarkEvent.BookmarkUpdated(item);
                    }
                    break;
                }
            }
            GeneralHelper.SaveBookmarks(mBookmarkList);
            mBookmarkList = GeneralHelper.LoadBookmarks();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void Start() {
        try {
            mBookmarkList = GeneralHelper.LoadBookmarks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void Stop() {
        try {
            if (mBookmarkList != null) {
                mBookmarkList.clear();
            }
            //mBookmarkList = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
