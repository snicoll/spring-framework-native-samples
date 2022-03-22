package com.example.nativex.sample.basic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.springframework.aot.generator.DefaultGeneratedTypeContext;
import org.springframework.aot.generator.GeneratedType;
import org.springframework.aot.generator.GeneratedTypeReference;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.nativex.FileNativeConfigurationGenerator;
import org.springframework.context.generator.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.JavaFile;

/**
 * @author Stephane Nicoll
 */
public class AotInvoker {

	private final Path generatedSources;

	private final Path generatedResources;

	public AotInvoker(Path generatedSources, Path generatedResources) {
		this.generatedSources = generatedSources;
		this.generatedResources = generatedResources;
	}

	public void invoke(GenericApplicationContext applicationContext, Class<?> application) {
		DefaultGeneratedTypeContext generationContext = new DefaultGeneratedTypeContext(application.getPackageName(),
				(packageName) -> GeneratedType.of(
						ClassName.get(packageName, application.getSimpleName() + "$$ApplicationContextInitializer")));
		ApplicationContextAotGenerator generator = new ApplicationContextAotGenerator();
		generator.generateApplicationContext(applicationContext, generationContext);

		// Register reflection hint for entry point as we access it via reflection
		generationContext.runtimeHints().reflection().registerType(
				GeneratedTypeReference.of(generationContext.getMainGeneratedType().getClassName()),
				(hint) -> hint.onReachableType(TypeReference.of(application)).withConstructor(Collections.emptyList(),
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

}
