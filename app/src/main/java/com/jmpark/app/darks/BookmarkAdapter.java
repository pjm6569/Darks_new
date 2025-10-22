package com.jmpark.app.darks; // 사용자의 패키지 이름

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
// [!!!] A/B 로직을 위해 다시 임포트 [!!!]
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;
// [!!!] ------------------------- [!!!]
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder> {

    private List<Bookmark> bookmarks = new ArrayList<>();
    private OnItemLongClickListener longClickListener;
    private ItemTouchHelper itemTouchHelper;

    @NonNull
    @Override
    public BookmarkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_bookmark, parent, false);
        return new BookmarkViewHolder(itemView);
    }

    // 헬퍼 메서드 (URL 멈춤 방지)
    private String ensureHttps(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "https://google.com"; // 비어있으면 기본값
        }
        String trimmedUrl = url.trim();
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return "https://" + trimmedUrl;
        }
        return trimmedUrl;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull BookmarkViewHolder holder, int position) {
        Bookmark currentBookmark = bookmarks.get(position);
        holder.textViewTitle.setText(currentBookmark.getTitle());
        holder.textViewUrl.setText(currentBookmark.getUrl()); // (list_item_bookmark.xml에서 숨김)

        // [!!! 여기가 핵심입니다 !!!]
        holder.itemView.setOnClickListener(v -> {
            String url = ensureHttps(currentBookmark.getUrl());
            Context context = holder.itemView.getContext();
            Uri uri = Uri.parse(url); // 두 로직 모두 사용

            // [!!!] 1. SharedPreferences에서 설정값 읽기 [!!!]
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            // 기본값은 안정적인 "SHARE"
            String clickAction = prefs.getString(MainActivity.PREF_CLICK_ACTION, MainActivity.ACTION_SHARE);

            // [!!!] 2. 설정값에 따라 로직 분기 [!!!]
            if (clickAction.equals(MainActivity.ACTION_CHOOSER)) {

                // --- 옵션 A: 브라우저 선택창 (실험적, 실패했던 코드) ---
                Intent viewIntent = new Intent(Intent.ACTION_VIEW, uri);
                viewIntent.addCategory(Intent.CATEGORY_BROWSABLE); // 브라우저 카테고리

                PackageManager pm = context.getPackageManager();
                // (AndroidManifest.xml에 <queries>가 있다는 가정 하에 실행)
                List<ResolveInfo> activities = pm.queryIntentActivities(viewIntent, 0);

                Log.d("BookmarkAdapter", "(CHOOSER) Found " + activities.size() + " browsers.");

                if (activities.isEmpty()) {
                    Toast.makeText(context, "URL을 열 수 있는 브라우저가 없습니다. (옵션 A)", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<Intent> targetIntents = new ArrayList<>();
                for (ResolveInfo info : activities) {
                    Intent targetIntent = new Intent(Intent.ACTION_VIEW, uri);
                    targetIntent.setPackage(info.activityInfo.packageName);
                    targetIntent.addCategory(Intent.CATEGORY_BROWSABLE);
                    targetIntents.add(targetIntent);
                }

                if (targetIntents.size() == 1) {
                    // 조회된 브라우저가 1개면 바로 실행
                    context.startActivity(targetIntents.get(0));
                } else {
                    // 여러 개일 때만 선택창 생성
                    Intent chooserIntent = Intent.createChooser(targetIntents.remove(0), "다음 브라우저로 열기");
                    Parcelable[] extraIntents = targetIntents.toArray(new Parcelable[0]);
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);
                    context.startActivity(chooserIntent);
                }

            } else {

                // --- 옵션 B: 공유하기 창 (안정적, 기본값) ---
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, currentBookmark.getTitle());
                shareIntent.putExtra(Intent.EXTRA_TEXT, url);

                try {
                    // 사용자가 선택할 수 있도록 항상 createChooser를 사용합니다.
                    context.startActivity(Intent.createChooser(shareIntent, "다음 앱으로 URL 공유"));
                } catch (Exception e) {
                    Toast.makeText(context, "공유할 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 아이템 롱클릭 (컨텍스트 메뉴용)
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(currentBookmark);
            }
            return false; // false로 두어야 ContextMenu가 이어서 호출됨
        });

        // 드래그 핸들 터치 시 드래그 시작
        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(holder);
                }
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return bookmarks.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setBookmarks(List<Bookmark> bookmarks) {
        this.bookmarks = bookmarks;
        notifyDataSetChanged();
    }

    public List<Bookmark> getList() {
        // [수정] NullPointerException 방지를 위해 비어있을 때 빈 리스트 반환
        return bookmarks != null ? bookmarks : new ArrayList<>();
    }

    public void setItemTouchHelper(ItemTouchHelper itemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper;
    }

    class BookmarkViewHolder extends RecyclerView.ViewHolder {
        private TextView textViewTitle;
        private TextView textViewUrl;
        private ImageView dragHandle;

        public BookmarkViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewTitle = itemView.findViewById(R.id.text_view_title);
            textViewUrl = itemView.findViewById(R.id.text_view_url);
            dragHandle = itemView.findViewById(R.id.image_view_drag_handle);
        }
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(Bookmark bookmark);
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }
}

