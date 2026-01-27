package com.example.planprep;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface; // Import for text style
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class NotificationActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
    private List<NotificationModel> notificationList;
    private FirebaseFirestore db;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        // --- 1. SETUP BACK BUTTON ---
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                getOnBackPressedDispatcher().onBackPressed();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            });
        }

        // --- 2. SETUP VIEWS ---
        recyclerView = findViewById(R.id.rvNotifications);
        tvEmpty = findViewById(R.id.tvEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationList = new ArrayList<>();
        adapter = new NotificationAdapter(notificationList);
        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();

        loadNotifications();
        setupSwipeGestures();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void loadNotifications() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("notifications")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error loading: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    notificationList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            NotificationModel model = doc.toObject(NotificationModel.class);
                            if (model != null) {
                                model.setDocId(doc.getId());
                                notificationList.add(model);
                            }
                        }
                    }

                    adapter.notifyDataSetChanged();

                    if (notificationList.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void setupSwipeGestures() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();

                if (position == RecyclerView.NO_POSITION || position >= notificationList.size()) {
                    adapter.notifyDataSetChanged();
                    return;
                }

                NotificationModel item = notificationList.get(position);

                if (direction == ItemTouchHelper.LEFT) {
                    deleteNotification(item.getDocId());
                } else {
                    markAsRead(item.getDocId(), position);
                }
            }

            // --- CHANGED: DRAW TEXT AND BACKGROUND ---
            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {

                View itemView = viewHolder.itemView;
                ColorDrawable background = new ColorDrawable();

                // Initialize Paint for Text
                Paint textPaint = new Paint();
                textPaint.setColor(Color.WHITE);
                textPaint.setTextSize(40); // Size in pixels
                textPaint.setAntiAlias(true);
                textPaint.setTypeface(Typeface.DEFAULT_BOLD);

                // Calculate Vertical Center for Text
                float textY = itemView.getTop() + (itemView.getHeight() / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f);

                if (dX > 0) {
                    // --- SWIPING RIGHT (Read) ---
                    background.setColor(Color.parseColor("#4CAF50")); // Green
                    background.setBounds(itemView.getLeft(), itemView.getTop(), (int) dX, itemView.getBottom());
                    background.draw(c);

                    // Draw "Mark Read" Text on the Left
                    // Only draw if swipe is wide enough to show text
                    if (dX > 60) {
                        c.drawText("Mark Read", itemView.getLeft() + 60, textY, textPaint);
                    }

                } else if (dX < 0) {
                    // --- SWIPING LEFT (Delete) ---
                    background.setColor(Color.parseColor("#F44336")); // Red
                    background.setBounds(itemView.getRight() + (int) dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());
                    background.draw(c);

                    // Draw "Delete" Text on the Right
                    String deleteText = "Delete";
                    float textWidth = textPaint.measureText(deleteText);

                    // Only draw if swipe is wide enough
                    if (Math.abs(dX) > 60) {
                        c.drawText(deleteText, itemView.getRight() - textWidth - 60, textY, textPaint);
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

        }).attachToRecyclerView(recyclerView);
    }

    private void deleteNotification(String docId) {
        db.collection("notifications").document(docId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Snackbar.make(recyclerView, "Notification Deleted", Snackbar.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show();
                    adapter.notifyDataSetChanged();
                });
    }

    private void markAsRead(String docId, int position) {
        db.collection("notifications").document(docId)
                .update("read", true)
                .addOnSuccessListener(aVoid -> {
                    if (position < notificationList.size()) {
                        notificationList.get(position).setRead(true);
                        adapter.notifyItemChanged(position);
                    }
                    Snackbar.make(recyclerView, "Marked as Read", Snackbar.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    adapter.notifyItemChanged(position);
                });
    }
}