package com.example.nativex.sample.basic;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

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

/**
 * @author Stephane Nicoll
 */
public class AotProcess {

	private final Function<String, ClassName> generatedTypeFactory;

	private final Path generatedSources;

	private final Path generatedResources;

	private final Path targetDirectory;

	public AotProcess(Builder builder) {
		this.generatedTypeFactory = builder.generatedTypeFactory;
		this.generatedSources = builder.generatedSources;
		this.generatedResources = builder.generatedResources;
		this.targetDirectory = builder.targetDirectory;
	}

	public static Builder ofNamingStrategy(Function<String, ClassName> generatedTypeFactory) {
		return new Builder(generatedTypeFactory);
	}

	public void run(GenericApplicationContext applicationContext, String packageName) {
		DefaultGeneratedTypeContext generationContext = new DefaultGeneratedTypeContext(packageName,
				(targetPackage) -> GeneratedType.of(this.generatedTypeFactory.apply(targetPackage)));
		ApplicationContextAotGenerator generator = new ApplicationContextAotGenerator();
		generator.generateApplicationContext(applicationContext, generationContext);

		// Register reflection hint for entry point as we access it via reflection
		generationContext.runtimeHints().reflection().registerType(
				GeneratedTypeReference.of(generationContext.getMainGeneratedType().getClassName()),
				(hint) -> hint.withConstructor(Collections.emptyList(),
						(constructorHint) -> constructorHint.setModes(ExecutableMode.INVOKE)));

		writeGeneratedSources(generationContext.toJavaFiles());
		writeGeneratedResources(generationContext.runtimeHints());
	}

	private void writeGeneratedSources(List<JavaFile> sources) {
		for (JavaFile source : sources) {
			try {
				source.writeTo(this.generatedSources);
			}
			catch (IOException ex) {
				throw new IllegalStateException("Failed to write " + source.typeSpec.name, ex);
			}
		}
	}

	private void writeGeneratedResources(RuntimeHints hints) {
		FileNativeConfigurationGenerator generator = new FileNativeConfigurationGenerator(this.generatedResources);
		generator.generate(hints);
	}

	public static class Builder {

		private final Function<String, ClassName> generatedTypeFactory;

		private Path generatedSources;

		private Path generatedResources;

		private Path targetDirectory;

		public Builder(Function<String, ClassName> generatedTypeFactory) {
			this.generatedTypeFactory = generatedTypeFactory;
		}

		public Builder withMavenConventions() {
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

		public AotProcess build() {
			return new AotProcess(this);
		}

	}

}
