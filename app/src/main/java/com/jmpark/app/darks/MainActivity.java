package com.jmpark.app.darks; // 사용자의 패키지 이름

import android.content.Intent;
// [!!!] SharedPreferences 임포트 [!!!]
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
// [!!!] PreferenceManager 임포트 [!!!]
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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;

import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BookmarkViewModel bookmarkViewModel;
    private BookmarkAdapter adapter;
    private RecyclerView recyclerView;
    private ItemTouchHelper itemTouchHelper;
    private Bookmark longClickedBookmark;

    // --- 업데이트 확인용 ---
    private static final String GITHUB_API_URL = "https://api.github.com/repos/pjm6569/Darks_new/releases/latest";
    private static final String TAG = "MainActivity_Update";

    // [!!!] 설정 저장을 위한 SharedPreferences [!!!]
    public static final String PREF_CLICK_ACTION = "pref_click_action";
    public static final String ACTION_SHARE = "SHARE"; // 공유하기
    public static final String ACTION_CHOOSER = "CHOOSER"; // 브라우저 선택창
    private SharedPreferences prefs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // [!!!] SharedPreferences 초기화 [!!!]
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        adapter = new BookmarkAdapter();
        recyclerView.setAdapter(adapter);

        bookmarkViewModel = new ViewModelProvider(this).get(BookmarkViewModel.class);

        bookmarkViewModel.getAllBookmarks().observe(this, bookmarks -> {
            adapter.setBookmarks(bookmarks);
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

    // --- 드래그 앤 드롭 ---
    private void setupItemTouchHelper() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) { // 스와이프는 0 (사용 안 함)

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                List<Bookmark> list = adapter.getList();
                Collections.swap(list, fromPosition, toPosition);
                adapter.notifyItemMoved(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // 드래그가 끝나면 순서 인덱스를 업데이트
                List<Bookmark> updatedList = adapter.getList();
                for (int i = 0; i < updatedList.size(); i++) {
                    updatedList.get(i).setOrderIndex(i);
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

            // [수정됨] URL 멈춤 방지를 위해 https 추가
            url = ensureHttps(url);

            if (bookmark == null) {
                // 추가 (orderIndex는 ViewModel에서 자동 설정)
                Bookmark newBookmark = new Bookmark(title, url, 0);
                bookmarkViewModel.insert(newBookmark);
                Toast.makeText(this, "추가되었습니다.", Toast.LENGTH_SHORT).show();
            } else {
                // 수정
                bookmark.setTitle(title);
                bookmark.setUrl(url); // 수정된 URL 저장
                bookmarkViewModel.update(bookmark);
                Toast.makeText(this, "수정되었습니다.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("취소", null);
        builder.show();
    }

    // --- 검색 메뉴 + [!!!] 설정 메뉴 핸들러 추가 [!!!] ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setQueryHint("제목 또는 URL 검색");

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // 텍스트가 변경될 때마다 필터링된 리스트 관찰
                bookmarkViewModel.getFilteredBookmarks(newText).observe(MainActivity.this, bookmarks -> {
                    adapter.setBookmarks(bookmarks);
                });
                return true;
            }
        });

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) { return true; }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                // 검색창이 닫힐 때 전체 리스트 관찰
                bookmarkViewModel.getAllBookmarks().observe(MainActivity.this, bookmarks -> {
                    adapter.setBookmarks(bookmarks);
                });
                return true;
            }
        });
        return true;
    }

    // [!!!] '설정' 메뉴 항목 클릭 시 호출 [!!!]
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            showSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // [!!!] 설정 다이얼로그 표시 메서드 [!!!]
    private void showSettingsDialog() {
        String[] items = {"공유하기 창 (선택 가능)", "브라우저 선택창(기본 앱 사용)"};

        // 현재 설정된 값(기본값: SHARE)을 읽어옵니다.
        String currentAction = prefs.getString(PREF_CLICK_ACTION, ACTION_SHARE);
        int checkedItem = currentAction.equals(ACTION_CHOOSER) ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle("클릭 시 동작 설정")
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    // 사용자가 선택한 항목을 SharedPreferences에 저장
                    SharedPreferences.Editor editor = prefs.edit();
                    if (which == 1) { // 1번: 브라우저 선택창
                        editor.putString(PREF_CLICK_ACTION, ACTION_CHOOSER);
                        Toast.makeText(this, "브라우저 선택창으로 설정 (기기에서 작동하지 않을 수 있음)", Toast.LENGTH_LONG).show();
                    } else { // 0번: 공유하기
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
                        String latestTag = response.getString("tag_name"); // 예: "v1.1.0"
                        String releasePageUrl = response.getString("html_url");
                        String latestVersion = latestTag.replace("v", "").trim();
                        String currentVersion = getCurrentVersionName();

                        Log.d(TAG, "Current Version: " + currentVersion + ", Latest Version: " + latestVersion);

                        // 버전 이름이 다르면 업데이트 다이얼로그 표시
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

