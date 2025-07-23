package com.dororogames.tarotllama

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class LlamaBridge {
    private val tag: String? = this::class.simpleName

    private var model: Long = 0
    private var context: Long = 0
    private var batch: Long = 0
    private var sampler: Long = 0

    private val runLoop: CoroutineDispatcher = Executors.newSingleThreadExecutor {
        thread(start = false, name = "Llm-RunLoop") {
            Log.d(tag, "Dedicated thread for native code: ${Thread.currentThread().name}")

            System.loadLibrary("llama_bridge")

            log_to_android()
            backend_init(false)

            Log.d(tag, system_info())

            it.run()
        }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, exception: Throwable ->
                Log.e(tag, "Unhandled exception", exception)
            }
        }
    }.asCoroutineDispatcher()


    private external fun log_to_android()
    private external fun load_model(filename: String): Long
    private external fun free_model(model: Long)
    private external fun new_context(model: Long): Long
    private external fun free_context(context: Long)
    private external fun backend_init(numa: Boolean)
    private external fun backend_free()
    private external fun new_batch(nTokens: Int, embd: Int, nSeqMax: Int): Long
    private external fun free_batch(batch: Long)
    private external fun new_sampler(): Long
    private external fun free_sampler(sampler: Long)
    private external fun bench_model(
        context: Long,
        model: Long,
        batch: Long,
        pp: Int,
        tg: Int,
        pl: Int,
        nr: Int
    ): String

    private external fun system_info(): String

    private external fun completion_init(
        context: Long,
        batch: Long,
        text: String,
        formatChat: Boolean,
        nLen: Int
    ): Int

    private external fun completion_loop(
        context: Long,
        batch: Long,
        sampler: Long,
        nLen: Int,
        ncur: Int
    ): String?

    private external fun kv_cache_clear(context: Long)

    suspend fun init(context: Context) {
        withContext(runLoop) {
            val modelPath = copyModelIfNeeded(context).absolutePath
            load(modelPath)
        }
    }

    suspend fun destroy() {
        withContext(runLoop) {
            unload()
        }
    }

    fun copyModelIfNeeded(context: Context): File {
        val file = File(context.filesDir, "tiny2.gguf")
        file.setReadable(true, false)
        Log.e("LLAMA_CHECK - ", file.exists().toString())
        if (!file.exists()) {
            context.assets.open("tiny2.gguf").use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                    file.setReadable(true, false)
                    Log.e("MODEL_COPY", "copy 완료됨")
                }
            }
        }
        return file
    }

    suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1): String {
        return withContext(runLoop) {
            if (model == 0L) throw IllegalStateException("Model not loaded")
            Log.d(tag, "bench(): model, context, batch, sampler = $model, $context, $batch, $sampler")
            bench_model(context, model, batch, pp, tg, pl, nr)
        }
    }

    suspend fun load(pathToModel: String) {
        withContext(runLoop) {
            if (model != 0L) {
                Log.e(tag, "Model is already loaded.")
                return@withContext
            }

            model = load_model(pathToModel)
            if (model == 0L) throw IllegalStateException("load_model() failed")

            context = new_context(model)
            if (context == 0L) throw IllegalStateException("new_context() failed")

            batch = new_batch(512, 0, 1)
            if (batch == 0L) throw IllegalStateException("new_batch() failed")

            sampler = new_sampler()
            if (sampler == 0L) throw IllegalStateException("new_sampler() failed")

            Log.i(tag, "Loaded model $pathToModel")
        }
    }

    private val nlen: Int = 400
    fun send(message: String, androidContext: Context, formatChat: Boolean = false): Flow<String> = flow {
        val startCur = completion_init(context, batch, message, formatChat, nlen)
        emit("\"INITAILIZED\"")
        var intCur = startCur
        while (intCur <= nlen) {
            val generatedText = completion_loop(context, batch, sampler, nlen, intCur++)
            if (generatedText == null) {
                break
            }
            emit(generatedText)
        }
        kv_cache_clear(context)
    }.flowOn(runLoop)

    suspend fun unload() {
        withContext(runLoop) {
            if (model != 0L) {
                free_context(context)
                free_model(model)
                free_batch(batch)
                free_sampler(sampler)

                model = 0L
                context = 0L
                batch = 0L
                sampler = 0L
            }
        }
    }

    companion object {
        private val _instance: LlamaBridge = LlamaBridge()
        fun instance(): LlamaBridge = _instance
    }
}
