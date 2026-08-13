package com.ggdevhub.newsapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform