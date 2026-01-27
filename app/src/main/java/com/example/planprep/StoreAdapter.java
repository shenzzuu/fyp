package com.example.planprep;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.StoreViewHolder> {

    private List<Store> storeList;

    public StoreAdapter(List<Store> storeList) {
        this.storeList = storeList;
    }

    @NonNull
    @Override
    public StoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Ensure item_store.xml contains tvStoreName, tvStoreAddress, and tvStoreDistance
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_store, parent, false);
        return new StoreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StoreViewHolder holder, int position) {
        Store store = storeList.get(position);

        holder.tvName.setText(store.getName());
        holder.tvAddress.setText(store.getAddress());
        holder.tvDistance.setText(store.getDistance());

        // Click listener to navigate to Google Maps
        holder.itemView.setOnClickListener(v -> {
            Uri mapUri;
            if (store.getUrl() != null && !store.getUrl().isEmpty()) {
                // If Apify provided a direct URL, use it
                mapUri = Uri.parse(store.getUrl());
            } else {
                // Fallback: Create a search query using name and address
                String query = Uri.encode(store.getName() + " " + store.getAddress());
                mapUri = Uri.parse("geo:0,0?q=" + query);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW, mapUri);
            intent.setPackage("com.google.android.apps.maps"); // Forces Google Maps if installed

            // Check if there is an app to handle the intent to prevent crashes
            if (intent.resolveActivity(v.getContext().getPackageManager()) != null) {
                v.getContext().startActivity(intent);
            } else {
                // If Google Maps isn't installed, open in browser
                v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, mapUri));
            }
        });
    }

    @Override
    public int getItemCount() {
        return storeList != null ? storeList.size() : 0;
    }

    public static class StoreViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress, tvDistance;

        public StoreViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvStoreName);
            tvAddress = itemView.findViewById(R.id.tvStoreAddress);
            tvDistance = itemView.findViewById(R.id.tvStoreDistance);
        }
    }
}