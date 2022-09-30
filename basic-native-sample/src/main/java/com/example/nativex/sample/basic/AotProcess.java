package com.example.nativex.sample.basic;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.springframework.context.aot.AotProcessor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.ReflectionUtils;

/**
 * @author Stephane Nicoll
 */
public class AotProcess extends AotProcessor {

	private final Path sourceOutput;

	private final Path resourceOutput;

	private final Path classOutput;

	private final Path classpathDir;

	public AotProcess(Builder builder) {
		super(builder.application, builder.sourceOutput, builder.resourceOutput, builder.classOutput, builder.groupId,
				builder.artifactId);
		this.sourceOutput = builder.sourceOutput;
		this.resourceOutput = builder.resourceOutput;
		this.classOutput = builder.classOutput;
		this.classpathDir = builder.classpathDir;
	}

	public static Builder configure() {
		return new Builder();
	}

	@Override
	public ClassName process() {
		ClassName className = super.process();
		compileAndCopyAssets();
		return className;
	}

	@Override
	protected GenericApplicationContext prepareApplicationContext(Class<?> application) {
		Method method = ReflectionUtils.findMethod(application, "prepareApplicationContext");
		if (method == null) {
			throw new IllegalArgumentException(
					"Expected a prepareApplicationContext() method on " + application.getName());
		}
		ReflectionUtils.makeAccessible(method);
		return (GenericApplicationContext) ReflectionUtils.invokeMethod(method, null);
	}

	private void compileAndCopyAssets() {
		try {
			compileSourceFiles();
			copyToClasspathDir(this.resourceOutput);
			copyToClasspathDir(this.classOutput);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to process assets", ex);
		}
	}

	private void compileSourceFiles() throws IOException {
		List<Path> sourceFiles = Files.walk(this.sourceOutput).filter(Files::isRegularFile).toList();
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

	public static class Builder {

		private Class<?> application;

		private Path sourceOutput;

		private Path resourceOutput;

		private Path classOutput;

		private Path classpathDir;

		private String groupId;

		private String artifactId;

		public Builder withApplication(Class<?> application) {
			this.application = application;
			return this;
		}

		public Builder withMavenBuildConventions() {
			Path target = Paths.get("").resolve("target");
			Path aot = target.resolve("spring-aot").resolve("main");
			return withSourceOutput(aot.resolve("sources")).withResourceOutput(aot.resolve("resources"))
					.withClassOutput(aot.resolve("classes")).withClasspathDir(target.resolve("classes"));
		}

		public Builder withSourceOutput(Path sourceOutput) {
			this.sourceOutput = sourceOutput;
			return this;
		}

		public Builder withResourceOutput(Path resourceOutput) {
			this.resourceOutput = resourceOutput;
			return this;
		}

		public Builder withClassOutput(Path classOutput) {
			this.classOutput = classOutput;
			return this;
		}

		public Builder withClasspathDir(Path classpathDir) {
			this.classpathDir = classpathDir;
			return this;
		}

		public Builder withProjectId(String groupId, String artifactId) {
			this.groupId = groupId;
			this.artifactId = artifactId;
			return this;
		}

		public AotProcess build() {
			return new AotProcess(this);
		}

	}

}
