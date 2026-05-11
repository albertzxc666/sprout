package com.transcard.data.api

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
