package com.example.planprep;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class MealStatusAdapter extends RecyclerView.Adapter<MealStatusAdapter.ViewHolder> {

    private Context context;
    private List<MealStatusItem> list;
    private SimpleDateFormat sdfDay = new SimpleDateFormat("dd", Locale.getDefault());
    private SimpleDateFormat sdfMonth = new SimpleDateFormat("MMM", Locale.getDefault());

    public MealStatusAdapter(Context context, List<MealStatusItem> list) {
        this.context = context;
        this.list = list;
    }

    public void updateList(List<MealStatusItem> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_meal_status, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MealStatusItem item = list.get(position);

        // 1. Set Meal Info
        holder.tvMealName.setText(item.mealName);
        holder.tvMealType.setText(item.mealType.toUpperCase());

        // 2. Set Date Info (Parse object)
        if (item.dateObj != null) {
            holder.tvDayNumber.setText(sdfDay.format(item.dateObj));
            holder.tvMonth.setText(sdfMonth.format(item.dateObj));
        } else {
            // Fallback if parsing failed
            holder.tvDayNumber.setText("--");
            holder.tvMonth.setText("---");
        }

        // 3. Status Badge Styling
        if (item.isEaten) {
            holder.tvStatus.setText("Eaten");
            // Green Color
            holder.cardStatus.setCardBackgroundColor(Color.parseColor("#4CAF50"));
        } else {
            holder.tvStatus.setText("Missed");
            // Red Color (Like screenshot)
            holder.cardStatus.setCardBackgroundColor(Color.parseColor("#EF5350"));
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDayNumber, tvMonth, tvMealType, tvMealName, tvStatus;
        CardView cardStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayNumber = itemView.findViewById(R.id.tvDayNumber);
            tvMonth = itemView.findViewById(R.id.tvMonth);
            tvMealType = itemView.findViewById(R.id.tvMealType);
            tvMealName = itemView.findViewById(R.id.tvMealName);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            cardStatus = itemView.findViewById(R.id.cardStatus);
        }
    }
}