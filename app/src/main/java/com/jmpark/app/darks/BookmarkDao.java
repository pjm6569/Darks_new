package com.jmpark.app.darks; // 본인의 패키지 이름

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BookmarkDao {

    @Insert
    void insert(Bookmark bookmark);

    @Update
    void update(Bookmark bookmark);

    @Update
    void updateAll(List<Bookmark> bookmarks);

    @Delete
    void delete(Bookmark bookmark);

    @Query("SELECT * FROM bookmarks ORDER BY order_index ASC")
    LiveData<List<Bookmark>> getAllBookmarks();

    @Query("SELECT * FROM bookmarks WHERE title LIKE :query OR url LIKE :query ORDER BY order_index ASC")
    LiveData<List<Bookmark>> searchBookmarks(String query);

    @Query("SELECT MAX(order_index) FROM bookmarks")
    int getMaxOrderIndex();
}
