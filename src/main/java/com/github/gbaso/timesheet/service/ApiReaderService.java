package com.github.gbaso.timesheet.service;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.util.UriBuilder;

import com.github.gbaso.timesheet.csv.WorklogRow;
import com.github.gbaso.timesheet.utils.TimeUtils;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ApiReaderService {

    private static final ParameterizedTypeReference<Map<String, Object>> STRING_OBJECT_MAP = new ParameterizedTypeReference<>() {};

    private String                                                       cloudId;

    private final WebClient                                              webClient;

    @SuppressWarnings("unchecked")
    public List<WorklogRow> readWorklog(Set<String> projects, String author, LocalDate from, LocalDate to) {
        List<Integer> worklogIds = getWorklogIds(from);
        List<Map<String, Object>> worklogs = getWorklogs(worklogIds);
        List<WorklogRow> rows = worklogs.stream()
                .map(map -> {
                    var row = new WorklogRow();
                    String started = (String) map.get("started");
                    String startedDate = started.split("T")[0];
                    row.setStarted(LocalDate.parse(startedDate));
                    row.setTimeSpent((String) map.get("timeSpent"));
                    Map<String, Object> authorProperty = (Map<String, Object>) map.get("author");
                    row.setAuthor((String) authorProperty.get("displayName"));
                    row.setIssueId((String) map.get("issueId"));
                    return row;
                })
                .filter(row -> StringUtils.equals(row.getAuthor(), author))
                .filter(row -> TimeUtils.between(row.getStarted(), from, to))
                .toList();
        Set<String> issueIds = rows.stream().map(WorklogRow::getIssueId).collect(Collectors.toUnmodifiableSet());
        Map<String, Issue> issues = getIssues(projects, issueIds);
        return rows.stream().filter(row -> issues.containsKey(row.getIssueId())).map(row -> {
            var issue = issues.get(row.getIssueId());
            row.setType(issue.type());
            row.setKey(issue.key());
            row.setSummary(issue.summary());
            return row;
        }).toList();
    }

    private List<Integer> getWorklogIds(LocalDate from) {
        Function<UriBuilder, URI> uriFunction = uriBuilder -> uriBuilder
                .path("/ex/jira/{cloudid}/rest/api/3/worklog/updated")
                .queryParam("since", TimeUtils.toEpochMillis(from))
                .queryParam("expand", "issueId,started,timeSpent,author")
                .build(getCloudId());
        Map<String, Object> response = executeRequest(HttpMethod.GET, uriFunction).blockFirst();
        Assert.notNull(response, "Cannot read worklogs");
        @SuppressWarnings("unchecked")
        var values = (List<Map<String, Object>>) response.get("values");
        return values.stream().map(it -> (Integer) it.get("worklogId")).toList();
    }

    private List<Map<String, Object>> getWorklogs(List<Integer> worklogIds) {
        Function<UriBuilder, URI> uriFunction = uriBuilder -> uriBuilder
                .path("/ex/jira/{cloudid}/rest/api/3/worklog/list")
                .build(getCloudId());
        return executeRequest(HttpMethod.POST, uriFunction, Map.of("ids", worklogIds)).toStream().toList();
    }

    private Map<String, Issue> getIssues(Set<String> projects, Set<String> issueIds) {
        List<Map<String, Object>> issues = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Function<UriBuilder, URI> uriFunction = uriBuilder -> uriBuilder
                    .path("/ex/jira/{cloudid}/rest/api/3/search")
                    .queryParam("startAt", issues.size())
                    .queryParam("jql", "project in ({projects}) and id in ({issueIds})")
                    .queryParam("fields", "summary,issuetype")
                    .build(getCloudId(), String.join(",", projects), String.join(",", issueIds));
            Map<String, Object> response = executeRequest(HttpMethod.GET, uriFunction).blockFirst();
            Assert.notNull(response, "Cannot read issues");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> page = (List<Map<String, Object>>) response.get("issues");
            if (page.isEmpty()) {
                break;
            }
            issues.addAll(page);
        }
        return issues.stream()
                .map(map -> Pair.of((String) map.get("id"), map))
                .filter(p -> issueIds.contains(p.getKey()))
                .collect(Collectors.toMap(Pair::getKey, p -> toIssue(p.getValue())));
    }

    @SuppressWarnings("unchecked")
    private Issue toIssue(Map<String, Object> map) {
        String key = (String) map.get("key");
        Map<String, Object> fields = (Map<String, Object>) map.get("fields");
        String summary = (String) fields.get("summary");
        Map<String, Object> issuetype = (Map<String, Object>) fields.get("issuetype");
        String type = (String) issuetype.get("name");
        return new Issue(key, summary, type);
    }

    private String getCloudId() {
        if (this.cloudId == null) {
            Map<String, Object> resource = executeRequest(HttpMethod.GET, uriBuilder -> uriBuilder.path("/oauth/token/accessible-resources").build()).blockFirst();
            Assert.notNull(resource, "Could not read accessile resources");
            this.cloudId = (String) resource.get("id");
        }
        return this.cloudId;
    }

    Flux<Map<String, Object>> executeRequest(HttpMethod method, Function<UriBuilder, URI> uriFunction) {
        return executeRequest(method, uriFunction, null);
    }

    Flux<Map<String, Object>> executeRequest(HttpMethod method, Function<UriBuilder, URI> uriFunction, Object body) {
        UnaryOperator<UriBuilder> host = uriBuilder -> uriBuilder.scheme("https").host("api.atlassian.com");
        RequestBodySpec reqestHeaderSpec = webClient.method(method).uri(host.andThen(uriFunction));
        if (body != null) {
            reqestHeaderSpec.bodyValue(body);
        }
        return reqestHeaderSpec
                .accept(MediaType.APPLICATION_JSON)
                .attributes(ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("jira"))
                .retrieve()
                .bodyToFlux(STRING_OBJECT_MAP);
    }
}
