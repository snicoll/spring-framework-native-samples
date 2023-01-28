package com.example.nativex.sample.basic;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.springframework.context.aot.ContextAotProcessor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.ReflectionUtils;

/**
 * @author Stephane Nicoll
 */
public class AotProcess extends ContextAotProcessor {

	private final Path classpathDir;

	protected AotProcess(Class<?> applicationClass, Settings settings, Path classpathDir) {
		super(applicationClass, settings);
		this.classpathDir = classpathDir;
	}

	@Override
	protected GenericApplicationContext prepareApplicationContext(Class<?> applicationClass) {
		Method method = ReflectionUtils.findMethod(applicationClass, "prepareApplicationContext");
		if (method == null) {
			throw new IllegalArgumentException(
					"Expected a prepareApplicationContext() method on " + applicationClass.getName());
		}
		ReflectionUtils.makeAccessible(method);
		return (GenericApplicationContext) ReflectionUtils.invokeMethod(method, null);
	}

	@Override
	protected ClassName doProcess() {
		ClassName className = super.doProcess();
		try {
			compileSourceFiles();
			copyToClasspathDir(getSettings().getResourceOutput());
			copyToClasspathDir(getSettings().getClassOutput());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to process assets", ex);
		}
		return className;
	}

	private void compileSourceFiles() throws IOException {
		List<Path> sourceFiles = Files.walk(getSettings().getSourceOutput()).filter(Files::isRegularFile).toList();
		if (sourceFiles.isEmpty()) {
			return;
		}
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
			List<String> options = List.of("-d", this.classpathDir.toAbsolutePath().toString());
			Iterable<? extends JavaFileObject> compilationUnits = fm.getJavaFileObjectsFromPaths(sourceFiles);
			Errors errors = new Errors();
			CompilationTask task = compiler.getTask(null, fm, errors, options, null, compilationUnits);
			boolean result = task.call();
			if (!result || errors.hasReportedErrors()) {
				throw new IllegalStateException("Unable to compile source" + errors);
			}
		}
	}

	private void copyToClasspathDir(Path directory) throws IOException {
		if (Files.exists(directory) && Files.isDirectory(directory)) {
			FileSystemUtils.copyRecursively(directory, this.classpathDir);
		}
	}

	/**
	 * {@link DiagnosticListener} used to collect errors.
	 */
	static class Errors implements DiagnosticListener<JavaFileObject> {

		private final StringBuilder message = new StringBuilder();

		@Override
		public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
			if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
				this.message.append("\n");
				this.message.append(diagnostic.getMessage(Locale.getDefault()));
				this.message.append(" ");
				this.message.append(diagnostic.getSource().getName());
				this.message.append(" ");
				this.message.append(diagnostic.getLineNumber()).append(":").append(diagnostic.getColumnNumber());
			}
		}

		boolean hasReportedErrors() {
			return this.message.length() > 0;
		}

		@Override
		public String toString() {
			return this.message.toString();
		}

	}

}
