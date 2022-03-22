package com.example.nativex.sample.basic;

import jakarta.annotation.PostConstruct;

public class SampleBean {

	private final String message;

	public SampleBean(String message) {
		this.message = message;
	}

	@PostConstruct
	public void printMessageOnStartup() {
		System.out.println(">>>>>>> " + message);
	}

}
