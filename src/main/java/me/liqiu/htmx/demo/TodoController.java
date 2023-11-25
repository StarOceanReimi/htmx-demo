package me.liqiu.htmx.demo;

import lombok.RequiredArgsConstructor;
import lombok.With;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
@RequiredArgsConstructor
@RequestMapping("/todos")
public class TodoController {

    private final TodoRepository repository;

    @GetMapping({"", "/"})
    public Mono<String> index(Model model, @RequestParam(value = "filter-type", required = false) String type) {

        Flux<Todo> todosFlux = "unfinished".equals(type)
            ? repository.findAll(Example.of(new Todo(null, null, false), ExampleMatcher.matching().withIgnoreNullValues()))
            : repository.findAll();
        return todosFlux.collectList().map(todos -> {
            model.addAttribute("items", todos);
            return "todo";
        });
    }

    @PostMapping({"", "/"})
    public Mono<String> save(ServerWebExchange exchange) {
        return exchange.getFormData().mapNotNull(m -> m.getFirst("todo"))
            .switchIfEmpty(Mono.error(new IllegalArgumentException("todo not exists!")))
            .flatMap(todo -> repository
                .save(new Todo(null, todo, false)))
            .map(t -> "redirect:/todos");
    }

    @PostMapping("update")
    public Mono<String> statusUpdate(ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(m -> {
            final String sid = m.getFirst("id");
            final int id = Integer.parseInt(sid);
            return repository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("id not exists")))
                .flatMap(t -> repository.save(t.withDone(!t.done())))
                .map(t -> "redirect:/todos");
        });
    }

    @PostMapping("clear-all")
    public Mono<String> clearAll() {
        final Example<Todo> example = Example.of(new Todo(null, null, true), ExampleMatcher.matching().withIgnoreNullValues());
        return repository.findAll(example).collectList()
            .flatMap(repository::deleteAll)
            .then(Mono.just("redirect:/todos"));
    }

    @PostMapping("delete-by-id")
    public Mono<String> deleteById(ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(m -> {
            final String id = m.getFirst("id");
            return repository.deleteById(Integer.parseInt(id));
        }).then(Mono.just("redirect:/todos"));

    }

}

@Table("todos")
record Todo(@Id Integer id, String task, @With boolean done) {}

@Repository
interface TodoRepository extends R2dbcRepository<Todo, Integer> {}
