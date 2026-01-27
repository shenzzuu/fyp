package com.example.planprep;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;

public class MealAdapter extends RecyclerView.Adapter<MealAdapter.MealViewHolder> {

    private List<Meal> meals;
    private final List<Meal> fullList;
    private OnMealActionListener listener;
    private OnFavoriteClickListener favListener;

    // Using a Set for faster lookup of favorite status
    private Set<String> favoriteMealNames = new HashSet<>();

    public interface OnMealActionListener {
        void onAddToToday(Meal meal);
    }

    public interface OnFavoriteClickListener {
        void onFavClick(Meal meal, boolean isCurrentlyFav);
    }

    public void setOnMealActionListener(OnMealActionListener listener) {
        this.listener = listener;
    }

    public void setOnFavoriteClickListener(OnFavoriteClickListener favListener) {
        this.favListener = favListener;
    }

    // NEW: Method to update the favorite list from Activity
    public void setFavoriteMeals(List<String> favNames) {
        this.favoriteMealNames = new HashSet<>(favNames);
        notifyDataSetChanged();
    }

    public MealAdapter(List<Meal> meals) {
        this.meals = new ArrayList<>(meals);
        this.fullList = new ArrayList<>(meals);
    }

    @NonNull
    @Override
    public MealViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_meal, parent, false);
        return new MealViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MealViewHolder holder, int position) {
        Meal meal = meals.get(position);

        holder.tvName.setText(meal.getName());
        holder.tvDescription.setText(meal.getDescription());
        holder.tvCategory.setText(meal.getCategory());

        // Check if this specific meal is in the favorites set
        boolean isFavorited = favoriteMealNames.contains(meal.getName());

        // Update heart icon based on state
        if (isFavorited) {
            holder.ivFavorite.setImageResource(R.drawable.fav_fill); // Change to your filled heart drawable
        } else {
            holder.ivFavorite.setImageResource(R.drawable.fav_outline); // Change to your outline heart drawable
        }

        // LOAD IMAGE (COIL)
        ImageLoader imageLoader = Coil.imageLoader(holder.itemView.getContext());
        ImageRequest request = new ImageRequest.Builder(holder.itemView.getContext())
                .data(meal.getImageUrl())
                .placeholder(R.drawable.meal)
                .error(R.drawable.meal)
                .crossfade(true)
                .target(holder.imgMeal)
                .build();
        imageLoader.enqueue(request);

        holder.btnAdd.setOnClickListener(v -> {
            if (listener != null) listener.onAddToToday(meal);
        });

        holder.ivFavorite.setOnClickListener(v -> {
            if (favListener != null) {
                // Pass the current state so the Activity knows whether to ADD or REMOVE
                favListener.onFavClick(meal, isFavorited);
            }
        });
    }

    @Override
    public int getItemCount() {
        return meals.size();
    }

    public void filter(String query) {
        List<Meal> filtered = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            filtered.addAll(fullList);
        } else {
            String lower = query.toLowerCase().trim();
            for (Meal m : fullList) {
                if (m.getName().toLowerCase().contains(lower) ||
                        m.getCategory().toLowerCase().contains(lower)) {
                    filtered.add(m);
                }
            }
        }
        meals = filtered;
        notifyDataSetChanged();
    }

    public static class MealViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDescription, tvCategory;
        ImageView imgMeal, ivFavorite;
        View btnAdd;

        public MealViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvMealName);
            tvDescription = itemView.findViewById(R.id.tvMealDescription);
            tvCategory = itemView.findViewById(R.id.tvMealCategory);
            imgMeal = itemView.findViewById(R.id.imgMeal);
            btnAdd = itemView.findViewById(R.id.btnAdd);
            ivFavorite = itemView.findViewById(R.id.ivFavorite);
        }
    }
}