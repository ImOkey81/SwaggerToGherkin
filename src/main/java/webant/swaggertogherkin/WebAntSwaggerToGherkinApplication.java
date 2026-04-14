package webant.swaggertogherkin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WebAntSwaggerToGherkinApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebAntSwaggerToGherkinApplication.class, args);
	}

}
