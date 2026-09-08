package com.example.seriestracker.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;

public class SectionHeaderAdapter extends RecyclerView.Adapter<SectionHeaderAdapter.HeaderViewHolder> {

    private final String title;
    private boolean visible;

    public SectionHeaderAdapter(String title) {
        this.title = title;
    }

    public void setVisible(boolean visible) {
        if (this.visible == visible) {
            return;
        }
        this.visible = visible;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HeaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_section_header, parent, false);
        return new HeaderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HeaderViewHolder holder, int position) {
        holder.titleText.setText(title);
    }

    @Override
    public int getItemCount() {
        return visible ? 1 : 0;
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.sectionTitleText);
        }
    }
}
