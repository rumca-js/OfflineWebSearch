package io.github.rumcajs.offlinewebsearch.webtoolkit


data class PageResponseObject(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val text: String? = null,
    val bytes: ByteArray? = null, // Changed from String? to ByteArray?
    val error: String? = null
) {
    val contentType: String?
        get() = headers.entries.find { it.key.equals("Content-Type", ignoreCase = true) }?.value?.firstOrNull()

    val length: Long?
        get() = headers.entries.find { it.key.equals("Content-Length", ignoreCase = true) }?.value?.firstOrNull()?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
            ?: bytes?.size?.toLong() // Check the binary size first
            ?: text?.toByteArray(Charsets.UTF_8)?.size?.toLong()

    val isValid: Boolean get() = NetworkUtils.isStatusCodeValid(statusCode)
    val isInvalid: Boolean get() = NetworkUtils.isStatusCodeInvalid(statusCode)

    // Overriding equals and hashCode is highly recommended when using ByteArrays in data classes
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PageResponseObject

        if (statusCode != other.statusCode) return false
        if (headers != other.headers) return false
        if (text != other.text) return false
        if (bytes != null) {
            if (other.bytes == null) return false
            if (!bytes.contentEquals(other.bytes)) return false
        } else if (other.bytes != null) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}
