package com.sovworks.eds.fs.encfs

interface AlgInfo {
    fun select(config: Config?): AlgInfo?
    val name: String
    val descr: String?
    val version1: Int
    val version2: Int
}
