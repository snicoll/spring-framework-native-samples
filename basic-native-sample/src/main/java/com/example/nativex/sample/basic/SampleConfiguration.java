package com.example.nativex.sample.basic;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SampleConfiguration {

	@Bean
	public String testBean() {
		return "hello";
	}

	@Bean
	public SampleBean sampleBean(String message) {
		return new SampleBean(message);
	}

}
