package com.yunx.feature.parser.quark

/**
 * 夸克 API 业务异常：携带服务端返回的 message 与 code 字段。
 */
class QuarkApiException(message: String, val code: Int = 0) : Exception(message)
