package com.example.planprep;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<NotificationModel> list;

    public NotificationAdapter(List<NotificationModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationModel model = list.get(position);

        holder.tvTitle.setText(model.getTitle());
        holder.tvMessage.setText(model.getMessage());

        // Format Date
        if (model.getTimestamp() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
            holder.tvDate.setText(sdf.format(model.getTimestamp().toDate()));
        }

        // Show/Hide "Unread" Red Dot
        if (model.isRead()) {
            holder.unreadDot.setVisibility(View.GONE);
            holder.tvTitle.setAlpha(0.5f); // Fade out read items slightly
        } else {
            holder.unreadDot.setVisibility(View.VISIBLE);
            holder.tvTitle.setAlpha(1.0f);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    // Method to remove item from list (for Swipe to Delete)
    public void removeItem(int position) {
        list.remove(position);
        notifyItemRemoved(position);
    }

    // Method to update item status (for Swipe to Read)
    public void markItemRead(int position) {
        list.get(position).setRead(true);
        notifyItemChanged(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMessage, tvDate;
        View unreadDot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvDate = itemView.findViewById(R.id.tvDate);
            unreadDot = itemView.findViewById(R.id.viewUnreadDot);
        }
    }
}