# Kafka Streams (2.6.0) with a Redis-backed State Store
By default, Kafka Streams utilizes the RocksDB storage engine for persistent state stores. One glaring issue you may have noticed is the biggest delay occurs when Kafka Streams is in a rebalancing state, where it internally rebuilds these state stores from the change-log topics. This is especially common if you use Kubernetes and your application environment is designed to scale up (new pods). While it’s in a rebalancing state, all involved consumers processing is blocked, so what this means is the application won’t be able to service any requests until the process becomes unblocked. This isn’t ideal especially in a production environment or from a practicality standpoint. The overall time with this delay seems to correspond with how large your dataset is in your topics, so if you're developing under a continuous integration environment this poses a major challenge since every deployment will require some amount of upfront time to rebalance.

Anyways, this is the primary motivation for switching over to a custom state store. We can leverage a permanent storage solution like Redis such that in any new deployment or application restart, we can avoid these upfront rebalancing delay.

## What You Need

* Java 14
* Maven 3.6.0+
* Docker 19+
* Redis 5+

## Getting Started
First, install a local instance of Redis (if you haven't already). If you're running on Mac OS X, you can use [Homebrew](https://brew.sh) via the command:

```zsh
$  brew install redis
```

For Windows, there's a couple of ways to do this, but I generally like to install it using [Docker](https://docs.docker.com/docker-for-windows/install) with the [Redis](https://hub.docker.com/_/redis) image.

```pwsh
>  docker run --name some-redis -p 6379 -d redis
```

The easiest way to start the Redis server is just executing the `redis-server` command without any arguments.

```zsh
$  redis-server
```

You can connect to the Redis instance (with default configurations) using the redis-cli command:

```zsh
$  redis-cli -h localhost -p 6379
```

Next, we need to launch the various Confluent services (i.e. Schema Registry, Broker, ZooKeeper) locally by running the `docker-compose up -d` CLI command where the [docker-compose.yml](https://github.com/bchen04/springboot-kafka-streams-rest-api/blob/master/docker-compose.yml) file is. Typically, you can create a stack file (in the form of a YAML file) to define your applications. You can also run `docker-compose ps` to check the status of the stack. Notice, the endpoints from within the containers on your host machine.

| Name | From within containers | From host machine |
| ------------- | ------------- | ------------- |
| Kafka Broker | broker:9092 | localhost:9092 |
| Schema Registry  | http://schema-registry:8081 | http://localhost:8081 |
| ZooKeeper | zookeeper:2181 | localhost:2181 |

> Note: you can run `docker-compose down` to stop all services and containers.

As part of this sample, I've retrofitted the average aggregate example from [Confluent's Kafka Tutorials](https://kafka-tutorials.confluent.io/aggregating-average/kstreams.html) into this project. The API will calculate and return a running average rating for a given movie identifier. This should demonstrate how to build a basic API service on top of an aggregation result.

Notice in the `~/src/main/avro` directory, we have all our Avro schema files for the stream of `ratings` and `countsum`. For your convenience, the classes were already generated under the `~/src/main/java/io/confluent/demo` directory, but feel free to tinker with them and recompile the schemas if needed. The Avro classes can be programmatically generated using `Maven` or by manually invoking the [schema compiler](https://avro.apache.org/docs/1.10.0/gettingstartedjava.html#Compiling+the+schema). 

So before building and running the project, open a new terminal and run the following commands to generate your input and output topics.

```zsh
$  docker-compose exec broker kafka-topics --create --bootstrap-server \
   localhost:9092 --replication-factor 1 --partitions 1 --topic ratings

$  docker-compose exec broker kafka-topics --create --bootstrap-server \
   localhost:9092 --replication-factor 1 --partitions 1 --topic rating-averages
```

Next, we will need to produce some data onto the input topic.

```zsh
$  docker exec -i schema-registry /usr/bin/kafka-avro-console-producer --topic ratings --broker-list broker:9092\
    --property "parse.key=false"\
    --property "key.separator=:"\
    --property value.schema="$(< src/main/avro/rating.avsc)"
 ```
 
Paste in the following `json` data when prompted and be sure to press enter twice to actually submit it.

```json
{"movie_id":362,"rating":10}
{"movie_id":362,"rating":8}
 ```

Optionally, you can also see the consumer results on the output topic by running this command on a new terminal window:

```zsh
$  docker exec -it broker /usr/bin/kafka-console-consumer --topic rating-averages --bootstrap-server broker:9092 \
    --property "print.key=true"\
    --property "key.deserializer=org.apache.kafka.common.serialization.LongDeserializer" \
    --property "value.deserializer=org.apache.kafka.common.serialization.DoubleDeserializer" \
    --from-beginning
```

## Build and Run the Sample

You can import the code straight into your preferred IDE or run the sample using the following command (in the root project folder).

```zsh
$  mvn spring-boot:run
```

After the application runs, from the `redis-cli`, observe that a new [Redis stream](https://redis.io/topics/streams-intro) got created using the `KEYS` command:

```zsh
$  localhost:6379> keys *
   1) "rating-averages-stream"
```

To query the stream, you can use the `XRANGE` command where each entry returned is an array of the ID and the list of field-value pairs. The `-` and `+` represent the smallest and the greatest ID possible.

```zsh
$  localhost:6379> xrange rating-averages-stream - +
   1) 1) "1605043614011-0"
      2) 1) "362"
         2) "9.0"
```

Finally, navigate to [http://localhost:7001/swagger-ui/index.html?configUrl=/api-docs/swagger-config](http://localhost:7001/swagger-ui/index.html?configUrl=/api-docs/swagger-config) in your web browser to access the Swagger UI. If you used the same sample data from above, you can enter `362` as the `movieId` and it should return something similar like this below:

```json
{
  "movieId": 362,
  "rating": 9
}
```

You'll find that the `store.read(...)` method in the controller is able to query into this Redis-backed state store.

> Note: keep in mind the various [states](https://kafka.apache.org/25/javadoc/org/apache/kafka/streams/KafkaStreams.State.html) of the client. When a Kafka Streams instance is in `RUNNING` state, it allows for inspection of the stream's metadata using methods like `queryMetadataForKey()`. While it is in `REBALANCING` state, the REST service cannot immediately answer requests until the state stores are fully rebuilt.

## Troubleshooting

* In certain conditions, you may need to do a complete application reset. You can delete the application’s local state directory where the application instance was run. In this project, Kafka Streams persists local states under the `~/data` folder.
