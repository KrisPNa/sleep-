package com.example.seriestracker.data.entities;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

public class CollectionWithSeries {
    @Embedded
    private Collection collection;

    @Relation(
            parentColumn = "id",  // id из Collection
            entityColumn = "id",  // id из Series
            associateBy = @androidx.room.Junction(
                    value = SeriesCollectionCrossRef.class,
                    parentColumn = "collectionId",  // должно совпадать с getCollectionId()
                    entityColumn = "seriesId"       // должно совпадать с getSeriesId()
            )
    )
    private List<Series> seriesList;

    public Collection getCollection() {
        return collection;
    }

    public List<Series> getSeriesList() {
        return seriesList;
    }

    public void setCollection(Collection collection) {
        this.collection = collection;
    }

    public void setSeriesList(List<Series> seriesList) {
        this.seriesList = seriesList;
    }
}