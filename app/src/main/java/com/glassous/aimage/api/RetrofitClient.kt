package com.glassous.aimage.api
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"
    private const val DOUBAO_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/"
    private const val QWEN_BASE_URL = "https://dashscope.aliyuncs.com/"
    private const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/"
    // MiniMax 不同区域主机：国内与全球（注意 minimaxi 比 minimax 多一个 i）
    private const val MINIMAX_BASE_URL_CN = "https://api.minimax.chat/"
    // 依据官方示例改为 minimaxi.com（全球）
    private const val MINIMAX_BASE_URL_GLOBAL = "https://api.minimaxi.com/"

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .retryOnConnectionFailure(true)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    val geminiApiService: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }

    private val doubaoRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(DOUBAO_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    val doubaoApiService: DoubaoApiService by lazy {
        doubaoRetrofit.create(DoubaoApiService::class.java)
    }

    private val qwenRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(QWEN_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    val qwenApiService: QwenApiService by lazy {
        qwenRetrofit.create(QwenApiService::class.java)
    }

    private val openRouterRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(OPENROUTER_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    val openRouterApiService: OpenRouterApiService by lazy {
        openRouterRetrofit.create(OpenRouterApiService::class.java)
    }

    private val minimaxRetrofitCn: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(MINIMAX_BASE_URL_CN)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    private val minimaxRetrofitGlobal: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(MINIMAX_BASE_URL_GLOBAL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    val minimaxApiServiceCn: MiniMaxApiService by lazy {
        minimaxRetrofitCn.create(MiniMaxApiService::class.java)
    }

    val minimaxApiServiceGlobal: MiniMaxApiService by lazy {
        minimaxRetrofitGlobal.create(MiniMaxApiService::class.java)
    }
}