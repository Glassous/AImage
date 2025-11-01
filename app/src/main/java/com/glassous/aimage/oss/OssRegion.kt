package com.glassous.aimage.oss

/**
 * 阿里云OSS中国大陆地域与外网Endpoint映射。
 * 数据来源：官方文档《地域和Endpoint》
 * https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints
 */
enum class OssRegion(
    val displayName: String,
    val regionId: String,
    val endpoint: String
) {
    HANGZHOU("华东1（杭州）", "cn-hangzhou", "oss-cn-hangzhou.aliyuncs.com"),
    SHANGHAI("华东2（上海）", "cn-shanghai", "oss-cn-shanghai.aliyuncs.com"),
    QINGDAO("华北1（青岛）", "cn-qingdao", "oss-cn-qingdao.aliyuncs.com"),
    BEIJING("华北2（北京）", "cn-beijing", "oss-cn-beijing.aliyuncs.com"),
    ZHANGJIAKOU("华北3（张家口）", "cn-zhangjiakou", "oss-cn-zhangjiakou.aliyuncs.com"),
    HUHEHAOTE("华北5（呼和浩特）", "cn-huhehaote", "oss-cn-huhehaote.aliyuncs.com"),
    WULANCHABU("华北6（乌兰察布）", "cn-wulanchabu", "oss-cn-wulanchabu.aliyuncs.com"),
    SHENZHEN("华南1（深圳）", "cn-shenzhen", "oss-cn-shenzhen.aliyuncs.com"),
    HEYUAN("华南2（河源）", "cn-heyuan", "oss-cn-heyuan.aliyuncs.com"),
    GUANGZHOU("华南3（广州）", "cn-guangzhou", "oss-cn-guangzhou.aliyuncs.com"),
    CHENGDU("西南1（成都）", "cn-chengdu", "oss-cn-chengdu.aliyuncs.com");

    companion object {
        fun fromRegionId(id: String?): OssRegion? = values().find { it.regionId == id }
    }
}