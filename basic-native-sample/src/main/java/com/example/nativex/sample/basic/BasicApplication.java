package com.example.nativex.sample.basic;

import java.io.IOException;

import org.springframework.aot.AotDetector;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.aot.ApplicationContextAotInitializer;
import org.springframework.context.support.GenericApplicationContext;

@Configuration(proxyBeanMethods = false)
@ComponentScan
public class BasicApplication {

	public static void main(String[] args) throws Exception {
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

	private static void generateAot() throws IOException {
		AotProcess process = AotProcess.configure().withApplication(BasicApplication.class).withMavenBuildConventions()
				.withProjectId("com.example", "basic-native-sample").build();
		GenericApplicationContext applicationContext = prepareApplicationContext();
		process.performAotProcessing(applicationContext);
	}

	private static void runAot() throws ClassNotFoundException {
		GenericApplicationContext context = new GenericApplicationContext();
		new ApplicationContextAotInitializer().initialize(context,
				BasicApplication.class.getName() + "__ApplicationContextInitializer");
		context.refresh();
	}

	private static GenericApplicationContext prepareApplicationContext() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(BasicApplication.class);
		return context;
	}

}
