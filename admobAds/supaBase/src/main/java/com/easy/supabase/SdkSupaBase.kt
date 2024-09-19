package com.easy.supabase

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.monetization.core.ad_units.core.AdType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class SdkSupaBase(
    private val context: Context
) {
    private var supaUrl = "https://dnbsjqscqvhpfgpdxzma.supabase.co"
    private var supaKey =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRuYnNqcXNjcXZocGZncGR4em1hIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNjU2NTA5OCwiZXhwIjoyMDQyMTQxMDk4fQ.2jw2TonHK_qbNaycn2kzSrLtOHYVrzvA3MRR9Co1o9o"
    private var requestTableName = "Requests"

    private val backupKey = "BackupsTable"
    private var projectName = ""
    private var ipAddressRequestScenario: IpAddressScenario = IpAddressScenario.None
    private var supaClient: SupabaseClient? = null

    private val prefs = context.getSharedPreferences("SdkSupa", Context.MODE_PRIVATE)


    private val client: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            val json = Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            }
            json(json)
        }
    }


    fun initSupaBase(
        projectName: String,
        requestTableName: String = "Requests",
        supabaseUrl: String = "",
        supaBaseKey: String = "",
        ipAddressScenario: IpAddressScenario = IpAddressScenario.OneTimeOnly
    ) {
        supaUrl = supabaseUrl.ifBlank {
            supaUrl
        }
        supaKey = supaBaseKey.ifBlank {
            supaKey
        }
        this.ipAddressRequestScenario = ipAddressScenario
        this.requestTableName = requestTableName
        this.projectName = projectName

        checkForFailures()

    }

    private fun checkForFailures() {
        Handler(Looper.getMainLooper()).postDelayed({
            CoroutineScope(Dispatchers.IO).launch {
                getBackupRequestList().forEach {
                    insertModel(table = requestTableName, request = it, false)
                }
            }
            checkForFailures()
        }, 5_000)
    }

    private fun getSupaBaseClient(): SupabaseClient {
        if (supaClient == null) {
            supaClient = createSupabaseClient(
                supabaseKey = supaKey,
                supabaseUrl = supaUrl,
            ) {
                install(Postgrest)
            }
        }
        return supaClient!!
    }

    @Serializable
    data class IpAddress(
        val ip: String
    )

    private var lastTimeIpAddressLoaded: Pair<Long, String?> = Pair(0, null)

    private fun isIpLoadedOneTime() =
        lastTimeIpAddressLoaded.second != null && lastTimeIpAddressLoaded.first > 0

    private suspend fun getPublicIp(): String? {
        val canLoadIp = when (ipAddressRequestScenario) {
            IpAddressScenario.LoadEveryTime -> {
                true
            }

            IpAddressScenario.OneTimeOnly -> {
                isIpLoadedOneTime().not()
            }

            is IpAddressScenario.RequestAfterEvery -> {
                val currentTime = System.currentTimeMillis()
                val lastTimeLoaded = lastTimeIpAddressLoaded.first
                val currentDelay = currentTime - lastTimeLoaded
                val delayAllowed =
                    (ipAddressRequestScenario as IpAddressScenario.RequestAfterEvery).millis
                currentDelay > delayAllowed || lastTimeIpAddressLoaded.second == null
            }

            IpAddressScenario.None -> {
                false
            }
        }
        return if (canLoadIp) {
            withContext(Dispatchers.IO) {
                try {
                    val ipAddress =
                        client.get("https://api.ipify.org/?format=json").body<IpAddress>().ip
                    lastTimeIpAddressLoaded = Pair(System.currentTimeMillis(), ipAddress)
                    ipAddress
                } catch (_: Exception) {
                    null
                }
            }
        } else {
            lastTimeIpAddressLoaded.second
        }
    }

    private fun HashMap<String, String>.toModelList(): List<ExtraAdData> {
        return map {
            ExtraAdData(
                it.key,
                it.value
            )
        }
    }

    private fun isSdkInitialized(onClick: () -> Unit) {
        if (projectName.isBlank()) {
            throw IllegalArgumentException("Kindly call initSupaBase() and provide project name")
        }
        onClick.invoke()
    }

    fun onAdLoaded(
        userId: String,
        adId: String,
        adKey: String,
        adType: AdType,
        dataMap: HashMap<String, String>
    ) {
        isSdkInitialized {
            val requestTime = System.currentTimeMillis()
            CoroutineScope(Dispatchers.IO).launch {
                insertModel(
                    table = requestTableName,
                    request = SupaRequestModel(
                        userId = userId,
                        adKey = adKey,
                        adUnit = adId,
                        adType = adType.name,
                        ip = "",
                        dataMap = dataMap.toModelList(),
                        historyType = "loaded",
                        time = requestTime,
                        projectName = projectName
                    )
                )

            }
        }
    }

    fun onAdFailed(
        userId: String,
        adId: String,
        adKey: String,
        adType: AdType,
        errorCode: Int,
        message: String,
        dataMap: HashMap<String, String>
    ) {
        isSdkInitialized {
            val requestTime = System.currentTimeMillis()
            CoroutineScope(Dispatchers.IO).launch {
                insertModel(
                    table = requestTableName,
                    request = SupaRequestModel(
                        userId = userId,
                        adKey = adKey,
                        adUnit = adId,
                        adType = adType.name,
                        ip = "",
                        dataMap = dataMap.toModelList(),
                        historyType = "failed",
                        time = requestTime,
                        projectName = projectName,
                        error = errorCode,
                        message = message
                    )
                )
            }
        }
    }

    fun onAdImpression(
        userId: String,
        adId: String,
        adKey: String,
        adType: AdType,
        dataMap: HashMap<String, String>
    ) {
        isSdkInitialized {
            val requestTime = System.currentTimeMillis()
            CoroutineScope(Dispatchers.IO).launch {
                insertModel(
                    table = requestTableName,
                    request = SupaRequestModel(
                        userId = userId,
                        adKey = adKey,
                        adUnit = adId,
                        adType = adType.name,
                        ip = "",
                        dataMap = dataMap.toModelList(),
                        historyType = "impression",
                        time = requestTime,
                        projectName = projectName
                    )
                )
            }
        }
    }

    fun onAdClick(
        userId: String,
        adId: String,
        adKey: String,
        adType: AdType,
        dataMap: HashMap<String, String>
    ) {
        isSdkInitialized {
            CoroutineScope(Dispatchers.IO).launch {
                val requestTime = System.currentTimeMillis()
                insertModel(
                    table = requestTableName,
                    request = SupaRequestModel(
                        userId = userId,
                        adKey = adKey,
                        adUnit = adId,
                        adType = adType.name,
                        ip = "",
                        dataMap = dataMap.toModelList(),
                        historyType = "clicked",
                        time = requestTime,
                        projectName = projectName
                    )
                )
            }
        }
    }
    fun onAdRequested(
        userId: String,
        adId: String,
        adKey: String,
        adType: AdType,
        dataMap: HashMap<String, String>
    ) {
        isSdkInitialized {
            CoroutineScope(Dispatchers.IO).launch {
                val requestTime = System.currentTimeMillis()
                insertModel(
                    table = requestTableName,
                    request = SupaRequestModel(
                        userId = userId,
                        adKey = adKey,
                        adUnit = adId,
                        adType = adType.name,
                        ip = "",
                        dataMap = dataMap.toModelList(),
                        historyType = "request",
                        time = requestTime,
                        projectName = projectName
                    )
                )
            }
        }
    }

    private suspend fun insertModel(
        table: String,
        request: SupaRequestModel,
        fetchIp: Boolean = true
    ): String? {
        return try {
            val model = if (fetchIp) {
                val ipAddress = getPublicIp() ?: kotlin.run { "0.0.0" }
                request.copy(
                    ip = ipAddress
                )
            } else {
                request
            }
            withContext(Dispatchers.IO) {
                val response = getSupaBaseClient()
                    .from(table)
                    .insert(model)
                    .data
                removeItemFromBackup(model)
                response
            }
        } catch (_: Exception) {
            addItemInBackup(request)
            null
        }
    }


    private fun addItemInBackup(model: SupaRequestModel) {
        val list = getBackupRequestList().toMutableList()
        list.add(model)
        saveBackupText(Json.encodeToString(list))
    }

    private fun saveBackupText(backupText: String) {
        prefs.edit().putString(backupKey, backupText).apply()
    }

    private fun removeItemFromBackup(model: SupaRequestModel) {
        val list = getBackupRequestList().toMutableList()
        val index = list.indexOfFirst {
            it.hashCode() == model.hashCode()
        }
        if (index != -1) {
            list.removeAt(index)
            saveBackupText(Json.encodeToString(list))
        }
    }

    private fun getBackupRequestList(): List<SupaRequestModel> {
        val prevValues = prefs.getString(backupKey, "") ?: ""
        return if (prevValues.isNotBlank()) {
            Json.decodeFromString<List<SupaRequestModel>>(prevValues)
        } else {
            listOf()
        }.toMutableList()
    }

}