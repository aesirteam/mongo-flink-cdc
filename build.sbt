ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.16"

lazy val root = (project in file("."))
  .settings(
    name := "mongo-flink-cdc"
  )

val flinkVersion = "1.15.3"

//val hadoopVersion = "3.2.4"
//
//val paimonVersion = "0.8.0"

val finkMajorVersion = {
  val regex = """^([^\.]*)\.([^\.]*)\.(.*)""".r
  flinkVersion match {
    case regex(v1, v2, _) => v1 + "." + v2
    case _ => null
  }
}

val flinkLibraryPath = s"file:///home/zhongkui/download/flink-${flinkVersion}/lib"

libraryDependencies ++= Seq(
  "org.apache.flink" % "flink-cep" % flinkVersion from s"${flinkLibraryPath}/flink-cep-${flinkVersion}.jar",
  "org.apache.flink" % "flink-connector-files" % flinkVersion from s"${flinkLibraryPath}/flink-connector-files-${flinkVersion}.jar",
  "org.apache.flink" % "flink-csv" % flinkVersion from s"${flinkLibraryPath}/flink-csv-${flinkVersion}.jar",
  "org.apache.flink" % "flink-dist" % flinkVersion from s"${flinkLibraryPath}/flink-dist-${flinkVersion}.jar",
  "org.apache.flink" % "flink-json" % flinkVersion from s"${flinkLibraryPath}/flink-json-${flinkVersion}.jar",
  "org.apache.flink" %% "flink-scala" % flinkVersion from s"${flinkLibraryPath}/flink-scala_${scalaBinaryVersion.value}-${flinkVersion}.jar",
  "org.apache.flink" % "flink-shaded-zookeeper" % "3.5.9" from s"${flinkLibraryPath}/flink-shaded-zookeeper-3.5.9.jar",
  "org.apache.flink" % "flink-table-api-java-uber" % flinkVersion from s"${flinkLibraryPath}/flink-table-api-java-uber-${flinkVersion}.jar",
  "org.apache.flink" %% "flink-table-api-scala-bridge" % flinkVersion from s"${flinkLibraryPath}/flink-table-api-scala-bridge_${scalaBinaryVersion.value}-${flinkVersion}.jar",
  "org.apache.flink" %% "flink-table-planner" % flinkVersion from s"${flinkLibraryPath}/flink-table-planner_${scalaBinaryVersion.value}-${flinkVersion}.jar",
  "org.apache.flink" % "flink-table-runtime" % flinkVersion from s"${flinkLibraryPath}/flink-table-runtime-${flinkVersion}.jar",
  "org.apache.logging.log4j" % "log4j-1.2-api" % "2.17.1" from s"${flinkLibraryPath}/log4j-1.2-api-2.17.1.jar",
  "org.apache.logging.log4j" % "log4j-api" % "2.17.1" from s"${flinkLibraryPath}/log4j-api-2.17.1.jar",
  "org.apache.logging.log4j" % "log4j-core" % "2.17.1" from s"${flinkLibraryPath}/log4j-core-2.17.1.jar",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.17.1" from s"${flinkLibraryPath}/log4j-slf4j-impl-2.17.1.jar",
  "org.apache.flink" % "flink-sql-connector-mongodb-cdc" % "3.1.1" from s"${flinkLibraryPath}/ext/flink-sql-connector-mongodb-cdc-3.1.1.jar",
  "com.starrocks" % "flink-connector-starrocks" % s"1.2.9_flink-${finkMajorVersion}" from s"${flinkLibraryPath}/ext/flink-connector-starrocks-1.2.9_flink-${finkMajorVersion}.jar",
  //  "org.apache.flink" % "flink-s3-fs-hadoop" % flinkVersion from s"${flinkLibraryPath}/ext/flink-s3-fs-hadoop-${flinkVersion}.jar",
  //  "org.apache.hadoop" % "hadoop-hdfs-client" % hadoopVersion from s"${flinkLibraryPath}/ext/hadoop-hdfs-client-${hadoopVersion}.jar",
  //  "org.apache.paimon" % s"paimon-flink-${finkMajorVersion}" % paimonVersion from s"${flinkLibraryPath}/ext/paimon-flink-${finkMajorVersion}-${paimonVersion}.jar",
  //  "org.apache.paimon" % s"paimon-s3" % paimonVersion from s"${flinkLibraryPath}/ext/paimon-s3-${paimonVersion}.jar",
)

resolvers += "ocs" at "https://maven.aliyun.com/repository/central/"