package me.liqiu.htmx.demo;

import lombok.RequiredArgsConstructor;
import lombok.With;
import org.reactivestreams.Publisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.thymeleaf.spring6.view.reactive.ThymeleafReactiveViewResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

@Controller
@RequiredArgsConstructor
@RequestMapping("/todos")
public class TodoController {

    private final TodoRepository repository;

    @GetMapping({"", "/"})
    public Mono<String> index(Model model, @RequestParam(value = "filter-type", required = false) String type) {
        return todoState(type).collectList().map(todos -> {
            final int finished = (int) todos.stream().filter(Todo::done).count();
            final int total = todos.size();
            model.addAttribute("items", todos);
            model.addAttribute("filter", ofNullable(type).orElse("all"));
            model.addAttribute("finished", finished);
            model.addAttribute("total", total);
            return "todo";
        });
    }

    @PostMapping(value = "/refresh", headers = "hx-request")
    public Mono<Void> refresh(ServerWebExchange exchange) {
        return renderTodosState(exchange);
    }

    @PostMapping(value = "/save", headers = "hx-request")
    public Mono<Void> save(ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(m -> {
            final String todo = m.getFirst("todo");
            var saveMono = repository.save(new Todo(null, todo, false));
            return saveMono.then(renderTodosState(exchange));
        });
    }

    @PutMapping("/{id}/complete")
    public Mono<Void> completeItem(@PathVariable Integer id, ServerWebExchange exchange) {
        final Mono<String> todoType = Mono.justOrEmpty(exchange.getRequest().getQueryParams().getFirst("filter-type"))
            .switchIfEmpty(exchange.getFormData().mapNotNull(
                m -> Optional.ofNullable(m.getFirst("filter-type")).orElse(m.getFirst("todos-filter")))
            )
            .switchIfEmpty(Mono.just("all"));
        return todoType.flatMap(filter -> repository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("id not exists")))
            .flatMap(todo -> repository.save(todo.withDone(!todo.done())))
            .flatMap(todo -> {
                exchange.getResponse().getHeaders().set("HX-Trigger", "status-update");
                if (isUnfinishedType(filter)) return Mono.empty();
                return renderFragment("fragments/biz/todos :: todo-item",
                    Map.of("id", todo.id(), "task", todo.task(), "done", todo.done(), "filter", filter), exchange);
            }));
    }

    @DeleteMapping(value = "/{id}", headers = "hx-request")
    public Mono<Void> delete(@PathVariable Integer id, ServerWebExchange exchange) {
        return repository.deleteById(id)
            .then(renderTodosState(exchange));
    }

    @DeleteMapping(value = "/clear-finished", headers = "hx-request")
    public Mono<Void> clearFinished(ServerWebExchange exchange) {
        return repository.findAll(Example.of(new Todo(null, null, true), ExampleMatcher.matching().withIgnoreNullValues()))
            .collectList().flatMap(repository::deleteAll)
            .then(renderTodosState(exchange));
    }

    @PostMapping("/status")
    public Mono<Void> todoStatusQuery(ServerWebExchange exchange) {
        return obtainFilterType(exchange)
            .flatMap(type -> todoState(type).collectList())
            .flatMap(todos -> {
                int finished = (int) todos.stream().filter(Todo::done).count();
                int total = todos.size();
                return renderFragment("/fragments/biz/todos :: todo-count-msg", Map.of("finished", finished, "total", total), exchange);
            });
    }

    private boolean isUnfinishedType(String type) {
        return "unfinished".equals(type);
    }

    private Flux<Todo> todoState(String type) {
        final Sort idSort = Sort.by(Sort.Direction.DESC, "id");
        return isUnfinishedType(type)
            ? repository.findAll(Example.of(new Todo(null, null, false), ExampleMatcher.matching().withIgnoreNullValues()), idSort)
            : repository.findAll(idSort);
    }

    private final ThymeleafReactiveViewResolver resolver;

    private Mono<Void> renderFragment(String fragments, Map<String, ?> model, ServerWebExchange exchange) {
        final Locale locale = LocaleContextHolder.getLocale();
        return resolver.resolveViewName(fragments, locale)
            .flatMap(v -> v.render(model, MediaType.TEXT_HTML, exchange));
    }

    private Mono<String> obtainFilterType(ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getQueryParams().getFirst("filter-type"))
            .switchIfEmpty(exchange.getFormData().mapNotNull(
                m -> Optional.ofNullable(m.getFirst("filter-type")).orElse(m.getFirst("todos-filter")))
            )
            .switchIfEmpty(Mono.just("all"));
    }

    private Mono<Void> renderTodosState(ServerWebExchange exchange) {
        return obtainFilterType(exchange).flatMap(type -> todoState(type).collectList()
            .flatMap(todos -> renderFragment(
                "fragments/biz/todos :: todo-container-oob",
                toTodosModel(todos, type),
                exchange
            ))
        );
    }

    private Map<String, ?> toTodosModel(List<Todo> todos, String type) {
        int finished = (int) todos.stream().filter(Todo::done).count();
        int total = todos.size();
        return Map.of(
            "items", todos,
            "filter", type,
            "finished", finished,
            "total", total
        );
    }

}

@Table("todos")
record Todo(@Id Integer id, String task, @With boolean done) {}

@Repository
interface TodoRepository extends R2dbcRepository<Todo, Integer> {}
