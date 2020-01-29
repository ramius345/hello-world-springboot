package com.example.restservice;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

	private static final String template = "Hello, %s!";
	private final AtomicLong counter = new AtomicLong();

	@GetMapping("/greeting")
	public Greeting greeting(@RequestParam(value = "name", defaultValue = "Tom") String name) {
	
		String env_name = System.getenv("ENVIRONMENT"); 
		if ( env_name != null && env_name != "" ) {
			name = env_name;		
		}
		
		
		return new Greeting(counter.incrementAndGet(), String.format(template, name));
	}
}
