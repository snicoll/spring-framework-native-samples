package com.example.nativex.sample.basic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.FileSystemGeneratedFiles;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.hint.ExecutableHint;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.nativex.FileNativeConfigurationWriter;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;
import org.springframework.util.FileSystemUtils;

/**
 * @author Stephane Nicoll
 */
public class AotProcess {

	private static final Consumer<ExecutableHint.Builder> INVOKE_CONSTRUCTOR_HINT = (hint) -> hint
			.setModes(ExecutableMode.INVOKE);

	private final Class<?> application;

	private final Path sourceOutput;

	private final Path resourceOutput;

	private final Path classOutput;

	private final String groupId;

	private final String artifactId;

	public AotProcess(Builder builder) {
		this.application = builder.application;
		this.sourceOutput = builder.sourceOutput;
		this.resourceOutput = builder.resourceOutput;
		this.classOutput = builder.classOutput;
		this.groupId = builder.groupId;
		this.artifactId = builder.artifactId;
	}

	public static Builder configure() {
		return new Builder();
	}

	public void performAotProcessing(GenericApplicationContext applicationContext) throws IOException {
		FileSystemGeneratedFiles generatedFiles = new FileSystemGeneratedFiles(this::getRoot);
		DefaultGenerationContext generationContext = new DefaultGenerationContext(generatedFiles);
		ApplicationContextAotGenerator generator = new ApplicationContextAotGenerator();
		ClassName generatedInitializerClassName = generationContext.getClassNameGenerator()
				.generateClassName(this.application, "ApplicationContextInitializer");
		generator.generateApplicationContext(applicationContext, generationContext, generatedInitializerClassName);
		registerEntryPointHint(generationContext, generatedInitializerClassName);
		generationContext.writeGeneratedContent();
		writeHints(generationContext.getRuntimeHints());
		writeNativeImageProperties();
		compileSourceFiles();
		copyResources();
	}

	private Path getRoot(Kind kind) {
		return switch (kind) {
		case SOURCE -> this.sourceOutput;
		case RESOURCE -> this.resourceOutput;
		case CLASS -> this.classOutput;
		};
	}

	private void registerEntryPointHint(DefaultGenerationContext generationContext,
			ClassName generatedInitializerClassName) {
		TypeReference generatedType = TypeReference.of(generatedInitializerClassName.canonicalName());
		TypeReference applicationType = TypeReference.of(this.application);
		ReflectionHints reflection = generationContext.getRuntimeHints().reflection();
		reflection.registerType(applicationType, (hint) -> {
		});
		reflection.registerType(generatedType, (hint) -> hint.onReachableType(applicationType)
				.withConstructor(Collections.emptyList(), INVOKE_CONSTRUCTOR_HINT));
	}

	private void compileSourceFiles() throws IOException {
		List<Path> sourceFiles = Files.walk(this.sourceOutput).filter(Files::isRegularFile).toList();
		if (sourceFiles.isEmpty()) {
			return;
		}
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
			List<String> options = List.of("-d", this.classOutput.toAbsolutePath().toString());
			Iterable<? extends JavaFileObject> compilationUnits = fm.getJavaFileObjectsFromPaths(sourceFiles);
			Errors errors = new Errors();
			CompilationTask task = compiler.getTask(null, fm, errors, options, null, compilationUnits);
			boolean result = task.call();
			if (!result || errors.hasReportedErrors()) {
				throw new IllegalStateException("Unable to compile source" + errors);
			}
		}
	}

	private void copyResources() throws IOException {
		FileSystemUtils.copyRecursively(this.resourceOutput, this.classOutput);
	}

	private void writeHints(RuntimeHints hints) {
		FileNativeConfigurationWriter writer = new FileNativeConfigurationWriter(this.resourceOutput, this.groupId,
				this.artifactId);
		writer.write(hints);
	}

	private void writeNativeImageProperties() {
		List<String> args = new ArrayList<>();
		args.add("-H:Class=" + this.application.getName());
		args.add("--allow-incomplete-classpath");
		args.add("--report-unsupported-elements-at-runtime");
		args.add("--no-fallback");
		args.add("--install-exit-handlers");
		StringBuilder sb = new StringBuilder();
		sb.append("Args = ");
		sb.append(String.join(String.format(" \\%n"), args));
		Path file = this.resourceOutput
				.resolve("META-INF/native-image/" + this.groupId + "/" + this.artifactId + "/native-image.properties");
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.createFile(file);
			}
			Files.writeString(file, sb.toString());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write native-image properties", ex);
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

		private String groupId;

		private String artifactId;

		public Builder withApplication(Class<?> application) {
			this.application = application;
			return this;
		}

		public Builder withMavenBuildConventions() {
			Path target = Paths.get("").resolve("target");
			Path aot = target.resolve("spring-aot/main");
			return withSourceOutput(aot.resolve("sources")).withResourceOutput(aot.resolve("resources"))
					.withClassOutput(target.resolve("classes"));
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
