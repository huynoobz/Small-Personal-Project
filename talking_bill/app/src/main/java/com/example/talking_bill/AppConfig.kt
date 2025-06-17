package com.example.talking_bill

typealias AppConfig = Map<String, AppConfigItem>

data class AppConfigItem(
    val receive_keyword: List<String>,
    val m_regex: List<String>
) 