package com.github.gbaso.timesheet.web;

import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LoginController {

    private static final String                 CLIENT_AUTHORIZATION_PATH = "/oauth2/authorization";

    private final OAuth2ClientProperties        properties;
    private final ClientRegistrationRepository  clientRegistrationRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @GetMapping
    public String getLoginPage(Model model) {
        var registrationId = properties.getRegistration().keySet().stream().findFirst().orElseThrow();
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(registrationId);
        model.addAttribute("url", CLIENT_AUTHORIZATION_PATH + "/" + registration.getRegistrationId());
        model.addAttribute("name", registration.getClientName());
        return "index";
    }

    @GetMapping("/login-success")
    public String getLoginInfo(OAuth2AuthenticationToken token) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(token.getAuthorizedClientRegistrationId(), token.getName());
        log.debug("Jira access token: {}", client.getAccessToken().getTokenValue());
        return "login-success";
    }

}
