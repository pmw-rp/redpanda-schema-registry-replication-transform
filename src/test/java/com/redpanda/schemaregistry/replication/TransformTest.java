package com.redpanda.schemaregistry.replication;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TransformTest {

    private static Transform transform;

    @BeforeAll
    public static void setup() {
        transform = new Transform();
        transform.configure(Collections.emptyMap());
    }

    private SourceRecord buildSourceRecord(long offset, long seq, boolean includeSeqAndNode) {
        String key = includeSeqAndNode ? "{\n" +
                "    \"keytype\": \"SCHEMA\",\n" +
                "    \"subject\": \"nasdaq_historical_proto-value\",\n" +
                "    \"version\": 1,\n" +
                "    \"magic\": 1,\n" +
                "    \"seq\": " + seq + ",\n" +
                "    \"node\": 4\n" +
                "}" : "{\n" +
                "    \"keytype\": \"SCHEMA\",\n" +
                "    \"subject\": \"nasdaq_historical_proto-value\",\n" +
                "    \"version\": 1,\n" +
                "    \"magic\": 1\n" +
                "}";

        String value = "{\n" +
                "    \"subject\": \"nasdaq_historical_proto-value\",\n" +
                "    \"version\": 1,\n" +
                "    \"id\": 1,\n" +
                "    \"schemaType\": \"PROTOBUF\",\n" +
                "    \"schema\": \"syntax = \\\"proto3\\\";\\n\\npackage nasdaq.historical.v1;\\n\\nmessage NasdaqHistorical {\\n  string date = 1;\\n  string last = 2;\\n  float volume = 3;\\n  string open = 4;\\n  string high = 5;\\n  string low = 6;\\n}\\n\\n\",\n" +
                "    \"deleted\": false\n" +
                "}";
        Map<String, ?> partitionMap = Map.of("partition", 0);
        Map<String, ?> offsetMap = Map.of("offset", offset);
        return new SourceRecord(partitionMap, offsetMap, "foo", 0, Schema.BYTES_SCHEMA, key.getBytes(), Schema.BYTES_SCHEMA, value.getBytes());
    }

    @Test
    public void testNoSeq() {
        // Since there is no seq in the key, the transform returns the same record object without changing it
        SourceRecord record = buildSourceRecord(0, -1, false);
        SourceRecord result = transform.apply(record);
        assertEquals(record, result);
        assertSame(record, result);
    }

    @Test
    public void testMatchingSeq() {
        // Since there is seq in the key and it matches the offset, the transform returns the same record (while removing the seq and node fields)
        SourceRecord record = buildSourceRecord(1, 1, true);
        String keyBefore = new String((byte[])record.key());
        assertTrue(keyBefore.contains("seq"));

        SourceRecord result = transform.apply(record);
        assertNotEquals(record, result);

        String keyAfter = new String((byte[])result.key());
        assertFalse(keyAfter.contains("seq"));
    }

    @Test
    public void testNonMatchingSeq() {
        // Since there is seq in the key and it doesn't match the offset, the transform returns null, which causes the record to be dropped
        SourceRecord record = buildSourceRecord(1, 0, true);
        SourceRecord result = transform.apply(record);
        assertNull(result);
    }
}