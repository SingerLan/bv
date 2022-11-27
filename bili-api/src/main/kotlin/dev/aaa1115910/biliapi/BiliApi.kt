package dev.aaa1115910.biliapi

import dev.aaa1115910.biliapi.entity.danmaku.DanmakuData
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuResponse
import dev.aaa1115910.biliapi.entity.live.HistoryDanmakuResponse
import dev.aaa1115910.biliapi.entity.live.LiveDanmuInfoResponse
import dev.aaa1115910.biliapi.entity.live.LiveRoomPlayInfoResponse
import dev.aaa1115910.biliapi.entity.video.PlayUrlResponse
import dev.aaa1115910.biliapi.entity.video.PopularVideosResponse
import dev.aaa1115910.biliapi.entity.video.VideoInfoResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import javax.xml.parsers.DocumentBuilderFactory

object BiliApi {
    private var endPoint: String = ""
    private lateinit var client: HttpClient
    private val logger = KotlinLogging.logger { }

    init {
        createClient()
    }

    private fun createClient() {
        client = HttpClient(OkHttp) {
            BrowserUserAgent()
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
            install(ContentEncoding) {
                deflate(1.0F)
                gzip(0.9F)
            }
            defaultRequest {
                host = "api.bilibili.com"
            }
        }
    }

    /**
     * 获取热门视频列表
     */
    suspend fun getPopularVideoData(
        pageNumber: Int = 1,
        pageSize: Int = 20
    ): PopularVideosResponse = client.get("/x/web-interface/popular") {
        parameter("pn", pageNumber)
        parameter("ps", pageSize)
    }.body()

    /**
     * 获取视频详细信息
     */
    suspend fun getVideoInfo(
        av: Int? = null,
        bv: String? = null
    ): VideoInfoResponse = client.get("/x/web-interface/view") {
        parameter("aid", av)
        parameter("bvid", bv)
    }.body()

    /**
     * 获取视频流
     */
    suspend fun getVideoPlayUrl(
        av: Int? = null,
        bv: Int? = null,
        cid: Int,
        qn: Int? = null,
        fnval: Int? = null,
        fnver: Int? = null,
        fourk: Int? = 0,
        session: String? = null,
        otype: String = "json",
        type: String = "",
        platform: String = "oc"
    ): PlayUrlResponse = client.get("/x/player/playurl") {
        parameter("avid", av)
        parameter("bvid", bv)
        parameter("cid", cid)
        parameter("qn", qn)
        parameter("fnval", fnval)
        parameter("fnver", fnver)
        parameter("fourk", fourk)
        parameter("session", session)
        parameter("otype", otype)
        parameter("type", type)
        parameter("platform", platform)
    }.body()

    /**
     * 通过[cid]获取视频弹幕
     */
    suspend fun getDanmakuXml(
        cid: Int
    ): DanmakuResponse {
        val xmlChannel = client.get("/x/v1/dm/list.so") {
            parameter("oid", cid)
        }.bodyAsChannel()

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = withContext(Dispatchers.IO) {
            dBuilder.parse(xmlChannel.toInputStream())
        }
        doc.documentElement.normalize()

        val chatServer = doc.getElementsByTagName("chatserver").item(0).textContent
        val chatId = doc.getElementsByTagName("chatid").item(0).textContent.toInt()
        val maxLimit = doc.getElementsByTagName("maxlimit").item(0).textContent.toInt()
        val state = doc.getElementsByTagName("state").item(0).textContent.toInt()
        val realName = doc.getElementsByTagName("real_name").item(0).textContent.toInt()
        val source = doc.getElementsByTagName("source").item(0).textContent

        val data = mutableListOf<DanmakuData>()
        val danmakuNodes = doc.getElementsByTagName("d")

        for (i in 0 until danmakuNodes.length) {
            val danmakuNode = danmakuNodes.item(i)
            val p = danmakuNode.attributes.item(0).textContent
            val text = danmakuNode.textContent
            data.add(DanmakuData.fromString(p, text))
        }

        val response = DanmakuResponse(chatServer, chatId, maxLimit, state, realName, source, data)
        return response
    }

    /**
     * 获取直播间[roomId]的弹幕连接地址等信息，例如 token
     */
    suspend fun getLiveDanmuInfo(roomId: Int): LiveDanmuInfoResponse =
        client.get("/xlive/web-room/v1/index/getDanmuInfo") {
            parameter("id", roomId)
        }.body()

    /**
     * 获取直播间[roomId]的信息
     */
    suspend fun getLiveRoomPlayInfo(roomId: Int): LiveRoomPlayInfoResponse =
        client.get("/xlive/web-room/v1/index/getRoomPlayInfo") {
            parameter("room_id", roomId)
        }.body()

    /**
     * 获取直播间[roomId]的历史弹幕
     */
    suspend fun getLiveDanmuHistory(roomId: Int): HistoryDanmakuResponse =
        client.get("https://api.live.bilibili.com/xlive/web-room/v1/dM/gethistory") {
            parameter("roomid", roomId)
        }.body()
}