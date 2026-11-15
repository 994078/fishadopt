package com.fishadopt.app.util

/**
 * 日志脱敏器（PRD 4.5 / 4.6 / 8 章）。
 *
 * 所有日志在写入与导出前必须经过 [mask] 处理，将命中的敏感特征替换为固定掩码，
 * 覆盖 API Key、鉴权头与 Cookie 特征。掩码不保留原值长度之外的任何信息。
 */
object SensitiveMasker {

    /** 固定掩码。 */
    const val MASK = "***REDACTED***"

    /**
     * 脱敏规则：正则 + 可显示前缀提取器。
     * [keep] 返回需要保留的前缀（如 "Authorization: Bearer "），值部分一律替换为掩码。
     */
    private data class Rule(val regex: Regex, val keep: (MatchResult) -> String)

    private val rules: List<Rule> = listOf(
        // DeepSeek / 通用 API Key：sk- 前缀 + 字母数字下划线连字符
        Rule(Regex("""sk-[A-Za-z0-9_\-]{6,}""")) { "" },
        // 鉴权头：Authorization: Bearer xxx 或 Authorization: xxx（保留前缀）
        Rule(Regex("""(?i)(authorization\s*[:=]\s*(?:bearer\s+)?)([^\s,;]+)""")) {
            it.groupValues[1]
        },
        // Cookie 头（保留 "Cookie: " 前缀）
        Rule(Regex("""(?i)(cookie\s*[:=]\s*)([^\r\n;]+)""")) {
            it.groupValues[1]
        },
        // 通用 api_key / apikey 键值（保留键名前缀）
        Rule(Regex("""(?i)(api[_-]?key\s*[:=]\s*)([^\s,;]+)""")) {
            it.groupValues[1]
        },
        // DSH web 访问 token（dsh web 打印的 ?token=... 访问凭据，保留前缀）
        Rule(Regex("""(?i)([?&]token=)([A-Za-z0-9_\-]+)""")) {
            it.groupValues[1]
        },
    )

    /**
     * 对文本执行脱敏，返回脱敏后的新字符串。无命中时返回原内容（不改变引用）。
     */
    fun mask(text: String): String {
        var out = text
        for (rule in rules) {
            out = rule.regex.replace(out) { m -> rule.keep(m) + MASK }
        }
        return out
    }
}
