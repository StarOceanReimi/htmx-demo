package me.liqiu.htmx.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.With;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Sort;
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

    private final ObjectMapper objectMapper;

    @GetMapping({"", "/"})
    public Mono<String> index(Model model, @RequestParam(value = "filter-type", required = false) String type) {
        final Sort idSort = Sort.by(Sort.Direction.DESC, "id");
        Flux<Todo> todosFlux = "unfinished".equals(type)
            ? repository.findAll(Example.of(new Todo(null, null, false), ExampleMatcher.matching().withIgnoreNullValues()), idSort)
            : repository.findAll(idSort);
        return todosFlux.collectList().map(todos -> {
            model.addAttribute("items", todos);
            return "todo";
        });
    }

    @PostMapping(value = "/save", headers = "hx-request")
    public Mono<String> save(ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(m -> {
            final String todo = m.getFirst("todo");
            Preconditions.checkArgument(!Strings.isNullOrEmpty(todo), "Todo cannot be empty");
            final boolean isEmpty = Boolean.parseBoolean(m.getFirst("type"));
            return repository.save(new Todo(null, todo, false)).map(t -> {
                if (isEmpty)
                    return "fragments/biz/todo-item :: todoItemFirst(id=%s, task='%s', done=%s)".formatted(t.id(), t.task(), t.done());
                return "fragments/biz/todo-item :: todoItemNew(id=%s, task='%s', done=%s)".formatted(t.id(), t.task(), t.done());
            });
        });
    }

}

@Table("todos")
record Todo(@Id Integer id, String task, @With boolean done) {}

@Repository
interface TodoRepository extends R2dbcRepository<Todo, Integer> {}
