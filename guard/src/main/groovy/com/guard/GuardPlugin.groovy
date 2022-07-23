package com.guard

import com.xxx.utils.ResGuardUtils
import com.xxx.utils.ZipUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.util.regex.Pattern

class GuardPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        GuardExtension ext = project.extensions.create("guard", GuardExtension.class)
        //通过正则表达式获取判断是否为打包任务
        //没有渠道包时 assembleRelease 就是打包任务
        //有渠道时 assemble渠道名称首字母大写Release 就是打包任务
        Pattern pattern = Pattern.compile("assemble(|[A-Z0-9][a-zA-Z0-9]*)Release")
        //获取系统名称 判断是windows还是mac linux暂时不用
        def sys = System.getProperty("os.name").toLowerCase()
        //整个项目的目录
        def rootProject = project.projectDir.parentFile

        project.afterEvaluate {
            project.plugins.withId("com.android.application") {

                project.tasks.each { Task task ->
                    def taskName = task.name
                    //获取当前运行的任务名称 并且进行匹配
                    def matcher = pattern.matcher(taskName)
                    //如果是打包任务 就继续
                    if (matcher.matches()) {
                        def versionName = project.android.defaultConfig.versionName
                        def versionCode = "" + project.android.defaultConfig.versionCode
                        def flavorUpper = taskName.replace("assemble", "")
                                .replace("Release", "")
                        def flavor = flavorUpper.toLowerCase()

                        def releaseTask = project.tasks.findByName(taskName)

                        if (sys.startsWith("mac")) {
                            releaseTask.doLast {
                                upzipGuardDir(project)
                                createConfig(ext, rootProject)

                                //资源混淆
                                ResGuardUtils.guard(project, rootProject, versionCode, versionName, "mac", flavor, null)
                            }
                        } else if (sys.contains("windows")) {
                            releaseTask.doLast {
                                upzipGuardDir(project)
                                createConfig(ext, rootProject)

                                //sdk目录
                                def sdkFolder = project.android.sdkDirectory.absolutePath
                                //资源混淆
                                ResGuardUtils.guard(project, rootProject, versionCode, versionName, "windows", flavor, sdkFolder)
                            }
                        } else if (sys.contains("linux")) {//todo linux 脚本和mac脚本应该一样
                            upzipGuardDir(project)
                            createConfig(ext, rootProject)

                            //资源混淆
                            ResGuardUtils.guard(project, rootProject, versionCode, versionName, "mac", flavor, null)
                        }
                    }
                }
            }
        }
    }

    void upzipGuardDir(Project project) {
        def resource = getClass().getResource("/AndResGuard.zip")
        def connection = resource.openConnection()
        connection.connect()

        def stream = connection.getInputStream()
        ZipUtils.unzip(stream, project.projectDir.parentFile.absolutePath)
    }

    static void createConfig(GuardExtension ext, File rootProject) {
        def factory = DocumentBuilderFactory.newInstance()
        def db = factory.newDocumentBuilder()
        def document = db.newDocument()
        document.setXmlStandalone(true)

        //根节点
        def resproguard = document.createElement("resproguard")

        ///////////////////////////添加property开始////////////////////////////////
        //property
        def propertyIssue = document.createElement("issue")
        propertyIssue.setAttribute("id", "property")

        //seventzip
        def seventzip = document.createElement("seventzip")
        seventzip.setAttribute("value", String.valueOf(ext.sevenZip))
        propertyIssue.appendChild(seventzip)

        //metaname
        def metaName = document.createElement("metaname")
        metaName.setAttribute("value", ext.metaName)
        propertyIssue.appendChild(metaName)

        //metaname
        def keepRoot = document.createElement("keeproot")
        keepRoot.setAttribute("value", String.valueOf(ext.keepRoot))
        propertyIssue.appendChild(keepRoot)

        resproguard.appendChild(propertyIssue)
        ///////////////////////////添加property结束////////////////////////////////

        ///////////////////////////添加whiteList开始////////////////////////////////
        //whitelist
        def whiteIssue = document.createElement("issue")
        whiteIssue.setAttribute("id", "whitelist")
        whiteIssue.setAttribute("isactive", String.valueOf(ext.whiteActive))

        ext.whiteList.each {
            def element = document.createElement("path")
            element.setAttribute("value", ext.pkgName + "." + it)
            whiteIssue.appendChild(element)
        }
        resproguard.appendChild(whiteIssue)
        ///////////////////////////添加whiteList结束////////////////////////////////

        ///////////////////////////添加keepmapping开始////////////////////////////////
        def mappingIssue = document.createElement("issue")
        mappingIssue.setAttribute("id", "keepmapping")
        mappingIssue.setAttribute("isactive", String.valueOf(ext.mappingActive))

        ext.mappingFiles.each {
            def element = document.createElement("path")
            element.setAttribute("value", it)
            mappingIssue.appendChild(element)
        }
        resproguard.appendChild(mappingIssue)
        ///////////////////////////添加keepmapping结束////////////////////////////////

        ///////////////////////////添加compress开始////////////////////////////////
        def compressIssue = document.createElement("issue")
        compressIssue.setAttribute("id", "compress")
        compressIssue.setAttribute("isactive", String.valueOf(ext.compressActive))

        ext.compress.each {
            def element = document.createElement("path")
            element.setAttribute("value", it)
            compressIssue.appendChild(element)
        }
        resproguard.appendChild(compressIssue)
        ///////////////////////////添加compress结束////////////////////////////////

        ///////////////////////////添加sign开始////////////////////////////////
        def signIssue = document.createElement("issue")
        signIssue.setAttribute("id", "sign")
        signIssue.setAttribute("isactive", String.valueOf(ext.sign))

        def signPath = document.createElement("path")
        signPath.setAttribute("value", String.valueOf(ext.signPath))
        signIssue.appendChild(signPath)

        def storepass = document.createElement("storepass")
        storepass.setAttribute("value", String.valueOf(ext.storepass))
        signIssue.appendChild(storepass)

        def keypass = document.createElement("keypass")
        keypass.setAttribute("value", String.valueOf(ext.keypass))
        signIssue.appendChild(keypass)

        def alias = document.createElement("alias")
        alias.setAttribute("value", String.valueOf(ext.keyAlias))
        signIssue.appendChild(alias)

        resproguard.appendChild(signIssue)
        ///////////////////////////添加sign结束////////////////////////////////

        document.appendChild(resproguard)

        // 创建TransformerFactory对象
        def tff = TransformerFactory.newInstance()
        // 创建 Transformer对象
        def tf = tff.newTransformer()

        // 输出内容是否使用换行
        tf.setOutputProperty(OutputKeys.INDENT, "yes")
//        tf.setOutputProperty(OutputPropertiesFactory.S_KEY_INDENT_AMOUNT, "4")
        tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")

        println "rootProject===>" + rootProject
        def configFile = new StringBuilder()
                .append(rootProject.absolutePath)
                .append(File.separator)
                .append("AndResGuard")
                .append(File.separator)
                .append("config.xml")

        def file = new File(configFile.toString())
        if (!file.exists()) {
            file.createNewFile()
        }
        // 创建xml文件并写入内容
        tf.transform(new DOMSource(document), new StreamResult(file))
    }

}

