package com.example.planprep;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.FoodViewHolder> {

    private List<FoodItem> fullList;
    private List<FoodItem> displayedList;
    private Context context;
    private OnFoodAddListener listener;

    public interface OnFoodAddListener {
        void onFoodAdd(FoodItem foodItem);
    }

    public FoodAdapter(Context context, List<FoodItem> data, OnFoodAddListener listener) {
        this.context = context;
        this.fullList = new ArrayList<>(data);
        this.displayedList = new ArrayList<>(data);
        this.listener = listener;
    }

    // Standard Search Filter
    public void filter(String text) {
        displayedList.clear();
        if (text == null || text.trim().isEmpty()) {
            displayedList.addAll(fullList);
        } else {
            String query = text.toLowerCase().trim();
            for (FoodItem item : fullList) {
                if (item.getName().toLowerCase().contains(query)) {
                    displayedList.add(item);
                }
            }
        }
        notifyDataSetChanged(); // Important: This triggers Activity's getItemCount() check
    }

    // Tab Filter (All vs Favorites)
    public void filterByTab(boolean showOnlyFavs, List<String> favoriteNames) {
        displayedList.clear();
        if (showOnlyFavs) {
            if (favoriteNames != null && !favoriteNames.isEmpty()) {
                for (FoodItem item : fullList) {
                    if (favoriteNames.contains(item.getName())) {
                        displayedList.add(item);
                    }
                }
            }
            // If showOnlyFavs is true but favoriteNames is empty,
            // displayedList remains empty, triggering the No Result state in Activity.
        } else {
            displayedList.addAll(fullList);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_food, parent, false);
        return new FoodViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FoodViewHolder holder, int position) {
        FoodItem item = displayedList.get(position);
        holder.tvName.setText(item.getName());
        holder.tvOrigin.setText(item.getOrigin());

        Glide.with(context)
                .load(item.getImageUrl())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .centerCrop()
                .into(holder.imgIcon);

        holder.btnAdd.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFoodAdd(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return displayedList.size();
    }

    public static class FoodViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvOrigin;
        ImageView imgIcon, btnAdd;

        public FoodViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvFoodName);
            tvOrigin = itemView.findViewById(R.id.tvFoodOrigin);
            imgIcon = itemView.findViewById(R.id.imgFoodIcon);
            btnAdd = itemView.findViewById(R.id.btnAdd);
        }
    }
}