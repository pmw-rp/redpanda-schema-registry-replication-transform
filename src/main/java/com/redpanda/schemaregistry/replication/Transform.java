package com.redpanda.schemaregistry.replication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.transforms.Transformation;

import java.util.HashMap;
import java.util.Map;

public class Transform implements Transformation<SourceRecord> {
    public static final String OVERVIEW_DOC = "A transform to filter and process Redpanda SR messages.";
    public static final ConfigDef config = new ConfigDef();

    private long getOffset(SourceRecord record) {
        if (!record.sourceOffset().containsKey("offset")) {
            throw new RuntimeException("Offset is missing");
        }
        Object value = record.sourceOffset().get("offset");
        if (value.getClass() != Long.class) {
            throw new RuntimeException("Offset is expected to be a Long");
        }
        return (Long) record.sourceOffset().get("offset");
    }

    private final ObjectMapper mapper = new ObjectMapper();

    private HashMap<String, Object> deserializeKey(SourceRecord record) {
        try {
            byte [] bytes = (byte[])record.key();
            return mapper.readValue(new String(bytes), HashMap.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SourceRecord apply(SourceRecord record) {
        if (record.value() == null) {
            return record;
        }

        Map<String, Object> key = deserializeKey(record);
        if (!key.containsKey("seq")) {
            return record;
        }

        long seq = (long) key.get("seq");
        long offset = getOffset(record);

        if (seq != offset) {
            return null;
        }

        key.remove("seq");
        key.remove("node");

        try {
            return record.newRecord(record.topic(), record.kafkaPartition(), record.keySchema(),
                    mapper.writeValueAsBytes(key), record.valueSchema(), record.value(), record.timestamp(),
                    record.headers());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ConfigDef config() {
        return config;
    }

    @Override
    public void close() {
        // NOP
    }

    @Override
    public void configure(Map<String, ?> configs) {
        mapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
    }
}
