package com.yunx.feature.parser.baidu

/**
 * 百度网盘 API 业务异常。
 */
class BaiduApiException(message: String, val errno: Int = 0) : Exception(message)
