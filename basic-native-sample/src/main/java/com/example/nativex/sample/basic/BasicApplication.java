package com.example.nativex.sample.basic;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.NativeDetector;

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

	private static void generateAot() {
		GenericApplicationContext applicationContext = prepareApplicationContext();
		Path target = Paths.get("").resolve("target");
		AotInvoker invoker = new AotInvoker(target.resolve("generated-sources/aot"), target.resolve("classes"));
		invoker.invoke(applicationContext, BasicApplication.class);
	}

	@SuppressWarnings("unchecked")
	private static void runAot() throws ClassNotFoundException {
		GenericApplicationContext context = new GenericApplicationContext();
		Class<? extends ApplicationContextInitializer<GenericApplicationContext>> initializer = (Class<? extends ApplicationContextInitializer<GenericApplicationContext>>) Class
				.forName(BasicApplication.class.getName() + "$$ApplicationContextInitializer");
		BeanUtils.instantiateClass(initializer).initialize(context);
		context.refresh();
	}

	private static GenericApplicationContext prepareApplicationContext() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(BasicApplication.class);
		return context;
	}

}
