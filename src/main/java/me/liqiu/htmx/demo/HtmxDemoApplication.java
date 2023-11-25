package me.liqiu.htmx.demo;

import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.MySqlDialect;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.result.view.View;
import org.thymeleaf.spring6.view.reactive.ThymeleafReactiveView;
import org.thymeleaf.spring6.view.reactive.ThymeleafReactiveViewResolver;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@SpringBootApplication
public class HtmxDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(HtmxDemoApplication.class, args);
    }

    @Bean
    public R2dbcCustomConversions customConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new BooleanToShortConverter());
        converters.add(new ShortToBooleanConverter());
        return R2dbcCustomConversions.of(MySqlDialect.INSTANCE, converters);
    }

    @WritingConverter
    static class BooleanToShortConverter implements Converter<Boolean, Short> {

        @Override
        public Short convert(@NonNull Boolean source) {
            return source ? (short) 1 : (short) 0;
        }
    }

    @ReadingConverter
    static class ShortToBooleanConverter implements Converter<Short, Boolean> {

        @Override
        public Boolean convert(@NonNull Short source) {
            return source != 0;
        }
    }

}

@Controller
@RequiredArgsConstructor
class IndexController {

//    @GetMapping("/")
//    public Mono<String> index(Model model) {
//        model.addAttribute("msg", "hello world!");
//        return Mono.just("index");
//    }

    @GetMapping("/frag")
    public Mono<String> frag(Model model) {
        model.addAttribute("name", "YF");
        model.addAttribute("xt", "QL");
        return Mono.just("fragments/f1 :: f1");
    }

}