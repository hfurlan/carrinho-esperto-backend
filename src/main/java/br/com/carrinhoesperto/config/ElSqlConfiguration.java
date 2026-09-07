package br.com.carrinhoesperto.config;

import com.opengamma.elsql.ElSqlConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElSqlConfiguration {

    @Bean
    public ElSqlConfig elSqlConfig() {
        return ElSqlConfig.DEFAULT;
    }
}
