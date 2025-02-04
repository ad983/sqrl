/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import com.datasqrl.graphql.server.BuildGraphQLEngine;
import com.datasqrl.graphql.kafka.KafkaSinkEmitter;
import com.datasqrl.graphql.server.Model.ArgumentLookupCoords;
import com.datasqrl.graphql.server.Model.ArgumentSet;
import com.datasqrl.graphql.server.Model.JdbcQuery;
import com.datasqrl.graphql.server.Model.KafkaMutationCoords;
import com.datasqrl.graphql.server.Model.RootGraphqlModel;
import com.datasqrl.graphql.server.Model.StringSchema;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import lombok.SneakyThrows;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.streams.integration.utils.EmbeddedKafkaCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@ExtendWith(VertxExtension.class)
@Testcontainers
class WriteTest {

  // will be started before and stopped after each test method
  @Container
  private PostgreSQLContainer testDatabase =
      new PostgreSQLContainer(DockerImageName.parse("postgres:14.2"))
          .withDatabaseName("foo")
          .withUsername("foo")
          .withPassword("secret")
          .withDatabaseName("datasqrl");

  EmbeddedKafkaCluster CLUSTER = new EmbeddedKafkaCluster(1);

  //Todo Add Kafka

  private RootGraphqlModel root = RootGraphqlModel.builder()
      .schema(StringSchema.builder().schema(""
          + "type Query { "
          + "  customer: Customer "
          + "} "
          + "type Mutation {"
          + "  addCustomer(event: CreateCustomerEvent): Customer"
          + "} "
          + "input CreateCustomerEvent {"
          + "  customerid: Int"
          + "} "
          + "type Customer {"
          + "  customerid: Int "
          + "}").build())
      .coord(ArgumentLookupCoords.builder()
          .parentType("Query")
          .fieldName("customer")
          .match(ArgumentSet.builder()
              .query(JdbcQuery.builder()
                  .sql("SELECT customerid FROM Customer")
                  .build())
              .build())
          .build())
      .mutation(KafkaMutationCoords.builder()
          .fieldName("addCustomer")
          .sinkConfig(Map.of("topic", "topic-1"))
          .build())
      .build();
  private PgPool client;
  private KafkaProducer<String, String> kafkaProducer;

  @SneakyThrows
  @BeforeEach
  public void init(Vertx vertx) {
    CLUSTER.start();


    PgConnectOptions options = new PgConnectOptions();
    options.setDatabase(testDatabase.getDatabaseName());
    options.setHost(testDatabase.getHost());
    options.setPort(testDatabase.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
    options.setUser(testDatabase.getUsername());
    options.setPassword(testDatabase.getPassword());

    options.setCachePreparedStatements(true);
    options.setPipeliningLimit(100_000);

    PgPool client = PgPool.pool(vertx, options, new PoolOptions());
    this.kafkaProducer = KafkaProducer.create(vertx, getKafkaProps());
    this.client = client;
  }

  private Properties getKafkaProps() {
    Properties props = new Properties();
    props.put("bootstrap.servers", CLUSTER.bootstrapServers());
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-topic-event-lister");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    return props;
  }

  @AfterEach
  public void after() {
    client.close();
  }

  @SneakyThrows
  @Test
  public void test() {

    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(getKafkaProps());

    CLUSTER.createTopic("topic-1");

    consumer.subscribe(Collections.singletonList("topic-1"));

    GraphQL graphQL = root.accept(
        new BuildGraphQLEngine(),
        new VertxContext(new VertxJdbcClient(client),
            Map.of("addCustomer", new KafkaSinkEmitter(kafkaProducer, "topic-1"))));

    ExecutionInput executionInput = ExecutionInput.newExecutionInput()
        .query("mutation ($event: CreateCustomerEvent!) { addCustomer(event: $event) { customerid } }")
        .variables(Map.of("event", Map.of("customerid", 123)))
        .build();

    ExecutionResult executionResult = graphQL.execute(executionInput);

    Map<String, Object> data = executionResult.getData();
    assertEquals(1, data.size());
    assertEquals("{customerid=123}", data.get("addCustomer").toString());

    try {
      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
      assertFalse(records.isEmpty());

      for (ConsumerRecord<String, String> record : records) {
        assertEquals("{\"customerid\":123}", record.value());
      }
    } catch (WakeupException e) {
      // Ignore exception
    } finally {
      consumer.close();
    }
  }
}