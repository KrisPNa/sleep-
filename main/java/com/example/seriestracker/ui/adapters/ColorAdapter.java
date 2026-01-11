package com.example.seriestracker.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;

import java.util.Arrays;
import java.util.List;

public class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ColorViewHolder> {

    public interface OnColorClickListener {
        void onColorClick(String color, String colorName);
    }

    private final List<String> colors;
    private final List<String> colorNames;
    private final OnColorClickListener listener;
    private String selectedColor;

    public ColorAdapter(OnColorClickListener listener) {
        this.listener = listener;
        this.colors = Arrays.asList(Collection.AVAILABLE_COLORS);
        this.colorNames = Arrays.asList(
                "Синий", "Розовый", "Зеленый", "Оранжевый", "Фиолетовый",
                "Коричневый", "Серый", "Малиновый", "Голубой",
                "Светло-зеленый", "Красный", "Темно-фиолетовый",
                // Добавленные цвета:
                "Черный", "Малиновый2", "Темно-красный", "Светлый коралловый", "Ярко-розовый",
                "Средний фиолетовый", "Оранжево-красный", "Оранжевый", "Желтый", "Темный хаки",
                "Лавандовый", "Фиолетовый2", "Фуксия", "Средний фиолетовый2", "Темно-маджента",
                "Индиго", "Темно-синий", "Синий2", "Голубой2", "Темный бирюзовый",
                "Темный бирюзовый2", "Бирюзовый", "Аквамарин", "Средний аквамарин", "Темный циан",
                "Темный серо-зеленый", "Средний весенний зеленый", "Зеленый2", "Лесной зеленый",
                "Темно-зеленый", "Желто-зеленый", "Темный грифельно-серый", "Сланцево-серый",
                "Темно-серый"
        );
        this.selectedColor = colors.get(0);
    }

    public void setSelectedColor(String color) {
        this.selectedColor = color;
        notifyDataSetChanged();
    }

    public String getSelectedColor() {
        return selectedColor;
    }

    public String getSelectedColorName() {
        int index = colors.indexOf(selectedColor);
        return index >= 0 ? colorNames.get(index) : "Синий";
    }

    @NonNull
    @Override
    public ColorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_color, parent, false);
        return new ColorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ColorViewHolder holder, int position) {
        String color = colors.get(position);
        String colorName = colorNames.get(position);
        boolean isSelected = color.equals(selectedColor);
        holder.bind(color, colorName, isSelected, listener);
    }

    @Override
    public int getItemCount() {
        return colors.size();
    }

    static class ColorViewHolder extends RecyclerView.ViewHolder {
        private final View innerCircle;
        private final ImageView checkIcon;

        public ColorViewHolder(@NonNull View itemView) {
            super(itemView);
            innerCircle = itemView.findViewById(R.id.innerCircle);
            checkIcon = itemView.findViewById(R.id.checkIcon);
        }

        public void bind(String color, String colorName, boolean isSelected, OnColorClickListener listener) {
            innerCircle.setBackgroundColor(Color.parseColor(color));

            if (isSelected) {
                checkIcon.setVisibility(View.VISIBLE);
                innerCircle.getLayoutParams().width = 36;
                innerCircle.getLayoutParams().height = 36;
            } else {
                checkIcon.setVisibility(View.GONE);
                innerCircle.getLayoutParams().width = 32;
                innerCircle.getLayoutParams().height = 32;
            }
            innerCircle.requestLayout();

            itemView.setOnClickListener(v -> {
                if (!isSelected && listener != null) {
                    listener.onColorClick(color, colorName);
                }
            });
        }
    }
}