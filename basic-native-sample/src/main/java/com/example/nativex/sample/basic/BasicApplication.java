package com.example.nativex.sample.basic;

import java.io.IOException;

import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.NativeDetector;
import org.springframework.javapoet.ClassName;

@Configuration(proxyBeanMethods = false)
@ComponentScan
public class BasicApplication {

	public static void main(String[] args) throws Exception {
		if (NativeDetector.inNativeImage()) {
			System.out.println("Run optimized application");
			runAot();
		}
		else {
			if (args.length == 1) {
				String command = args[0];
				if (command.equals("generateAot")) {
					System.out.println("Optimizing application for Native");
					generateAot();
				}
				else if (command.equals("runAot")) {
					System.out.println("Run optimized application");
					runAot();
				}
			}
			else {
				System.out.println("Run regular application");
				prepareApplicationContext().refresh();
			}
		}
	}

	private static void generateAot() throws IOException {
		AotProcess process = AotProcess
				.ofNamingStrategy((packageName) -> ClassName.get(packageName,
						BasicApplication.class.getSimpleName() + "__ApplicationContextInitializer"))
				.withMavenBuildConventions().withProjectId("com.example", "basic-native-sample").build();
		GenericApplicationContext applicationContext = prepareApplicationContext();
		process.run(applicationContext, BasicApplication.class.getPackageName());
	}

	@SuppressWarnings("unchecked")
	private static void runAot() throws ClassNotFoundException {
		GenericApplicationContext context = new GenericApplicationContext();
		Class<? extends ApplicationContextInitializer<GenericApplicationContext>> initializer = (Class<? extends ApplicationContextInitializer<GenericApplicationContext>>) Class
				.forName(BasicApplication.class.getName() + "__ApplicationContextInitializer");
		BeanUtils.instantiateClass(initializer).initialize(context);
		context.refresh();
	}

	private static GenericApplicationContext prepareApplicationContext() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(BasicApplication.class);
		return context;
	}

}
