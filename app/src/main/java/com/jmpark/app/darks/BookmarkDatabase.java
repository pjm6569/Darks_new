package com.jmpark.app.darks;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Bookmark.class}, version = 1, exportSchema = false)
public abstract class BookmarkDatabase extends RoomDatabase {

    public abstract BookmarkDao bookmarkDao();

    private static volatile BookmarkDatabase INSTANCE;

    static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(4);

    static BookmarkDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (BookmarkDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    BookmarkDatabase.class, "bookmark_database_darks")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
