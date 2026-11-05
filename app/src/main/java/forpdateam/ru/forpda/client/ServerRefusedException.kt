package forpdateam.ru.forpda.client

/**
 * The forum (or Cloudflare in front of it) refused to serve the request: too many requests,
 * a Cloudflare block/challenge, or the server is down. Shown to the user as a dialog, not a toast.
 */
class ServerRefusedException(
    val code: Int,
    val url: String,
    val kind: Kind,
    val retryAfterSeconds: Long?
) : Exception("Server refused: code=$code, kind=$kind, retryAfter=$retryAfterSeconds, url=$url") {

    enum class Kind { TOO_MANY_REQUESTS, CLOUDFLARE, UNAVAILABLE }

    companion object {
        private val UNAVAILABLE_CODES = setOf(502, 503, 504, 520, 521, 522, 523, 524, 525, 526)

        /** Returns null when the response is an ordinary error, not a refusal. */
        @JvmStatic
        fun classify(code: Int, url: String, header: (String) -> String?, body: () -> String): ServerRefusedException? {
            val viaCloudflare = header("cf-mitigated") != null ||
                    header("Server")?.contains("cloudflare", ignoreCase = true) == true
            val retryAfter = header("Retry-After")?.trim()?.toLongOrNull()
            val kind = when {
                code == 429 -> Kind.TOO_MANY_REQUESTS
                (code == 403 || code == 503) && viaCloudflare && looksLikeChallenge(body()) -> Kind.CLOUDFLARE
                code in UNAVAILABLE_CODES -> Kind.UNAVAILABLE
                else -> return null
            }
            return ServerRefusedException(code, url, kind, retryAfter)
        }

        private fun looksLikeChallenge(body: String): Boolean =
            body.contains("challenge-platform") || body.contains("cf-chl") ||
                    body.contains("Just a moment") || body.contains("Attention Required") ||
                    body.contains("cf-error-details")
    }
}
