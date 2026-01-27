package com.example.planprep;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private Context context;
    private List<DocumentSnapshot> list;
    private OnReuseClickListener listener;
    private int expandedPosition = -1; // To track which card is open

    public interface OnReuseClickListener {
        void onReuseClick(DocumentSnapshot doc);
    }

    public HistoryAdapter(Context context, List<DocumentSnapshot> list, OnReuseClickListener listener) {
        this.context = context;
        this.list = list;
        this.listener = listener;
    }

    public void updateList(List<DocumentSnapshot> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_history_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DocumentSnapshot doc = list.get(position);
        Map<String, Object> data = doc.getData();
        if (data == null) return;

        // 1. Format Date Title
        String dateId = (String) data.get("date");
        try {
            SimpleDateFormat sdfId = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date d = sdfId.parse(dateId);
            SimpleDateFormat sdfTitle = new SimpleDateFormat("EEEE • MMM dd, yyyy", Locale.getDefault());
            holder.tvDateTitle.setText(sdfTitle.format(d));
        } catch (Exception e) {
            holder.tvDateTitle.setText(dateId);
        }

        // 2. Build Summary Text (Breakfast, Lunch...)
        StringBuilder summary = new StringBuilder();
        String[] types = {"breakfast", "lunch", "dinner", "supper"};
        for (String t : types) {
            if (data.containsKey(t)) summary.append(capitalize(t)).append(", ");
        }
        if (summary.length() > 2) summary.setLength(summary.length() - 2); // Remove trailing comma
        holder.tvSummary.setText(summary.toString());

        // 3. Handle Expansion & Icon Swap
        boolean isExpanded = (position == expandedPosition);
        holder.detailsContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

        // --- UPDATED: Swap Icon Resource instead of Rotating ---
        if (isExpanded) {
            // Make sure you created this drawable (Vector Asset -> "expand_more")
            holder.ivArrow.setImageResource(R.drawable.down);
        } else {
            // Return to the default right arrow
            holder.ivArrow.setImageResource(R.drawable.right);
        }
        holder.ivArrow.setRotation(0f); // Reset any previous rotation just in case
        // ------------------------------------------------------

        holder.headerContainer.setOnClickListener(v -> {
            int prev = expandedPosition;
            expandedPosition = isExpanded ? -1 : position;
            notifyItemChanged(prev);
            notifyItemChanged(position);
        });

        // 4. Populate Meals if Expanded
        if (isExpanded) {
            holder.mealsContainer.removeAllViews();
            addMealRow(holder.mealsContainer, data, "breakfast");
            addMealRow(holder.mealsContainer, data, "lunch");
            addMealRow(holder.mealsContainer, data, "dinner");
            addMealRow(holder.mealsContainer, data, "supper");
        }

        holder.btnReuse.setOnClickListener(v -> listener.onReuseClick(doc));
    }

    private void addMealRow(LinearLayout container, Map<String, Object> data, String type) {
        if (!data.containsKey(type)) return;

        View row = LayoutInflater.from(context).inflate(R.layout.item_history_meal_row, container, false);

        ImageView img = row.findViewById(R.id.ivMealImg);
        TextView name = row.findViewById(R.id.tvMealName);
        TextView time = row.findViewById(R.id.tvMealTime);
        TextView typeTitle = row.findViewById(R.id.tvMealType);

        String mealName = (String) data.get(type);
        String mealTime = (String) data.get(type + "Time");
        String mealImg = (String) data.get(type + "Image");

        typeTitle.setText(capitalize(type));
        typeTitle.setTextColor(getColorForType(type)); // Optional color coding
        name.setText(mealName);
        time.setText(mealTime);

        if (mealImg != null && !mealImg.isEmpty()) {
            Glide.with(context).load(mealImg).into(img);
        }

        container.addView(row);
    }

    private int getColorForType(String type) {
        switch (type) {
            case "breakfast": return Color.parseColor("#FF9800"); // Orange
            case "lunch": return Color.parseColor("#F44336"); // Red
            case "dinner": return Color.parseColor("#3F51B5"); // Blue
            default: return Color.parseColor("#9C27B0"); // Purple
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateTitle, tvSummary;
        ImageView ivArrow;
        LinearLayout headerContainer, detailsContainer, mealsContainer;
        MaterialButton btnReuse;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateTitle = itemView.findViewById(R.id.tvDateTitle);
            tvSummary = itemView.findViewById(R.id.tvSummary);
            ivArrow = itemView.findViewById(R.id.ivArrow);
            headerContainer = itemView.findViewById(R.id.headerContainer);
            detailsContainer = itemView.findViewById(R.id.detailsContainer);
            mealsContainer = itemView.findViewById(R.id.mealsContainer);
            btnReuse = itemView.findViewById(R.id.btnReuse);
        }
    }
}