package org.example.util;

import jadx.api.*;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.instructions.args.CodeVar;
import jadx.core.dex.instructions.args.SSAVar;


import java.io.*;
import java.util.*;

import java.util.stream.Collectors;



public class MainUtils {
    public static void mainLog(String logType, String logName, Object log) {
        System.out.println("[" + logType + "](" + logName + "): " + log.toString());
    }

    public static String transferMethodSignature(String mthSign) {
        MethodSignature methodSignature = parseMethodSignature(mthSign);
        String clsName = methodSignature.getClassName();
        String mthName = methodSignature.getMethodName();
        String params = String.join(", ", methodSignature.getParamTypes());
        String returnType = methodSignature.getReturnType();
        return clsName + "." + mthName + "(" + params + "):" + returnType;
    }


    public static String getMethodSignature(JavaMethod meth) {
        String mthName = meth.getName().equals("<init>") ? meth.getFullName().split("\\.")[meth.getFullName().split("\\.").length - 2] : meth.getName();
        return "<" + meth.getMethodNode().getMethodInfo().getDeclClass().getFullName() +
                ": " + meth.getReturnType().toString() +
                " " + mthName +
                "(" + meth.getArguments().stream().map(ArgType::toString).collect(Collectors.joining(",")) +
                ")>";
    }

    public static String getMethodDescription(JavaMethod meth) {
        String args = meth.getMethodNode().getSVars().stream()
                .filter(s -> !(s.getName() == null))
                .filter(s -> !s.getName().equals("this")) // 过滤掉this和null
                .map(SSAVar::getCodeVar)
                .map(CodeVar::toString)
                .collect(Collectors.joining(", "));

        return meth.getReturnType().toString() + " " + meth.getName() + "(" + args + ")";
    }

    public static String getFieldDescription(JavaField field) {
        return field.getType() + " " + field.getName();
    }

    public static void saveObj(Object obj, String outputPath) {
        try {
            File newFile = new File(outputPath);
            File parentDir = newFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            FileOutputStream fos = new FileOutputStream(newFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(obj);
            oos.close();
            fos.close();
        } catch (IOException e) {
            mainLog("ERROR", "SaveObj", e);
        }
    }


    public static MethodSignature parseMethodSignature(String methodSignature) {
        String className = methodSignature.split(":")[0].substring(1);
        String returnType = methodSignature.split(":")[1].split(" ")[1];
        String methodName = methodSignature.split(" ")[2].split("\\(")[0];
        List<String> paramTypes = Arrays.stream(methodSignature.split("\\(")[1].replace(")>", "").split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        return new MethodSignature(className, returnType, methodName, paramTypes);
    }

    public static String getAndroidManifest(JadxDecompiler decompiler) {
        for (ResourceFile resourceFile : decompiler.getResources()) {
            if ("AndroidManifest.xml".equals(resourceFile.getOriginalName())) {
                return resourceFile.loadContent().getText().toString();
            }
        }
        return null;
    }

    public static void saveToFile(String path, String str) {
        try {
            File file = new File(path);
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            FileWriter fileWriter = new FileWriter(path, true); // 指定文件路径
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.println(str);
            printWriter.close();
        } catch (Exception e) {
            mainLog("ERROR", "saveToFile", e);
        }
    }

    public static class MethodSignature {
        private String className;
        private String returnType;
        private String methodName;
        private List<String> paramTypes;

        public MethodSignature(String className, String returnType, String methodName, List<String> paramTypes) {
            this.className = className;
            this.returnType = returnType;
            this.methodName = methodName;
            this.paramTypes = paramTypes;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getReturnType() {
            return returnType;
        }

        public void setReturnType(String returnType) {
            this.returnType = returnType;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public List<String> getParamTypes() {
            return paramTypes;
        }

        public void setParamTypes(List<String> paramTypes) {
            this.paramTypes = paramTypes;
        }
    }
}
