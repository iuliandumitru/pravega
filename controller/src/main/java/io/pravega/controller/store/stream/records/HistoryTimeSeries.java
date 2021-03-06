/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.controller.store.stream.records;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.pravega.common.ObjectBuilder;
import io.pravega.common.io.serialization.RevisionDataInput;
import io.pravega.common.io.serialization.RevisionDataOutput;
import io.pravega.common.io.serialization.VersionedSerializer;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Data
/**
 * This class stores chunks of the history time series.
 * Each chunk is of fixed size and contains list of epochs in form of HistoryTimeSeriesRecord.
 */
public class HistoryTimeSeries {
    public static final HistoryTimeSeriesSerializer SERIALIZER = new HistoryTimeSeriesSerializer();
    public static final int HISTORY_CHUNK_SIZE = 1000;

    private final List<HistoryTimeSeriesRecord> historyRecords;

    @Builder
    HistoryTimeSeries(List<HistoryTimeSeriesRecord> historyRecords) {
        this.historyRecords = ImmutableList.copyOf(historyRecords);
    }

    @SneakyThrows(IOException.class)
    public byte[] toBytes() {
        return SERIALIZER.serialize(this).getCopy();
    }

    @SneakyThrows(IOException.class)
    public static HistoryTimeSeries fromBytes(final byte[] record) {
        InputStream inputStream = new ByteArrayInputStream(record, 0, record.length);
        return SERIALIZER.deserialize(inputStream);
    }

    public static class HistoryTimeSeriesBuilder implements ObjectBuilder<HistoryTimeSeries> {

    }
    
    public HistoryTimeSeriesRecord getLatestRecord() {
        return historyRecords.get(historyRecords.size() - 1);
    }

    public static HistoryTimeSeries addHistoryRecord(HistoryTimeSeries series, HistoryTimeSeriesRecord record) {
        List<HistoryTimeSeriesRecord> list = Lists.newArrayList(series.historyRecords);

        // add only if epoch is immediate epoch following the highest epoch in the series
        if (list.get(list.size() - 1).getEpoch() == record.getEpoch() - 1) {
            list.add(record);
        } else if (list.get(list.size() - 1).getEpoch() != record.getEpoch()) {
            throw new IllegalArgumentException("new epoch record is not continuous");
        } 

        return new HistoryTimeSeries(list);
    }
    
    private static class HistoryTimeSeriesSerializer extends
            VersionedSerializer.WithBuilder<HistoryTimeSeries, HistoryTimeSeries.HistoryTimeSeriesBuilder> {
        @Override
        protected byte getWriteVersion() {
            return 0;
        }

        @Override
        protected void declareVersions() {
            version(0).revision(0, this::write00, this::read00);
        }

        private void read00(RevisionDataInput revisionDataInput, HistoryTimeSeries.HistoryTimeSeriesBuilder builder) throws IOException {
            builder.historyRecords(revisionDataInput.readCollection(HistoryTimeSeriesRecord.SERIALIZER::deserialize,
                    ArrayList::new));
        }

        private void write00(HistoryTimeSeries history, RevisionDataOutput revisionDataOutput) throws IOException {
            revisionDataOutput.writeCollection(history.getHistoryRecords(), HistoryTimeSeriesRecord.SERIALIZER::serialize);
        }

        @Override
        protected HistoryTimeSeries.HistoryTimeSeriesBuilder newBuilder() {
            return HistoryTimeSeries.builder();
        }
    }
}
