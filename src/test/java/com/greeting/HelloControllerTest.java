package com.greeting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class HelloControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(engine);
        viewResolver.setCharacterEncoding("UTF-8");

        mockMvc = MockMvcBuilders.standaloneSetup(new HelloController())
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void helloReturnsHelloView() throws Exception {
        mockMvc.perform(get("/hello"))
               .andExpect(status().isOk())
               .andExpect(view().name("hello"))
               .andExpect(model().attribute("message", "Hello, World!"));
    }

    @Test
    void helloWithNamePopulatesModel() throws Exception {
        mockMvc.perform(get("/hello?name=Jasjit"))
               .andExpect(status().isOk())
               .andExpect(view().name("hello"))
               .andExpect(model().attribute("message", "Hello, Jasjit!"));
    }
}
