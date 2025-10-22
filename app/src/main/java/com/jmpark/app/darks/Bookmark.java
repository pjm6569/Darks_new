package com.jmpark.app.darks; // 본인의 패키지 이름으로 변경하세요

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "bookmarks")
public class Bookmark {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String title;
    private String url;

    // 순서 저장을 위한 인덱스
    @ColumnInfo(name = "order_index")
    private int orderIndex;

    // 생성자
    public Bookmark(String title, String url, int orderIndex) {
        this.title = title;
        this.url = url;
        this.orderIndex = orderIndex;
    }

    // --- Getter 및 Setter ---
    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
