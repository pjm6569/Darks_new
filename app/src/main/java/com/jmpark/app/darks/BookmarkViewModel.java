package com.jmpark.app.darks;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

public class BookmarkViewModel extends AndroidViewModel {

    private BookmarkDao bookmarkDao;
    private LiveData<List<Bookmark>> allBookmarks;

    public BookmarkViewModel(@NonNull Application application) {
        super(application);
        BookmarkDatabase db = BookmarkDatabase.getInstance(application);
        bookmarkDao = db.bookmarkDao();
        allBookmarks = bookmarkDao.getAllBookmarks();
    }

    public LiveData<List<Bookmark>> getFilteredBookmarks(String query) {
        if (query == null || query.isEmpty()) {
            return bookmarkDao.getAllBookmarks();
        } else {
            return bookmarkDao.searchBookmarks("%" + query + "%");
        }
    }

    public LiveData<List<Bookmark>> getAllBookmarks() {
        return allBookmarks;
    }

    public void insert(Bookmark bookmark) {
        BookmarkDatabase.databaseWriteExecutor.execute(() -> {
            int maxIndex = bookmarkDao.getMaxOrderIndex();
            bookmark.setOrderIndex(maxIndex + 1);
            bookmarkDao.insert(bookmark);
        });
    }

    public void update(Bookmark bookmark) {
        BookmarkDatabase.databaseWriteExecutor.execute(() -> {
            bookmarkDao.update(bookmark);
        });
    }

    public void updateAll(List<Bookmark> bookmarks) {
        BookmarkDatabase.databaseWriteExecutor.execute(() -> {
            bookmarkDao.updateAll(bookmarks);
        });
    }

    public void delete(Bookmark bookmark) {
        BookmarkDatabase.databaseWriteExecutor.execute(() -> {
            bookmarkDao.delete(bookmark);
        });
    }
}