package jobs

import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.ConfigOptions
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend
import org.apache.flink.streaming.api.environment.CheckpointConfig.ExternalizedCheckpointCleanup
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment

import java.time.{Duration, ZoneId}
import java.util.concurrent.TimeUnit


trait AbstractJobFactory {
  val env = StreamExecutionEnvironment.getExecutionEnvironment
    .setRuntimeMode(RuntimeExecutionMode.STREAMING)
    .enableCheckpointing(TimeUnit.SECONDS.toMillis(10))
    .disableOperatorChaining()

  val tableEnv = StreamTableEnvironment.create(env)
  tableEnv.getConfig.setLocalTimeZone(ZoneId.of("Asia/Shanghai"))

  val emptyErrTemplate = "programArg: '%s' is empty"

  def getConfiguration[T](key: String)(implicit default_value: T) = default_value match {
    case v: String => tableEnv.getConfig.getConfiguration.get(ConfigOptions.key(key).stringType.defaultValue(v)).asInstanceOf[T]
    case v: Int => tableEnv.getConfig.getConfiguration.get(ConfigOptions.key(key).intType.defaultValue(v)).asInstanceOf[T]
    case v: Long => tableEnv.getConfig.getConfiguration.get(ConfigOptions.key(key).longType.defaultValue(v)).asInstanceOf[T]
    case v: Boolean => tableEnv.getConfig.getConfiguration.get(ConfigOptions.key(key).booleanType.defaultValue(v)).asInstanceOf[T]
    case v: Float => tableEnv.getConfig.getConfiguration.get(ConfigOptions.key(key).floatType.defaultValue(v)).asInstanceOf[T]
    case v: Double => tableEnv.getConfig.getConfiguration.get(ConfigOptions.key(key).doubleType.defaultValue(v)).asInstanceOf[T]
    case v: Duration => tableEnv.getConfig.getConfiguration.get(ConfigOptions.key(key).durationType().defaultValue(v)).asInstanceOf[T]
  }

  def execute(): Unit

  def runJob(args: Array[String]) = {
    tableEnv.getConfig.addConfiguration(ParameterTool.fromArgs(args).getConfiguration)
    var jobName = getConfiguration("name")("")
    if (jobName == "") {
      jobName = this.getClass.getSimpleName.dropRight(1)
    }

    execute()

    if (env.getCheckpointConfig.getCheckpointInterval > 0) {
      env.setStateBackend(new EmbeddedRocksDBStateBackend(true))
      env.getCheckpointConfig.setCheckpointStorage("file:///tmp/flink-checkpoints")
      env.getCheckpointConfig.setExternalizedCheckpointCleanup(ExternalizedCheckpointCleanup.DELETE_ON_CANCELLATION)
      env.getCheckpointConfig.setMaxConcurrentCheckpoints(3)
    }

    env.execute(jobName)
  }
}
