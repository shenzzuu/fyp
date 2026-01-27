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

import java.util.List;

public class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.ViewHolder> {

    private Context context;
    private List<FavoriteItem> list;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onDeleteClick(int position, String docId);
        void onItemClick(FavoriteItem item);
        void onAddToPlanClick(FavoriteItem item); // NEW METHOD
    }

    public FavoritesAdapter(Context context, List<FavoriteItem> list, OnItemClickListener listener) {
        this.context = context;
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_favorite_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FavoriteItem item = list.get(position);

        holder.tvName.setText(item.getName());
        holder.tvCategory.setText(item.getCategory());

        Glide.with(context)
                .load(item.getImage())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .fallback(android.R.drawable.ic_menu_gallery)
                .into(holder.ivRecipe);

        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(position, item.getId()));

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));

        // NEW: Handle Add Button Click
        holder.btnAddToPlan.setOnClickListener(v -> listener.onAddToPlanClick(item));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivRecipe, btnDelete, btnAddToPlan; // Added btnAddToPlan
        TextView tvName, tvCategory;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivRecipe = itemView.findViewById(R.id.ivRecipe);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnAddToPlan = itemView.findViewById(R.id.btnAddToPlan); // Find View
            tvName = itemView.findViewById(R.id.tvName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
        }
    }
}