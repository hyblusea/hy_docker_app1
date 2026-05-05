package com.tradingx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

@Slf4j
@Component
public class StrategyCompiler {

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)");

    private volatile String cachedClasspath = null;
    private volatile boolean extracted = false;

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

        String classpath = buildClasspath();
        log.debug("Compile classpath length: {}", classpath.length());
        List<String> options = Arrays.asList("-classpath", classpath);
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
        if (cachedClasspath != null) {
            return cachedClasspath;
        }

        synchronized (this) {
            if (cachedClasspath != null) {
                return cachedClasspath;
            }

            String javaClassPath = System.getProperty("java.class.path");
            log.info("Original java.class.path: {}", javaClassPath != null ? javaClassPath.substring(0, Math.min(javaClassPath.length(), 200)) : "null");

            Set<String> classpathEntries = new LinkedHashSet<>();

            if (javaClassPath != null && !javaClassPath.isEmpty()) {
                String separator = System.getProperty("path.separator");
                for (String entry : javaClassPath.split(separator)) {
                    if (!entry.isBlank()) {
                        classpathEntries.add(entry.trim());
                    }
                }
            }

            addRuntimeClasspath(classpathEntries);

            String result = String.join(System.getProperty("path.separator"), classpathEntries);
            log.info("Built classpath with {} entries, total length: {}", classpathEntries.size(), result.length());
            cachedClasspath = result;
            return result;
        }
    }

    private void addRuntimeClasspath(Set<String> classpathEntries) {
        addDockerDependencyJars(classpathEntries);

        if (!extracted) {
            extractFatJarDependencies(classpathEntries);
        }

        addClassLoaderUrls(classpathEntries);

        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            classpathEntries.add(javaHome + "/lib");
        }
    }

    private void addDockerDependencyJars(Set<String> classpathEntries) {
        String[] jarDirs = {"/app/dependency-jars", "/app/lib", "/app/jars"};
        for (String dir : jarDirs) {
            File jarDir = new File(dir);
            if (jarDir.isDirectory()) {
                File[] jars = jarDir.listFiles((d, name) -> name.endsWith(".jar"));
                if (jars != null && jars.length > 0) {
                    log.info("Found {} JAR files in {}", jars.length, dir);
                    for (File jar : jars) {
                        classpathEntries.add(jar.getAbsolutePath());
                    }
                }
            }
        }
    }

    private void extractFatJarDependencies(Set<String> classpathEntries) {
        String javaClassPath = System.getProperty("java.class.path");
        if (javaClassPath == null || !javaClassPath.endsWith(".jar")) {
            return;
        }

        File fatJar = new File(javaClassPath);
        if (!fatJar.isFile()) {
            return;
        }

        File extractDir = new File(fatJar.getParentFile(), "dependency-jars");
        if (extractDir.isDirectory()) {
            File[] existing = extractDir.listFiles((d, name) -> name.endsWith(".jar"));
            if (existing != null && existing.length > 5) {
                log.info("Using existing extracted JARs in {}", extractDir.getAbsolutePath());
                for (File jar : existing) {
                    classpathEntries.add(jar.getAbsolutePath());
                }
                extracted = true;
                return;
            }
        }

        try {
            extractDir.mkdirs();
            int count = extractBootInfLib(fatJar, extractDir);
            log.info("Extracted {} JAR files from {} to {}", count, fatJar.getName(), extractDir.getAbsolutePath());

            File[] jars = extractDir.listFiles((d, name) -> name.endsWith(".jar"));
            if (jars != null) {
                for (File jar : jars) {
                    classpathEntries.add(jar.getAbsolutePath());
                }
            }
            extracted = true;
        } catch (Exception e) {
            log.error("Failed to extract dependencies from fat jar: {}", e.getMessage(), e);
        }
    }

    private int extractBootInfLib(File fatJar, File targetDir) throws IOException {
        int count = 0;
        try (JarFile jarFile = new JarFile(fatJar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar")) {
                    String jarName = name.substring(name.lastIndexOf('/') + 1);
                    File outFile = new File(targetDir, jarName);
                    if (!outFile.exists()) {
                        try (InputStream is = jarFile.getInputStream(entry);
                             OutputStream os = new FileOutputStream(outFile)) {
                            is.transferTo(os);
                        }
                    }
                    count++;
                }
            }
        }
        return count;
    }

    private void addClassLoaderUrls(Set<String> classpathEntries) {
        ClassLoader cl = getClass().getClassLoader();
        Set<URL> urls = new LinkedHashSet<>();

        while (cl != null) {
            if (cl instanceof URLClassLoader) {
                try {
                    URL[] loaderUrls = ((URLClassLoader) cl).getURLs();
                    if (loaderUrls != null) {
                        urls.addAll(Arrays.asList(loaderUrls));
                    }
                } catch (Exception e) {
                    log.debug("Failed to get URLs from classloader: {}", cl.getClass().getName());
                }
            }
            cl = cl.getParent();
        }

        for (URL url : urls) {
            String path = url.getPath();
            if (path.startsWith("file:")) {
                path = path.substring(5);
            }
            if (path.contains("!") && path.contains(".jar")) {
                int bangIndex = path.indexOf("!");
                path = path.substring(0, bangIndex);
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
            }
            if (!path.isBlank()) {
                classpathEntries.add(path);
            }
        }
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
