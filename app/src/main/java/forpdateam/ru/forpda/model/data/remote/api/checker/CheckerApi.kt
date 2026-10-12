package forpdateam.ru.forpda.model.data.remote.api.checker

import forpdateam.ru.forpda.entity.remote.checker.UpdateData
import forpdateam.ru.forpda.model.data.remote.IWebClient

/**
 * Created by radiationx on 28.01.18.
 */
class CheckerApi(
    private val client: IWebClient,
    private val checkerParser: CheckerParser
) {

    fun checkUpdate(): UpdateData = client
        .get("https://raw.githubusercontent.com/Steklorez/ForPDA-Fork-Fix-Fuck/main/update/check.json")
        .let {
            checkerParser.parse(it.body)
        }

    fun loadPatterns(): String = client
        .get("https://raw.githubusercontent.com/Steklorez/ForPDA-Fork-Fix-Fuck/main/update/patterns.json")
        .body

}