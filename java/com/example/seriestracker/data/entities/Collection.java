package com.example.seriestracker.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "collections")
public class Collection {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private String name;
    private long createdAt;
    private boolean isFavorite;
    private String color; // Новое поле для цвета

    // Предопределенные цвета
    public static final String[] AVAILABLE_COLORS = {
            // Основные цвета (старые)
            "#2196F3", // синий (по умолчанию)
            "#FF4081", // розовый
            "#4CAF50", // зеленый
            "#FF9800", // оранжевый
            "#9C27B0", // фиолетовый
            "#795548", // коричневый
            "#607D8B", // сине-серый
            "#E91E63", // малиновый
            "#00BCD4", // голубой
            "#8BC34A", // светло-зеленый
            "#FF5722", // глубокий оранжевый
            "#673AB7",  // глубокий фиолетовый

            // Добавленные цвета
            "#000000", // Черный
            "#DC143C", // Малиновый2
            "#8B0000", // Темно-красный
            "#F08080", // Светлый коралловый
            "#FF69B4", // Ярко-розовый
            "#C71585", // Средний фиолетовый
            "#FF4500", // Оранжево-красный
            "#FFA500", // Оранжевый
            "#FFFF00", // Желтый
            "#BDB76B", // Темный хаки
            "#E6E6FA", // Лавандовый
            "#EE82EE", // Фиолетовый2
            "#FF00FF", // Фуксия
            "#9370DB", // Средний фиолетовый2
            "#8B008B", // Темно-маджента
            "#4B0082", // Индиго
            "#000080", // Темно-синий
            "#0000FF", // Синий2
            "#00BFFF", // Голубой2
            "#008080", // Темный бирюзовый
            "#00CED1", // Темный бирюзовый2
            "#00FFFF", // Бирюзовый
            "#7FFFD4", // Аквамарин
            "#66CDAA", // Средний аквамарин
            "#008B8B", // Темный циан
            "#8FBC8F", // Темный серо-зеленый
            "#00FA9A", // Средний весенний зеленый
            "#00FF00", // Зеленый2
            "#228B22", // Лесной зеленый
            "#006400", // Темно-зеленый
            "#ADFF2F", // Желто-зеленый
            "#2F4F4F", // Темный грифельно-серый
            "#708090", // Сланцево-серый
            "#696969"  // Темно-серый
    };

    public Collection() {
        this.createdAt = System.currentTimeMillis();
        this.isFavorite = false;
        this.color = AVAILABLE_COLORS[0]; // Синий по умолчанию
    }

    public Collection(String name) {
        this();
        this.name = name;
    }

    public Collection(String name, String color) {
        this();
        this.name = name;
        this.color = color;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public long getCreatedAt() { return createdAt; }
    public boolean isFavorite() { return isFavorite; }
    public String getColor() { return color; }

    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
    public void setColor(String color) { this.color = color; }
}