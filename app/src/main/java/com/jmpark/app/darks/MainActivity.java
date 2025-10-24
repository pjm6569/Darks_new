package com.jmpark.app.darks; // 사용자의 패키지 이름

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
// [!!!] 임포트 추가 [!!!]
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
// [!!!] 임포트 추가 [!!!]
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;

import java.util.ArrayList; // NPE 방지
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BookmarkViewModel bookmarkViewModel;
    private BookmarkAdapter adapter;
    private RecyclerView recyclerView;
    private ItemTouchHelper itemTouchHelper;
    private Bookmark longClickedBookmark;

    // !!! 본인의 GitHub 정보로 반드시 변경하세요 !!!
    private static final String GITHUB_API_URL = "https://api.github.com/repos/pjm6569/Darks_new/releases/latest";
    private static final String TAG = "MainActivity_Update";

    // --- 설정 저장을 위한 SharedPreferences ---
    public static final String PREF_CLICK_ACTION = "pref_click_action";
    public static final String ACTION_SHARE = "SHARE";
    public static final String ACTION_CHOOSER = "CHOOSER";

    // [!!!] 레이아웃 모드 저장을 위한 키 [!!!]
    public static final String PREF_LAYOUT_MODE = "pref_layout_mode";
    public static final String LAYOUT_LIST = "LIST";
    public static final String LAYOUT_GRID = "GRID";

    private SharedPreferences prefs;
    private String currentLayoutMode; // 현재 레이아웃 모드 저장


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        recyclerView = findViewById(R.id.recycler_view);

        // 어댑터 설정 (LayoutManager 설정 전)
        adapter = new BookmarkAdapter();
        recyclerView.setAdapter(adapter);

        // [!!!] 저장된 레이아웃 모드 불러오기 (기본값: LIST) [!!!]
        currentLayoutMode = prefs.getString(PREF_LAYOUT_MODE, LAYOUT_LIST);
        // [!!!] RecyclerView 레이아웃 매니저 설정 [!!!]
        setupLayoutManager();

        bookmarkViewModel = new ViewModelProvider(this).get(BookmarkViewModel.class);
        bookmarkViewModel.getAllBookmarks().observe(this, bookmarks -> {
            // NullPointerException 방지
            if (bookmarks != null) {
                adapter.setBookmarks(bookmarks);
            }
        });

        FloatingActionButton fab = findViewById(R.id.fab_add_bookmark);
        fab.setOnClickListener(v -> {
            showAddEditDialog(null);
        });

        setupItemTouchHelper();
        adapter.setItemTouchHelper(itemTouchHelper);
        setupContextMenu();

        // 앱 시작 시 업데이트 확인
        checkForUpdates();
    }

    // [!!!] 현재 모드에 따라 LayoutManager를 설정하는 메서드 [!!!]
    private void setupLayoutManager() {
        if (currentLayoutMode.equals(LAYOUT_GRID)) {
            // 격자 뷰 (2열)
            recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        } else {
            // 일렬 리스트 뷰
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
        }
    }


    // --- 드래그 앤 드롭 ---
    private void setupItemTouchHelper() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                // [!!!] 격자 뷰를 위해 좌우 드래그 추가 [!!!]
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                0) { // 스와이프는 0 (사용 안 함)

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();

                // 데이터 유효성 검사
                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                    return false;
                }

                List<Bookmark> list = adapter.getList();
                // IndexOutOfBoundsException 방지
                if (list == null || fromPosition < 0 || fromPosition >= list.size() || toPosition < 0 || toPosition >= list.size()) {
                    return false;
                }

                // [!!!] 격자 뷰 드래그 수정 (Swap -> Remove/Add) [!!!]
                // Collections.swap(list, fromPosition, toPosition); (X)
                Bookmark item = list.remove(fromPosition); // 1. 아이템을 뺍니다.
                list.add(toPosition, item); // 2. 원하는 위치에 삽입합니다. (나머지는 밀려남)

                adapter.notifyItemMoved(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 스와이프 사용 안 함
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // 드래그가 끝나면 순서 인덱스를 업데이트
                List<Bookmark> updatedList = adapter.getList();
                // NullPointerException 방지
                if (updatedList == null) return;

                for (int i = 0; i < updatedList.size(); i++) {
                    if (updatedList.get(i) != null) { // 리스트 내 null 객체 방지
                        updatedList.get(i).setOrderIndex(i);
                    }
                }
                bookmarkViewModel.updateAll(updatedList);
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false; // 핸들 아이콘으로만 드래그
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    // --- 컨텍스트 메뉴 (편집/삭제) ---
    private void setupContextMenu() {
        adapter.setOnItemLongClickListener(bookmark -> {
            longClickedBookmark = bookmark; // 롱클릭된 아이템 저장
        });
        registerForContextMenu(recyclerView);
    }

    @Override
    public void onCreateContextMenu(android.view.ContextMenu menu, View v, android.view.ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.bookmark_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (longClickedBookmark == null) {
            return super.onContextItemSelected(item);
        }

        int id = item.getItemId();
        if (id == R.id.menu_edit) {
            showAddEditDialog(longClickedBookmark);
            longClickedBookmark = null; // 사용 후 초기화
            return true;
        } else if (id == R.id.menu_delete) {
            new AlertDialog.Builder(this)
                    .setTitle("삭제 확인")
                    .setMessage("'" + longClickedBookmark.getTitle() + "' 북마크를 삭제하시겠습니까?")
                    .setPositiveButton("삭제", (dialog, which) -> {
                        bookmarkViewModel.delete(longClickedBookmark);
                        Toast.makeText(this, "삭제되었습니다.", Toast.LENGTH_SHORT).show();
                        longClickedBookmark = null; // 사용 후 초기화
                    })
                    .setNegativeButton("취소", (dialog, which) -> {
                        longClickedBookmark = null; // 사용 후 초기화
                    })
                    .show();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    // 헬퍼 메서드 (URL 멈춤 방지)
    private String ensureHttps(String url) {
        if (url == null || url.trim().isEmpty()) {
            return url;
        }
        String trimmedUrl = url.trim();
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return "https://" + trimmedUrl;
        }
        return trimmedUrl;
    }

    // --- 추가/편집 다이얼로그 ---
    private void showAddEditDialog(Bookmark bookmark) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_bookmark, null);
        final EditText editTitle = view.findViewById(R.id.edit_text_title);
        final EditText editUrl = view.findViewById(R.id.edit_text_url);

        String dialogTitle = (bookmark == null) ? "새 북마크 추가" : "북마크 편집";
        builder.setTitle(dialogTitle);

        if (bookmark != null) {
            editTitle.setText(bookmark.getTitle());
            editUrl.setText(bookmark.getUrl());
        }

        builder.setView(view);
        builder.setPositiveButton("저장", (dialog, which) -> {
            String title = editTitle.getText().toString().trim();
            String url = editUrl.getText().toString().trim();

            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(url)) {
                Toast.makeText(this, "제목과 URL을 모두 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            url = ensureHttps(url); // https 보정

            if (bookmark == null) {
                // 추가 (orderIndex는 ViewModel에서 자동 설정)
                Bookmark newBookmark = new Bookmark(title, url, 0);
                bookmarkViewModel.insert(newBookmark);
                Toast.makeText(this, "추가되었습니다.", Toast.LENGTH_SHORT).show();
            } else {
                // 수정
                bookmark.setTitle(title);
                bookmark.setUrl(url);
                bookmarkViewModel.update(bookmark);
                Toast.makeText(this, "수정되었습니다.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("취소", null);
        builder.show();
    }

    // --- 메뉴 생성 및 핸들러 ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // 검색창 설정
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint("제목 또는 URL 검색");
        EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);

        if (searchEditText != null) {
            // 2. 검색어 입력 시 텍스트 색상 (흰색)
            searchEditText.setTextColor(Color.WHITE);

            // 3. 힌트 텍스트 색상 (흰색)
            searchEditText.setHintTextColor(Color.WHITE);
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // LiveData 관찰 로직 개선 (Null 체크)
                bookmarkViewModel.getFilteredBookmarks(newText).observe(MainActivity.this, bookmarks -> {
                    if (bookmarks != null) {
                        adapter.setBookmarks(bookmarks);
                    }
                });
                return true;
            }
        });

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                bookmarkViewModel.getAllBookmarks().observe(MainActivity.this, bookmarks -> {
                    if (bookmarks != null) {
                        adapter.setBookmarks(bookmarks);
                    }
                });
                return true;
            }
        });

        // [!!!] 레이아웃 토글 아이콘 설정 [!!!]
        // onCreateOptionsMenu는 메뉴가 처음 생성될 때, 그리고 invalidateOptionsMenu() 호출 시 다시 실행됩니다.
        MenuItem toggleItem = menu.findItem(R.id.action_toggle_layout);
        if (currentLayoutMode.equals(LAYOUT_GRID)) {
            // 현재 격자 -> '리스트' 아이콘 표시
            toggleItem.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_view_list));
            toggleItem.setTitle("리스트 뷰로 보기"); // 접근성 향상
        } else {
            // 현재 리스트 -> '격자' 아이콘 표시
            toggleItem.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_view_grid));
            toggleItem.setTitle("격자 뷰로 보기"); // 접근성 향상
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            // 설정 클릭
            showSettingsDialog();
            return true;
        } else if (id == R.id.action_toggle_layout) {
            // [!!!] 레이아웃 토글 클릭 [!!!]
            toggleLayoutMode();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // [!!!] 레이아웃 모드 변경 메서드 [!!!]
    private void toggleLayoutMode() {
        SharedPreferences.Editor editor = prefs.edit();

        if (currentLayoutMode.equals(LAYOUT_GRID)) {
            // 격자 -> 리스트로 변경
            currentLayoutMode = LAYOUT_LIST;
            editor.putString(PREF_LAYOUT_MODE, LAYOUT_LIST);
        } else {
            // 리스트 -> 격자로 변경
            currentLayoutMode = LAYOUT_GRID;
            editor.putString(PREF_LAYOUT_MODE, LAYOUT_GRID);
        }

        editor.apply();

        // 1. RecyclerView의 레이아웃 매니저 교체
        setupLayoutManager();
        // 2. 툴바의 아이콘을 갱신 (onCreateOptionsMenu를 다시 호출)
        invalidateOptionsMenu();
    }


    // --- 설정 다이얼로그 ---
    private void showSettingsDialog() {
        String[] items = {"공유하기 창 (안정적)", "브라우저 선택창 (실험적)"};
        String currentAction = prefs.getString(PREF_CLICK_ACTION, ACTION_SHARE);
        int checkedItem = currentAction.equals(ACTION_CHOOSER) ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle("클릭 시 동작 설정")
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    if (which == 1) {
                        editor.putString(PREF_CLICK_ACTION, ACTION_CHOOSER);
                        Toast.makeText(this, "브라우저 선택창으로 설정 (기기에서 작동하지 않을 수 있음)", Toast.LENGTH_LONG).show();
                    } else {
                        editor.putString(PREF_CLICK_ACTION, ACTION_SHARE);
                        Toast.makeText(this, "공유하기 창으로 설정", Toast.LENGTH_SHORT).show();
                    }
                    editor.apply();
                    dialog.dismiss();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // --- GitHub 업데이트 확인 로직 ---
    private String getCurrentVersionName() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
    private void checkForUpdates() {
        if (GITHUB_API_URL.contains("pjm6569")) {
            Log.e(TAG, "GitHub API URL을 설정하세요. (MainActivity.java)");
            return;
        }
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.GET, GITHUB_API_URL, null,
                response -> {
                    try {
                        String latestTag = response.getString("tag_name");
                        String releasePageUrl = response.getString("html_url");
                        String latestVersion = latestTag.replace("v", "").trim();
                        String currentVersion = getCurrentVersionName();
                        Log.d(TAG, "Current Version: " + currentVersion + ", Latest Version: " + latestVersion);
                        if (currentVersion != null && !currentVersion.equals(latestVersion)) {
                            showUpdateDialog(latestVersion, releasePageUrl);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON Parsing Error: " + e.getMessage());
                    }
                },
                error -> Log.e(TAG, "Volley Error: " + error.toString())
        );
        queue.add(jsonObjectRequest);
    }
    private void showUpdateDialog(String newVersion, String releaseUrl) {
        new AlertDialog.Builder(this)
                .setTitle("새 버전 알림 (Darks)")
                .setMessage("새로운 버전 (" + newVersion + ")이 있습니다.\nGitHub 릴리스 페이지로 이동하시겠습니까?")
                .setPositiveButton("업데이트", (dialog, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl));
                    startActivity(browserIntent);
                })
                .setNegativeButton("나중에", null)
                .show();
    }
}

