# Redpanda Schema Registry Replicator

This project contains a Single Message Transform (SMT) for use with Apache Kafka Connect.

The transform enables MirrorMaker 2 to successfully replicate messages from `_schemas` on a source Redpanda cluster to a
target cluster, while honouring the message validity checks used by the Redpanda Schema Registry.

## Message Validity

Redpanda Schema Registry extends the key design used by the Apache Schema Registry by adding two additional fields:

- `node` is used to indicate which SR instance published the message
- `seq` is a long value used by SR instances to determine which message to consider as valid; if the value in the seq 
field matches the offset of the message, it is considered valid

## Transform Behaviour

When the [transform](src/main/java/com/redpanda/schemaregistry/replication/Transform.java) processes a message, there are three possibilities:

- The message doesn't contain a seq field in the key, therefore the message is replicated without being changed
- The message does contain a seq field and it matches the offset, therefore the message is valid and should be replicated, 
though the node and seq fields are removed
- The message does contain a seq field but it doesn't match the offset, therefore the message is invalid and shouldn't
be replicated, therefore the transform returns null

## Configuration

The example below shows how the transform can be deployed alongside a MirrorMaker2 source connector:

```json
{
  "connector.class": "org.apache.kafka.connect.mirror.MirrorSourceConnector",
  "name": "mm2-source-connector",
  "topics": "_schemas",
  "replication.factor": "1",
  "source.cluster.alias": "source",
  "replication.policy.class": "org.apache.kafka.connect.mirror.IdentityReplicationPolicy",
  "source.cluster.bootstrap.servers": "seed-fa015309.certnoj7m575jtvbg730.fmc.prd.cloud.redpanda.com:9092",
  "source.cluster.sasl.mechanism": "SCRAM-SHA-256",
  "source.cluster.sasl.jaas.config": "org.apache.kafka.common.security.scram.ScramLoginModule required username='pmw' password='redacted';",
  "source.cluster.security.protocol": "SASL_SSL",
  "target.cluster.bootstrap.servers": "redpanda-a:9092",
  "header.converter": "org.apache.kafka.connect.storage.StringConverter",
  "key.converter": "org.apache.kafka.connect.converters.ByteArrayConverter",
  "value.converter": "org.apache.kafka.connect.converters.ByteArrayConverter",
  "sync.topic.acls.enabled": "false",
  "sync.topic.configs.enabled": "false",
  "transforms": "provenance,schema-replicator",
  "transforms.schema-replicator.type": "com.redpanda.schemaregistry.replication.Transform",
  "transforms.provenance.type": "org.apache.kafka.connect.transforms.InsertHeader",
  "transforms.provenance.header": "source",
  "transforms.provenance.value.literal": "certnoj7m575jtvbg730"
}
```

## Demo

The [demo](demo) directory contains a [docker-compose.yml](demo/docker-compose.yml) file that starts up the following containers:

- Redpanda Broker
- Redpanda Console
- Prometheus
- Grafana
- Apache Connect

Once the containers have started, use the Redpanda console to deploy a new connector and supply the configuration snippet above.

The example reads `_schemas` from a cluster in Redpanda Cloud and modifies the messages when writing them to the local Redpanda
instance. Customise this as needed for your source cluster.

### Auth

In the demo, the target Redpanda cluster doesn't require credentials, but these can be supplied in [docker-compose.yml](demo/docker-compose.yml)
and [password](demo/connect-password/redpanda-password/password) files.

For the upstream source cluster, credentials need to be placed in the [connector json](example/connector.json).

## JAR

Maven can be used to build the project, but for ease of deployment the demo already contains a [pre-built JAR](demo/connect-plugins/RedpandaSchemaRegistryReplication-1.0.jar) containing the transform code.

## Debug

The Connect container also allows Java Remote Debug on port 5005.