package forpdateam.ru.forpda.model.data.remote.api.favorites

import android.net.Uri
import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.client.OkHttpResponseException
import java.io.IOException
import java.util.Collections

/**
 * Created by radiationx on 22.09.16.
 */

class FavoritesApi(
    private val webClient: IWebClient,
    private val favoritesParser: FavoritesParser
) {

    fun getFavorites(st: Int, all: Boolean, sorting: Sorting): FavData {
        val uriBuilder = Uri.Builder()
            .scheme("https")
            .authority("4pda.to")
            .appendPath("forum")
            .appendQueryParameter("act", "fav")
            .appendQueryParameter("type", "all")
            .appendQueryParameter("st", st.toString())
            .appendQueryParameter(Sorting.Key.HEADER, sorting.key)
            .appendQueryParameter(Sorting.Order.HEADER, sorting.order)

        val response = webClient.get(uriBuilder.build().toString())

        val data = favoritesParser.parseFavorites(response.body)

        if (all) {
            while (true) {
                if (data.pagination.current >= data.pagination.all) {
                    break
                }
                val favData =
                    getFavorites(data.pagination.getPage(data.pagination.current), false, sorting)
                data.pagination = favData.pagination
                if (favData.items.isEmpty()) {
                    break
                }
                data.items.addAll(favData.items)
            }
            data.pagination.all = 1

            if (data.sorting.key == Sorting.Key.TITLE) {
                if (data.sorting.order == Sorting.Order.DESC) {
                    Collections.sort(data.items, DESC_ORDER)
                } else if (data.sorting.order == Sorting.Order.ASC) {
                    Collections.sort(data.items, ASC_ORDER)
                }
            }
        }

        return data
    }

    fun editSubscribeType(type: String?, favId: Int): Boolean {
        checkNotNull(type)
        val response =
            webClient.get("https://4pda.to/forum/index.php?act=fav&sort_key=&sort_by=&type=all&st=0&tact=$type&selectedtids=$favId")
        return favoritesParser.checkIsComplete(response.body)
    }

    fun editPinState(type: String?, favId: Int): Boolean {
        checkNotNull(type)
        val builder = NetworkRequest.Builder()
            .url("https://4pda.to/forum/index.php?act=fav")
            .formHeader("selectedtids", favId.toString())
            .formHeader("tact", type)
        val response = webClient.request(builder.build())
        return favoritesParser.checkIsComplete(response.body)
    }

    fun delete(favId: Int): Boolean {
        val builder = NetworkRequest.Builder()
            .url("https://4pda.to/forum/index.php?act=fav")
            .xhrHeader()
            .formHeader("selectedtids", favId.toString())
            .formHeader("tact", "delete")
        val response = webClient.request(builder.build())
        return favoritesParser.checkIsComplete(response.body)
    }

    fun add(id: Int, action: Int, type: String?): Boolean {
        checkNotNull(type)
        var url = "https://4pda.to/forum/index.php?act=fav&type=add&track_type=$type"
        if (action == ACTION_ADD_FORUM) {
            url += "&f="
        } else if (action == ACTION_ADD) {
            url += "&t="
        }
        url += id
        val response = webClient.request(NetworkRequest.Builder().url(url).build())
        return favoritesParser.checkIsComplete(response.body)
    }

    /**
     * Marks one topic as read on the forum: opening its "last post" link is what the site counts as
     * having read it. Pauses before every call and retries on flood limits and server hiccups.
     */
    fun markTopicRead(topicId: Int): Boolean {
        val url = "https://4pda.to/forum/index.php?showtopic=$topicId&view=getlastpost"
        var attempt = 0
        while (attempt < READ_MAX_ATTEMPTS) {
            Thread.sleep(DELAY_BETWEEN_CALLS_MS)
            try {
                webClient.get(url)
                return true
            } catch (ex: Exception) {
                val retry = ex is IOException ||
                        (ex is OkHttpResponseException && (ex.code == 429 || ex.code == 404 || ex.code in 500..599))
                attempt++
                if (!retry || attempt >= READ_MAX_ATTEMPTS) return false
                Thread.sleep(minOf(READ_BACKOFF_BASE_MS * (1L shl (attempt - 1)), READ_BACKOFF_CAP_MS))
            }
        }
        return false
    }

    companion object {

        private const val DELAY_BETWEEN_CALLS_MS = 300L
        private const val READ_MAX_ATTEMPTS = 4
        private const val READ_BACKOFF_BASE_MS = 2_500L
        private const val READ_BACKOFF_CAP_MS = 10_000L

        const val ACTION_EDIT_SUB_TYPE = 0
        const val ACTION_EDIT_PIN_STATE = 1
        const val ACTION_DELETE = 2
        const val ACTION_ADD = 3
        const val ACTION_ADD_FORUM = 4
        val SUB_TYPES = arrayOf("none", "delayed", "immediate", "daily", "weekly", "pinned")

        private val DESC_ORDER = Comparator<FavItem> { item1, item2 ->
            item1.topicTitle.orEmpty().compareTo(item2.topicTitle.orEmpty(), ignoreCase = true)
        }
        private val ASC_ORDER = Comparator<FavItem> { item1, item2 ->
            item2.topicTitle.orEmpty().compareTo(item1.topicTitle.orEmpty(), ignoreCase = true)
        }
    }
}
