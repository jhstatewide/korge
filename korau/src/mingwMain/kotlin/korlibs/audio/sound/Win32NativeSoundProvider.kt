@file:OptIn(ExperimentalStdlibApi::class)

package korlibs.audio.sound

import korlibs.datastructure.Pool
import korlibs.time.milliseconds
import korlibs.logger.Logger
import korlibs.io.async.delay
import korlibs.io.async.launchImmediately
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.ThreadLocal
import kotlin.native.concurrent.Worker

actual val nativeSoundProvider: NativeSoundProvider = Win32NativeSoundProvider

@ThreadLocal
private val Win32NativeSoundProvider_workerPool = Pool {
    Worker.start(name = "Win32NativeSoundProvider$it")
}

@ThreadLocal
private val Win32NativeSoundProvider_WaveOutProcess = Pool {
    WaveOutProcess(44100, 2).start(Win32NativeSoundProvider_workerPool.alloc())
}

object Win32NativeSoundProvider : NativeSoundProvider(), AutoCloseable {

    val workerPool get() = Win32NativeSoundProvider_WaveOutProcess

    override fun createPlatformAudioOutput(coroutineContext: CoroutineContext, freq: Int): PlatformAudioOutput =
        Win32PlatformAudioOutput(this, coroutineContext, freq)

    override fun close() {
        while (Win32NativeSoundProvider_workerPool.itemsInPool > 0) {
            Win32NativeSoundProvider_workerPool.alloc().requestTermination()
        }
    }
}

class Win32PlatformAudioOutput(
    val provider: Win32NativeSoundProvider,
    coroutineContext: CoroutineContext,
    val freq: Int
) : PlatformAudioOutput(coroutineContext, freq) {
    private var process: WaveOutProcess? = null
    private val logger = Logger("Win32PlatformAudioInput")

    override val availableSamples: Int get() = if (process != null) (process!!.length - process!!.position).toInt() else 0

    override var pitch: Double = 1.0
        set(value) {
            field = value
            process?.pitch?.value = value
        }
    override var volume: Double = 1.0
        set(value) {
            field = value
            process?.volume?.value = value
        }
    override var panning: Double = 0.0
        set(value) {
            field = value
            process?.panning?.value = value
        }

    override suspend fun add(samples: AudioSamples, offset: Int, size: Int) {
        // More than 1 second queued, let's wait a bit
        if (process == null || availableSamples > freq) {
            delay(200.milliseconds)
        }

        process!!.addData(samples, offset, size, freq)
    }

    override fun start() {
        process = provider.workerPool.alloc()
            .also { it.reopen(freq) }
        process!!.volume.value = volume
        process!!.pitch.value = pitch
        process!!.panning.value = panning
        //println("Win32PlatformAudioOutput.START WORKER: $worker")
    }

    override suspend fun wait() {
        //while (!process.isCompleted) {
        while (availableSamples > 0) {
        //while (process?.pendingAudio == true) {
            delay(10.milliseconds)
            //println("WAITING...: process.isCompleted=${process.isCompleted}")
        }
    }

    override fun stop() {
        //println("Win32PlatformAudioOutput.STOP WORKER: $worker")
        //process.stop()
        val process = this.process
        this.process = null
        if (process != null) {
            launchImmediately(coroutineContext) {
                try {
                    wait()
                } catch (e: CancellationException) {
                    // Do nothing
                } catch (e: Throwable) {
                    logger.error { "Error in Win32PlatformAudioOutput.stop:" }
                    e.printStackTrace()
                } finally {
                    provider.workerPool.free(process)
                }
            }
        }
    }
}
