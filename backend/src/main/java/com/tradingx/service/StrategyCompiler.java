package com.tradingx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class StrategyCompiler {

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)");

    public Strategy compileAndRun(String sourceCode, BarSeries series) {
        String className = extractClassName(sourceCode);
        if (className == null) {
            throw new IllegalArgumentException("无法从代码中提取类名，请确保代码包含 public class XXX");
        }

        String packageName = extractPackageName(sourceCode);
        String fullClassName = (packageName != null && !packageName.isEmpty())
                ? packageName + "." + className
                : className;

        try {
            Class<?> clazz = compile(fullClassName, className, sourceCode);

            try {
                Method method = clazz.getMethod("buildStrategy", BarSeries.class);
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    return (Strategy) method.invoke(null, series);
                }
                Object instance = clazz.getDeclaredConstructor().newInstance();
                Object result = method.invoke(instance, series);
                if (result instanceof Strategy) {
                    return (Strategy) result;
                }
                throw new IllegalStateException("buildStrategy 方法返回值类型不是 org.ta4j.core.Strategy");
            } catch (NoSuchMethodException e) {
                try {
                    Constructor<?> ctor = clazz.getConstructor(BarSeries.class);
                    Object instance = ctor.newInstance(series);
                    if (instance instanceof Strategy) {
                        return (Strategy) instance;
                    }
                    throw new IllegalStateException("构造函数创建的对象不是 org.ta4j.core.Strategy 类型");
                } catch (NoSuchMethodException e2) {
                    throw new IllegalArgumentException("策略类必须包含 public Strategy buildStrategy(BarSeries series) 方法，或带有 BarSeries 参数的构造函数");
                }
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("策略编译或执行失败", e);
            throw new RuntimeException("策略执行失败: " + e.getMessage(), e);
        }
    }

    public String compileCheck(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return "策略代码为空";
        }
        String className = extractClassName(sourceCode);
        if (className == null) {
            return "无法从代码中提取类名，请确保代码包含 public class XXX";
        }
        String packageName = extractPackageName(sourceCode);
        String fullClassName = (packageName != null && !packageName.isEmpty())
                ? packageName + "." + className
                : className;
        try {
            Class<?> clazz = compile(fullClassName, className, sourceCode);
            boolean hasBuildStrategy = false;
            boolean hasBarSeriesCtor = false;
            try {
                clazz.getMethod("buildStrategy", BarSeries.class);
                hasBuildStrategy = true;
            } catch (NoSuchMethodException ignored) {
            }
            try {
                clazz.getConstructor(BarSeries.class);
                hasBarSeriesCtor = true;
            } catch (NoSuchMethodException ignored) {
            }
            if (!hasBuildStrategy && !hasBarSeriesCtor) {
                return "策略类必须包含 public Strategy buildStrategy(BarSeries series) 方法，或带有 BarSeries 参数的构造函数（需继承 BaseStrategy）";
            }
            return null;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = e.getClass().getSimpleName();
            }
            return msg;
        }
    }

    private String extractClassName(String sourceCode) {
        Matcher matcher = CLASS_NAME_PATTERN.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractPackageName(String sourceCode) {
        Pattern pattern = Pattern.compile("package\\s+([\\w.]+)\\s*;");
        Matcher matcher = pattern.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private Class<?> compile(String fullClassName, String simpleClassName, String sourceCode) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("系统未安装 JDK，无法动态编译策略代码");
        }

        JavaFileObject fileObject = new JavaSourceFileObject(simpleClassName, sourceCode);
        JavaFileManager standardFileManager = compiler.getStandardFileManager(null, null, null);

        Map<String, ByteArrayOutputStream> classBytesMap = new HashMap<>();
        JavaFileManager fileManager = new ForwardingJavaFileManager<JavaFileManager>(standardFileManager) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                classBytesMap.put(className, baos);
                return new JavaClassFileObject(className, kind, baos);
            }
        };

        List<String> options = Arrays.asList("-classpath", buildClasspath());
        StringWriter writer = new StringWriter();
        var task = compiler.getTask(writer, fileManager, null, options, null, Collections.singletonList(fileObject));

        boolean success = task.call();
        if (!success) {
            throw new IllegalArgumentException("策略代码编译失败:\n" + writer);
        }

        ClassLoader classLoader = new ClassLoader(this.getClass().getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                ByteArrayOutputStream baos = classBytesMap.get(name);
                if (baos != null) {
                    byte[] bytes = baos.toByteArray();
                    return defineClass(name, bytes, 0, bytes.length);
                }
                return super.findClass(name);
            }
        };

        return classLoader.loadClass(fullClassName);
    }

    private String buildClasspath() {
        String javaClassPath = System.getProperty("java.class.path");
        if (javaClassPath != null && !javaClassPath.isEmpty()) {
            return javaClassPath;
        }
        return "";
    }

    private static class JavaSourceFileObject extends SimpleJavaFileObject {
        private final String sourceCode;

        JavaSourceFileObject(String className, String sourceCode) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }

    private static class JavaClassFileObject extends SimpleJavaFileObject {
        private final OutputStream outputStream;

        JavaClassFileObject(String className, Kind kind, OutputStream outputStream) {
            super(URI.create("bytes:///" + className.replace('.', '/') + kind.extension), kind);
            this.outputStream = outputStream;
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }
    }
}
