package com.rivetdeploy.backend.auth;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String githubId = oAuth2User.getName();
        String username = oAuth2User.getAttribute("login");
        String avatarUrl = oAuth2User.getAttribute("avatar_url");

        userRepository.findByGithubId(githubId).orElseGet(() -> {
            User user = new User();
            user.setId(UUID.randomUUID().toString());
            user.setGithubId(githubId);
            user.setUsername(username);
            user.setAvatarUrl(avatarUrl);
            user.setCreatedAt(Instant.now());
            return userRepository.save(user);
        });

        return oAuth2User;
    }
}
