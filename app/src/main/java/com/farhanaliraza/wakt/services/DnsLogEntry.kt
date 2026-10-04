package com.farhanaliraza.wakt.services

/** One DNS query as seen by the website filter, for the in-app diagnostics log. */
data class DnsLogEntry(
    val timeMillis: Long,
    val domain: String,
    val queryType: String,
    val action: String,
    val app: String?,
    val server: String
)
