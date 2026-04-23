package us.beary.netlens.feature.httptester.model

enum class HttpMethod(val label: String) {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
    PATCH("PATCH"),
    HEAD("HEAD"),
}
