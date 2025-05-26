package tesis.tesisfp.config;

import org.springframework.beans.factory.annotation.Value;  // Corregido el import
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;  // Añadido

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class FileStorageConfig implements WebMvcConfigurer {  // Implementar WebMvcConfigurer

    @Value("${app.upload.dir:${user.home}/product-images}")  // Corregida la anotación Value
    private String uploadDir;

    @Bean
    public String createUploadDirectory() throws IOException {  // Cambiado de void a String
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        return uploadDir;  // Devolver algo
    }

    @Override  // Usar override en lugar de @Bean
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + uploadDir + "/")
                .setCachePeriod(3600)
                .resourceChain(true);
    }
}