package com.github.gbaso.timesheet.service;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.util.UriBuilder;

import com.github.gbaso.timesheet.csv.WorklogRow;
import com.github.gbaso.timesheet.utils.TimeUtils;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ApiReaderService {

    private String          cloudId;

    private final WebClient webClient;

    public List<WorklogRow> readWorklog(Set<String> projects, String author, LocalDate from, LocalDate to) {
        List<Integer> worklogIds = getWorklogIds(from);
        record Author(String displayName) {}
        record Worklog(String started, String timeSpent, Author author, String issueId) {}
        Function<UriBuilder, URI> uriFunction = uriBuilder -> uriBuilder
                .path("/ex/jira/{cloudid}/rest/api/3/worklog/list")
                .build(getCloudId());
        RequestHeadersSpec<?> request = webClient.post().uri(uriFunction).bodyValue(Map.of("ids", worklogIds));
        List<Worklog> worklogs = retrieve(request, Worklog.class).toStream().toList();
        List<WorklogRow> rows = worklogs.stream()
                .map(worklog -> WorklogRow.builder()
                        .started(LocalDate.parse(worklog.started.split("T")[0]))
                        .timeSpent(worklog.timeSpent)
                        .author(worklog.author.displayName)
                        .issueId(worklog.issueId)
                        .build())
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
        RequestHeadersSpec<?> request = webClient.get().uri(uriFunction);
        record WorklogEntry(Integer worklogId) {}
        record Worklogs(List<WorklogEntry> values) {}
        Worklogs worklogs = retrieve(request, Worklogs.class).blockFirst();
        Assert.notNull(worklogs, "Cannot read worklogs");
        return worklogs.values.stream().map(WorklogEntry::worklogId).toList();
    }

    private Map<String, Issue> getIssues(Set<String> projects, Set<String> issueIds) {
        record IssueType(String name) {}
        record Fields(String summary, IssueType issuetype) {}
        record ResultEntry(String id, String key, Fields fields) {}
        record SearchResult(List<ResultEntry> issues) {}
        List<ResultEntry> resultEntries = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Function<UriBuilder, URI> uriFunction = uriBuilder -> uriBuilder
                    .path("/ex/jira/{cloudid}/rest/api/3/search")
                    .queryParam("startAt", resultEntries.size())
                    .queryParam("jql", "project in ({projects}) and id in ({issueIds})")
                    .queryParam("fields", "summary,issuetype")
                    .build(getCloudId(), String.join(",", projects), String.join(",", issueIds));
            RequestHeadersSpec<?> request = webClient.get().uri(uriFunction);
            var searchResult = retrieve(request, SearchResult.class).blockFirst();
            Assert.notNull(searchResult, "Cannot read issues");
            if (searchResult.issues.isEmpty()) {
                break;
            }
            resultEntries.addAll(searchResult.issues);
        }
        return resultEntries.stream()
                .filter(e -> issueIds.contains(e.id))
                .collect(Collectors.toMap(ResultEntry::id, e -> new Issue(e.key, e.fields.summary, e.fields.issuetype.name)));
    }

    private String getCloudId() {
        if (this.cloudId == null) {
            RequestHeadersSpec<?> request = webClient.get().uri(uriBuilder -> uriBuilder.path("/oauth/token/accessible-resources").build());
            record Resource(String id) {}
            var resource = retrieve(request, Resource.class).blockFirst();
            Assert.notNull(resource, "Could not read accessile resources");
            this.cloudId = resource.id;
        }
        return this.cloudId;
    }

    <T> Flux<T> retrieve(RequestHeadersSpec<?> request, Class<T> responseClass) {
        return retrieve(request, ParameterizedTypeReference.forType(responseClass));
    }

    <T> Flux<T> retrieve(RequestHeadersSpec<?> request, ParameterizedTypeReference<T> responseTypeRef) {
        return request
                .accept(MediaType.APPLICATION_JSON)
                .attributes(ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("jira"))
                .retrieve()
                .bodyToFlux(responseTypeRef);
    }

}
