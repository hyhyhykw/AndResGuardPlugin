package com.xxx.utils;

import org.gradle.api.Project;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * Created time : 2021/3/17 13:26.
 * 项目资源混淆
 * 关于配置 $projectDir/AndResGuard/config.xml   whitelist标签中添加白名单（不需要混淆的id,layout等资源）
 *
 * @author 10585
 */
public class ResGuardUtils {
    //资源混淆目录名称
    private static final String AndResGuard = "AndResGuard";

    private static final String separator = File.separator;
    //安装包输出目录 渠道包另算
    private static final String release = "release";
    //资源混淆解压目录名称
    private static final String app_release = "app-release";
    //复制进入资源混淆工具包的文件名
    private static final String app_release_apk = "app-release.apk";
    //7z压缩的文件
    private static final String signed_apk = app_release + "_signed_aligned.apk";
    //7z压缩并且对其的文件
    private static final String seven_signed_apk = app_release + "_7zip_aligned_signed.apk";

    //混淆完成之后的文件名
    private static final String newApkName = "资源混淆完成v%s-%s-%s.apk";
    //mac资源混淆的命令行指令
    private static final String mac = "sh build_apk.sh app-release.apk";

    private static final SimpleDateFormat sdf = (SimpleDateFormat) SimpleDateFormat.getInstance();

    static {
        sdf.applyPattern("yyyy-MM-dd_HH_mm_ss");
    }

    //不打开cmd窗口运行批处理文件 结束后关闭窗口
//    private static final String win = "cmd /c start /b build_apk.bat";
    private static final String win = "cmd /c start /b build_apk.bat";

    private static final String batFormat =
            "set jdkpath=%s\n" +
                    "set zipalign=%s\n" +
                    "\"%%jdkpath%%\" -jar AndResGuard-cli-1.2.20-fix.jar app-release.apk -config config.xml -out app-release -zipalign \"%%zipalign%%\" -signatureType v2\n" +
                    "exit\n";

    /**
     * 拷贝文件 覆盖
     *
     * @param src    源文件
     * @param target 目标文件
     */
    private static void copyTo(File src, File target) {
        if (!src.exists()) {
            System.out.println("文件拷贝失败");
            return;
        }

        if (target.exists()) {
            if (!target.delete()) {
                System.out.println("旧目标文件无法删除");
                return;
            }
        }
        File parentFile = target.getParentFile();
        if (parentFile == null) {
            System.out.println("目标文件目录无法创建");
            return;
        }
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                System.out.println("目标文件目录无法创建");
                return;
            }
        }
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(src);
            fos = new FileOutputStream(target);
            byte[] bytes = new byte[4096];
            int len;
            while ((len = fis.read(bytes)) != -1) {
                fos.write(bytes, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


    }


    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void delete(File path) {
        if (path.isDirectory()) {
            File[] listFiles = path.listFiles();
            //递归删除目录中的子目录下
            if (null != listFiles) {
                for (File listFile : listFiles) {
                    delete(listFile);
                }
            }
        }
        // 目录此时为空，可以删除
        path.delete();
    }


    //mac和windows共同的资源混淆方法
    public static void guard(Project project,
                             File projectDir,/*项目文件目录*/
                             String versionCode,/*版本号*/
                             String version,/*版本名称*/
                             String platform,/*平台*/
                             String flavor,/*渠道名称 渠道包输出目录不一样*/
                             String sdkPath/*sdk目录*/) {

        //-------------------------------- 先拿到打包完成的路径 ----------------------------------------
        //资源混淆工具路径
        String resGuardDir = projectDir.getAbsolutePath() + separator + AndResGuard;
        //app模块路径
        String appDir = project.getProjectDir().getAbsolutePath();
//        String appDir = projectDir.getAbsolutePath() + separator + app;

        //安装包的输出目录
        String apkFileParentPath;

        if (flavor == null || flavor.isEmpty()) {//没有渠道
            apkFileParentPath = appDir + separator +
                    release + separator;
        } else {//有渠道
            apkFileParentPath = appDir + separator +
                    flavor + separator +
                    release + separator;
        }

        File apkFileParent = new File(apkFileParentPath);
        //找到最新的apk包
        if (!apkFileParent.isDirectory()) {
            System.out.println(apkFileParent.getAbsolutePath() + "这不是一个目录");
            return;
        }
        //找出目录里的apk列表
        File[] files = apkFileParent.listFiles(pathname -> {
            String filename = pathname.getName();

            return filename.endsWith(".apk") && !filename.startsWith("资源混淆完成") && filename.length() > 4;
        });
        if (files == null || files.length == 0) {
            System.out.println("目录里没有apk文件");
            return;
        }
        //找到最新的apk包
        Arrays.sort(files, (o1, o2) -> Long.compare(o2.lastModified(), o1.lastModified()));
        File apkFile = files[0];
        String apkFilePath = apkFile.getAbsolutePath();
        System.out.println("apkFilePath===" + apkFilePath);

        if (!apkFile.exists()) {//二次判断
            System.out.println("文件不存在");
            return;
        }

        if (platform.equals("windows")) {
            //找到最新的sdk tools
            File file = new File(sdkPath + separator + "build-tools");
            File[] buildTools = file.listFiles(File::isDirectory);
            Arrays.sort(buildTools, (o1, o2) -> {
                String name2 = o2.getName();
                String name1 = o1.getName();
                try {
                    int v2 = Integer.parseInt(name2.substring(0, 2));
                    int v1 = Integer.parseInt(name1.substring(0, 2));
                    int compare = Integer.compare(v2, v1);
                    if (compare != 0) {
                        return compare;
                    }
                } catch (NumberFormatException ignore) {
                }
                return name2.compareTo(name1);
            });
            //最新的build tool
            File buildTool = buildTools[0];

            //因为windows系统安装路径不一定相同 需要动态生成批处理文件
            try {
                System.out.println("-----------------------------创建build_apk.bat-------------------------------");
                String zipalign = buildTool.getAbsolutePath() +
                        separator +
                        "zipalign.exe";
                //获取java安装路径
                String jdkPath = System.getProperty("java.home");
                //动态生成build_apk.bat
                File batFile = new File(resGuardDir + separator + "build_apk.bat");
                String bat = String.format(
                        batFormat,
                        jdkPath + separator + "bin" + separator + "java.exe",
                        zipalign
                );
                FileWriter fos = new FileWriter(batFile);
                fos.write(bat);
                fos.flush();
                fos.close();
                System.out.println("-----------------------------创建build_apk.bat结束-------------------------------");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        //--------------------------- 资源混淆 -------------------------------
        //资源解压目录
        File resUnzipPath = new File(resGuardDir + separator + app_release);
        //拷贝的新文件
        File oldApk = new File(resGuardDir + separator + app_release_apk);

        if (resUnzipPath.exists()) {
            //删除旧的资源混淆文件
            delete(resUnzipPath);
//            project.delete(resUnzipPath);
        }

        System.out.println("------------------------------复制映射文件------------------------------");
        String mappingDir = appDir + separator + "build" + separator + "outputs" + separator + "mapping";
        String mappingFilepath;
        if (flavor == null || flavor.isEmpty()) {
            mappingFilepath = mappingDir + separator + "release" + separator + "mapping.txt";
        } else {
            mappingFilepath = mappingDir + separator + flavor + "Release" + separator + "mapping.txt";
        }

        String datetimeFormat = sdf.format(new Date().getTime());
        String mappingName = String.format("mapping-%s-%s-%s.txt", version, versionCode, datetimeFormat);
        File mappingFile = new File(mappingFilepath);
        File file = new File(apkFileParent, mappingName);
        copyTo(mappingFile, file);
        System.out.println("------------------------------复制映射文件结束------------------------------");

        copyTo(apkFile, oldApk);

        //使用命令行进行资源混淆
        Runtime runtime = Runtime.getRuntime();

        try {
            System.out.println("-----------------------------使用平台命令行工具混淆资源-------------------------------");
            String command = platform.equals("windows") ? win : mac;
            Process process = runtime.exec(command, null, new File(resGuardDir));
            //命令行输出
            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            //命令行错误输出
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line = stdoutReader.readLine();

            //打印资源混淆的输出
            boolean show = false;
            while (line != null) {
                if (!show) {
                    System.out.println("OUTPUT");
                    show = true;
                }
                System.out.println(line);

                line = stdoutReader.readLine();
            }

            //打印资源混淆的错误输出
            show = false;
            line = stderrReader.readLine();
            while (line != null) {
                if (!show) {
                    System.out.println("ERROR");
                    show = true;
                }

                System.out.println(line);
                line = stderrReader.readLine();
            }
            //最终运行结果 0表示运行成功
            int exitVal = process.waitFor();
            if (exitVal == 0) {
                System.out.println("-----------------------------混淆资源结束-------------------------------");
                //成功后复制回原来的输出路径
                //7z压缩过的
                File sevenZApk =
                        new File(
                                resUnzipPath.getAbsolutePath() +
                                        File.separator +
                                        seven_signed_apk
                        );
                //7z压缩失败的
                File resGuardApk =
                        new File(
                                resUnzipPath.getAbsolutePath() +
                                        separator +
                                        (platform.equals("windows") ? "app-release_signed.apk" : signed_apk)
                        );

                String pathnameFormat = apkFileParentPath + newApkName;
                String pathname = String.format(pathnameFormat, version, versionCode, datetimeFormat);
                File targetApk = new File(pathname);

                System.out.println("-----------------------------复制混淆文件到原文件夹-------------------------------");
                //复制文件回原目录
                if (sevenZApk.exists()) {
                    copyTo(sevenZApk, targetApk);
                } else if (resGuardApk.exists()) {
                    copyTo(resGuardApk, targetApk);
                }

                System.out.println("-----------------------------删除临时垃圾文件-------------------------------");
                //删除中途产生的垃圾文件
                if (resUnzipPath.exists()) {
                    delete(resUnzipPath);
                }
                if (oldApk.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    oldApk.delete();
                }

                delete(new File(resGuardDir));
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }

}