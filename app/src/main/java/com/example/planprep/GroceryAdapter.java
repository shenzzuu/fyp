package com.example.planprep;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class GroceryAdapter extends RecyclerView.Adapter<GroceryAdapter.ViewHolder> {

    private final List<Ingredient> fullList;
    private final List<Ingredient> displayedList;
    private OnItemClickListener listener;

    private String currentCategory = "Breakfast";
    private String currentQuery = "";

    public interface OnItemClickListener {
        void onItemClick(Ingredient ingredient);
        void onEditClick(Ingredient ingredient);
        void onDeleteClick(Ingredient ingredient);
    }

    public GroceryAdapter(List<Ingredient> data) {
        this.fullList = new ArrayList<>(data);
        this.displayedList = new ArrayList<>(data);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public boolean checkAllDisplayed(boolean checked) {
        for (Ingredient item : displayedList) {
            item.setChecked(checked);
            if (listener != null) {
                listener.onItemClick(item);
            }
        }
        notifyDataSetChanged();
        return checked;
    }

    public List<Ingredient> getDisplayedList() {
        return displayedList;
    }

    public void filterByCategory(String category) {
        this.currentCategory = category;
        applyFilters();
    }

    public void filterBySearch(String query) {
        this.currentQuery = query != null ? query : "";
        applyFilters();
    }

    private void applyFilters() {
        displayedList.clear();
        for (Ingredient item : fullList) {
            boolean matchesCategory = item.getCategory().equalsIgnoreCase(currentCategory);
            boolean matchesSearch = item.getName().toLowerCase()
                    .contains(currentQuery.toLowerCase().trim());

            if (matchesCategory && matchesSearch) {
                displayedList.add(item);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ingredient, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Ingredient item = displayedList.get(position);
        holder.tvName.setText(item.getName());

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(item.isChecked());

        updateStrikeThrough(holder.tvName, item.isChecked());

        // --- CUSTOM ITEM LOGIC ---
        if (item.isCustom()) {
            // Delete is always visible for custom items
            holder.ivDelete.setVisibility(View.VISIBLE);

            // Edit is ONLY visible if the item is NOT checked
            if (item.isChecked()) {
                holder.ivEdit.setVisibility(View.GONE);
            } else {
                holder.ivEdit.setVisibility(View.VISIBLE);
            }

            holder.ivEdit.setOnClickListener(v -> {
                if (listener != null) listener.onEditClick(item);
            });

            holder.ivDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClick(item);
            });
        } else {
            // Standard meal ingredients never show Edit/Delete
            holder.ivEdit.setVisibility(View.GONE);
            holder.ivDelete.setVisibility(View.GONE);
        }

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setChecked(isChecked);
            updateStrikeThrough(holder.tvName, isChecked);

            // Notify change so the Edit icon visibility updates immediately
            notifyItemChanged(holder.getAdapterPosition());

            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    private void updateStrikeThrough(TextView tv, boolean isChecked) {
        Context context = tv.getContext();
        if (isChecked) {
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            // OLD: Color.parseColor("#BDBDBD")
            // NEW: Grey text for checked items
            tv.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        } else {
            tv.setPaintFlags(tv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            // OLD: Color.parseColor("#1B5E20") -> This was forcing Dark Green
            // NEW: White in Dark Mode, Black in Light Mode
            tv.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        }
    }

    @Override
    public int getItemCount() {
        return displayedList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        CheckBox checkBox;
        ImageView ivEdit, ivDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvIngredientName);
            checkBox = itemView.findViewById(R.id.cbIngredient);
            ivEdit = itemView.findViewById(R.id.ivEdit);
            ivDelete = itemView.findViewById(R.id.ivDelete);
        }
    }
}