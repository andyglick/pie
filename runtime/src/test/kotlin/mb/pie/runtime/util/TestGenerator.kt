package mb.pie.runtime.util

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import mb.pie.api.*
import mb.pie.api.stamp.FileStamper
import mb.pie.api.stamp.OutputStamper
import mb.pie.api.stamp.output.EqualsOutputStamper
import mb.pie.api.stamp.path.HashFileStamper
import mb.pie.api.stamp.path.ModifiedFileStamper
import mb.pie.runtime.PieBuilderImpl
import mb.pie.runtime.cache.MapCache
import mb.pie.runtime.cache.NoopCache
import mb.pie.runtime.layer.ValidationLayer
import mb.pie.runtime.logger.StreamLogger
import mb.pie.runtime.logger.exec.LoggerExecutorLogger
import mb.pie.runtime.share.NonSharingShare
import mb.pie.runtime.store.InMemoryStore
import mb.pie.runtime.taskdefs.MutableMapTaskDefs
import org.junit.jupiter.api.*
import java.util.stream.Stream

object TestGenerator {
  fun generate(
    name: String,
    storeGens: Array<(Logger) -> Store> = arrayOf({ _ -> InMemoryStore() }),
    cacheGens: Array<(Logger) -> Cache> = arrayOf({ _ -> NoopCache() }, { _ -> MapCache() }),
    shareGens: Array<(Logger) -> Share> = arrayOf({ _ -> NonSharingShare() }),
    layerGens: Array<(Logger) -> Layer> = arrayOf({ l -> ValidationLayer(l) }),
    defaultOutputStampers: Array<OutputStamper> = arrayOf(EqualsOutputStamper()),
    defaultFileReqStampers: Array<FileStamper> = arrayOf(ModifiedFileStamper(), HashFileStamper()),
    defaultFileGenStampers: Array<FileStamper> = arrayOf(ModifiedFileStamper(), HashFileStamper()),
    executorLoggerGen: (Logger) -> ExecutorLogger = { l -> LoggerExecutorLogger(l) },
    logger: Logger = StreamLogger.only_errors(),
    testFunc: TestCtx.() -> Unit
  ): Stream<out DynamicNode> {
    val fsGen = { Jimfs.newFileSystem(Configuration.unix()) }

    val tests =
      storeGens.flatMap { storeGen ->
        cacheGens.flatMap { cacheGen ->
          shareGens.flatMap { shareGen ->
            layerGens.flatMap { layerGen ->
              defaultOutputStampers.flatMap { defaultOutputStamper ->
                defaultFileReqStampers.flatMap { defaultFileReqStamper ->
                  defaultFileGenStampers.map { defaultFileGenStamper ->
                    val fs = fsGen()
                    val taskDefs = MutableMapTaskDefs()
                    val pieBuilder = PieBuilderImpl()
                      .withTaskDefs(taskDefs)
                      .withStore(storeGen)
                      .withCache(cacheGen)
                      .withShare(shareGen)
                      .withDefaultOutputStamper(defaultOutputStamper)
                      .withDefaultFileReqStamper(defaultFileReqStamper)
                      .withDefaultFileGenStamper(defaultFileGenStamper)
                      .withLayer(layerGen)
                      .withLogger(logger)
                      .withExecutorLogger(executorLoggerGen)
                    val pie = pieBuilder.build()
                    DynamicTest.dynamicTest("${pie.store}, ${pie.cache}, ${pie.share}, ${pie.defaultOutputStamper}, ${pie.defaultFileReqStamper}, ${pie.defaultFileGenStamper}, ${pie.layerFactory(pie.logger)}", {
                      val context = TestCtx(pie, taskDefs, fs)
                      context.testFunc()
                      context.close()
                    })
                  }
                }
              }
            }
          }
        }
      }.stream()
    return Stream.of(DynamicContainer.dynamicContainer(name, tests))
  }
}