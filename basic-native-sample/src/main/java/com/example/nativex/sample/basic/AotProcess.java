package com.example.nativex.sample.basic;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.springframework.aot.generator.DefaultGeneratedTypeContext;
import org.springframework.aot.generator.GeneratedType;
import org.springframework.aot.generator.GeneratedTypeReference;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.nativex.FileNativeConfigurationGenerator;
import org.springframework.context.generator.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.JavaFile;
import org.springframework.util.FileSystemUtils;

/**
 * @author Stephane Nicoll
 */
public class AotProcess {

	private final Function<String, ClassName> generatedTypeFactory;

	private final Path generatedSources;

	private final Path generatedResources;

	private final Path targetDirectory;

	private final String groupId;

	private final String artifactId;

	public AotProcess(Builder builder) {
		this.generatedTypeFactory = builder.generatedTypeFactory;
		this.generatedSources = builder.generatedSources;
		this.generatedResources = builder.generatedResources;
		this.targetDirectory = builder.targetDirectory;
		this.groupId = builder.groupId;
		this.artifactId = builder.artifactId;
	}

	public static Builder configure() {
		return new Builder();
	}

	public void run(GenericApplicationContext applicationContext, String packageName) throws IOException {
		DefaultGeneratedTypeContext generationContext = new DefaultGeneratedTypeContext(packageName,
				(targetPackage) -> GeneratedType.of(this.generatedTypeFactory.apply(targetPackage)));
		ApplicationContextAotGenerator generator = new ApplicationContextAotGenerator();
		generator.generateApplicationContext(applicationContext, generationContext);

		// Register reflection hint for entry point as we access it via reflection
		generationContext.runtimeHints().reflection().registerType(
				GeneratedTypeReference.of(generationContext.getMainGeneratedType().getClassName()),
				(hint) -> hint.withConstructor(Collections.emptyList(),
						(constructorHint) -> constructorHint.setModes(ExecutableMode.INVOKE)));

		List<Path> sourceFiles = writeGeneratedSources(generationContext.toJavaFiles());
		compileSourceFiles(sourceFiles);
		writeGeneratedResources(generationContext.runtimeHints());
		// TODO: improve by only copying what was generated.
		FileSystemUtils.copyRecursively(this.generatedResources, this.targetDirectory);
	}

	private List<Path> writeGeneratedSources(List<JavaFile> sources) {
		List<Path> sourceFiles = new ArrayList<>();
		for (JavaFile source : sources) {
			try {
				sourceFiles.add(source.writeToPath(this.generatedSources));
			}
			catch (IOException ex) {
				throw new IllegalStateException("Failed to write " + source.typeSpec.name, ex);
			}
		}
		return sourceFiles;
	}

	private void writeGeneratedResources(RuntimeHints hints) {
		FileNativeConfigurationGenerator generator = new FileNativeConfigurationGenerator(this.generatedResources,
				this.groupId, this.artifactId);
		generator.generate(hints);
	}

	private void compileSourceFiles(List<Path> sourceFiles) throws IOException {
		if (sourceFiles.isEmpty()) {
			return;
		}
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
			List<String> options = List.of("-d", this.targetDirectory.toAbsolutePath().toString());
			Iterable<? extends JavaFileObject> compilationUnits = fm.getJavaFileObjectsFromPaths(sourceFiles);
			Errors errors = new Errors();
			CompilationTask task = compiler.getTask(null, fm, errors, options, null, compilationUnits);
			boolean result = task.call();
			if (!result || errors.hasReportedErrors()) {
				throw new IllegalStateException("Unable to compile source" + errors);
			}
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

		private Function<String, ClassName> generatedTypeFactory;

		private Path generatedSources;

		private Path generatedResources;

		private Path targetDirectory;

		private String groupId;

		private String artifactId;

		public Builder withGeneratedTypeFactory(Function<String, ClassName> generatedTypeFactory) {
			this.generatedTypeFactory = generatedTypeFactory;
			return this;
		}

		public Builder withDefaultGeneratedTypeFactory(Class<?> application) {
			return withGeneratedTypeFactory((packageName) -> ClassName.get(packageName,
					application.getSimpleName() + "__ApplicationContextInitializer"));
		}

		public Builder withMavenBuildConventions() {
			Path target = Paths.get("").resolve("target");
			Path aot = target.resolve("spring-aot/main");
			return withGeneratedSources(aot.resolve("sources")).withGeneratedResources(aot.resolve("resources"))
					.withTargetDirectory(target.resolve("classes"));
		}

		public Builder withGeneratedSources(Path generatedSources) {
			this.generatedSources = generatedSources;
			return this;
		}

		public Builder withGeneratedResources(Path generatedResources) {
			this.generatedResources = generatedResources;
			return this;
		}

		public Builder withTargetDirectory(Path targetDirectory) {
			this.targetDirectory = targetDirectory;
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
