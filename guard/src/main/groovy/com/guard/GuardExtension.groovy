package com.guard

class GuardExtension {

    String pkgName = ""
    boolean whiteActive = true
    Iterable<String> whiteList = []

    boolean sevenZip = true
    String metaName = "META-INF"
    boolean keepRoot = true

    boolean mappingActive = false
    Iterable<String> mappingFiles = []
    boolean compressActive = true
    Iterable<String> compress = []

    boolean sign = true
    String signPath = ""
    String storepass = ""
    String keyAlias = ""
    String keypass = ""

}


