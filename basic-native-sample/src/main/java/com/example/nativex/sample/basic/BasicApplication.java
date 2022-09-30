package com.example.nativex.sample.basic;

import org.springframework.aot.AotDetector;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

@Configuration(proxyBeanMethods = false)
@ComponentScan
public class BasicApplication {

	public static void main(String[] args) {
		if (AotDetector.useGeneratedArtifacts()) {
			System.out.println("Run optimized application");
			runAot();
		}
		else if (args.length == 1 && args[0].equals("generateAot")) {
			System.out.println("Optimizing application for Native");
			generateAot();
		}
		else {
			System.out.println("Run regular application");
			prepareApplicationContext().refresh();
		}
	}

	private static void generateAot() {
		AotProcess process = AotProcess.configure().withApplication(BasicApplication.class).withMavenBuildConventions()
				.withProjectId("com.example", "basic-native-sample").build();
		process.process();
	}

	private static void runAot() {
		GenericApplicationContext context = new GenericApplicationContext();
		AotApplicationContextInitializer
				.forInitializerClasses(BasicApplication.class.getName() + "__ApplicationContextInitializer")
				.initialize(context);
		context.refresh();
	}

	private static GenericApplicationContext prepareApplicationContext() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(BasicApplication.class);
		return context;
	}

}
