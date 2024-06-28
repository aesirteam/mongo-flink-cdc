package jobs

import com.starrocks.connector.flink.row.sink.StarRocksSinkOP
import com.starrocks.connector.flink.table.sink.{StarRocksDynamicSinkFunctionV2, StarRocksSinkOptions, StarRocksSinkRowDataWithMeta}
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.cdc.connectors.mongodb.source.MongoDBSource
import org.apache.flink.cdc.debezium.JsonDebeziumDeserializationSchema
import org.apache.flink.shaded.guava31.com.google.common.hash.Hashing
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.streaming.api.scala.{OutputTag, createTypeInformation}
import org.apache.flink.util.{Collector, Preconditions}

import java.time.{Instant, LocalDateTime, ZoneId}

object MongoExporter extends AbstractJobFactory with App {
  private case class UnknownFieldRow(key: String, value: AnyRef)

  private def dateTimeConverter(ts: Long) = {
    LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault).toString
  }

  private def generateMD5(input: String) = {
    Hashing.md5().hashBytes(input.getBytes("UTF-8")).toString
  }

  override def execute(): Unit = {
    val mongo_hosts = getConfiguration[String]("mongo.hosts")("localhost:27117")
    val mongo_user = getConfiguration[String]("mongo.username")("")
    val mongo_password = getConfiguration[String]("mongo.password")("")
    val mongo_db = getConfiguration[String]("mongo.database")("test")
    val mongo_collection = getConfiguration[String]("mongo.collection")("collection")

    Preconditions.checkNotNull(mongo_hosts, "mongo.hosts is empty")
    Preconditions.checkNotNull(mongo_db, "mongo.database")
    Preconditions.checkNotNull(mongo_collection, "mongo.collection is empty")

    val fe_nodes = getConfiguration[String]("starrocks.fe_nodes")("")
    val username = getConfiguration[String]("starrocks.username")("root")
    val password = getConfiguration[String]("starrocks.password")("")
    val sinkDb = getConfiguration[String]("starrocks.database")("mongo")

    Preconditions.checkNotNull(fe_nodes, "starrocks.fe_nodes is empty")
    Preconditions.checkNotNull(username, "starrocks.username is empty")
    Preconditions.checkNotNull(password, "starrocks.password is empty")
    Preconditions.checkNotNull(sinkDb, "starrocks.database is empty")

    val mapper = new ObjectMapper()
    val knownFieldKey = Array("_id", "_id_$oid", "_class", "corpId", "delete", "effectDate_$date", "effectVersion", "empId", "gmtCreate_$date", "gmtModified_$date", "invalidDate_$date", "operate", "operateType", "syncDingTalk")

    val sinkOutputTag = Array(
      new OutputTag[StarRocksSinkRowDataWithMeta]("hrm_org_history"),
      new OutputTag[StarRocksSinkRowDataWithMeta]("hrm_org_history_detail")
    )

    val sourceFunction = MongoDBSource.builder()
      .hosts(mongo_hosts)
      //      .username("flinkuser")
      //      .password("flinkpw")
      .databaseList(mongo_db)
      .collectionList(s"${mongo_db}.${mongo_collection}")
      .deserializer(new JsonDebeziumDeserializationSchema())

    if (!mongo_user.isEmpty) {
      sourceFunction.username(mongo_user)
    }

    if (!mongo_password.isEmpty) {
      sourceFunction.password(mongo_password)
    }

    val sinkOptions = StarRocksSinkOptions.builder()
      .withProperty("jdbc-url", s"jdbc:mysql://${fe_nodes.replace("8030", "9030")}")
      .withProperty("load-url", s"${fe_nodes.replace(",", ";")}")
      .withProperty("username", username)
      .withProperty("password", password)
      .withProperty("database-name", "*")
      .withProperty("table-name", "*")
      .withProperty("sink.properties.format", "json")
    //        .withProperty("sink.semantic", "exactly-once")
    //        .withProperty("label-prefix", s"${getClass.getSimpleName.dropRight(1)}")

    val sinkFunction = new StarRocksDynamicSinkFunctionV2[StarRocksSinkRowDataWithMeta](sinkOptions.build())

    val sourceStream = env.fromSource(sourceFunction.build(), WatermarkStrategy.noWatermarks(), "MongoDB")
      .map(json => try {
        mapper.readTree(json)
      } catch {
        case ex: Exception => {
          ex.printStackTrace()
          mapper.createObjectNode().put("operationType", "")
        }
      })
      .process(new ProcessFunction[JsonNode, StarRocksSinkRowDataWithMeta] {
        override def processElement(in: JsonNode, ctx: ProcessFunction[JsonNode, StarRocksSinkRowDataWithMeta]#Context, out: Collector[StarRocksSinkRowDataWithMeta]): Unit = {
          //          val db = in.at("/ns/db").asText
          //          val table = in.at("/ns/coll").asText
          in.get("operationType").asText match {
            case "insert" | "update" => {
              val doc = mapper.readTree(in.get("fullDocument").asText)
              val oid = doc.get("_id").get("$oid").asText
              //              println(in.get("operationType").asText, oid, doc)
              var unknownFields = Map[String, UnknownFieldRow]()

              doc.fields.forEachRemaining(x => {
                if (!knownFieldKey.contains(x.getKey)) {
                  unknownFields += generateMD5(s"${x.getKey}$oid") -> UnknownFieldRow(x.getKey, x.getValue)
                }
              })

              var rowData = new StarRocksSinkRowDataWithMeta()
              rowData.setDatabase(sinkDb)
              rowData.setTable(sinkOutputTag(0).getId)
              rowData.addDataRow(mapper.createObjectNode()
                .put(StarRocksSinkOP.COLUMN_KEY, StarRocksSinkOP.UPSERT.ordinal)
                .put("oid", oid)
                .put("_class", doc.get("_class").asText)
                .put("corpId", doc.get("corpId").asText)
                .put("_delete", doc.get("delete").asText)
                .put("effectDate", dateTimeConverter(doc.get("effectDate_$date").asLong))
                .put("effectVersion", doc.get("effectVersion").asInt)
                .put("empId", doc.get("empId").asText)
                .put("gmtCreate", dateTimeConverter(doc.get("gmtCreate_$date").asLong))
                .put("gmtModified", dateTimeConverter(doc.get("gmtModified_$date").asLong))
                .put("invalidDate", dateTimeConverter(doc.get("invalidDate_$date").asLong))
                .put("operate", doc.get("operate").asText)
                .put("operateType", doc.get("operateType").asText)
                .put("syncDingTalk", doc.get("syncDingTalk").asBoolean)
                .putPOJO("refKey", unknownFields.map(x => x._1).toArray)
                .toString
              )
              ctx.output(sinkOutputTag(0), rowData)

              if (!unknownFields.isEmpty) {
                rowData = new StarRocksSinkRowDataWithMeta()
                rowData.setDatabase(sinkDb)
                rowData.setTable(sinkOutputTag(1).getId)
                unknownFields.foreach(x =>
                  rowData.addDataRow(mapper.createObjectNode()
                    .put(StarRocksSinkOP.COLUMN_KEY, StarRocksSinkOP.UPSERT.ordinal)
                    .put("id", x._1)
                    .put("oid", oid)
                    .put("name", x._2.key)
                    .putPOJO("value", x._2.value)
                    .toString
                  )
                )
                ctx.output(sinkOutputTag(1), rowData)
              }
            }
            case "delete" => {
              val doc = mapper.readTree(in.get("documentKey").asText)
              //              println(in.get("operationType").asText, in)
              val rowData = new StarRocksSinkRowDataWithMeta()
              rowData.setDatabase(sinkDb)
              rowData.setTable(sinkOutputTag(0).getId)
              rowData.addDataRow(mapper.createObjectNode()
                .put(StarRocksSinkOP.COLUMN_KEY, StarRocksSinkOP.DELETE.ordinal)
                .put("oid", doc.get("_id").get("$oid").asText)
                .toString
              )
              ctx.output(sinkOutputTag(0), rowData)
            }
          }
        }
      })

    for (tag <- sinkOutputTag) {
      sourceStream.getSideOutput(tag).addSink(sinkFunction)
    }
  }

  runJob(args)
}
