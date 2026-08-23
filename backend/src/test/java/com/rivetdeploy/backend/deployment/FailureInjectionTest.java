package com.rivetdeploy.backend.deployment;

import com.rivetdeploy.backend.docker.DockerBuildService;
import com.rivetdeploy.backend.executor.CancellationManager;
import com.rivetdeploy.backend.git.GitService;
import com.rivetdeploy.backend.project.Project;
import com.rivetdeploy.backend.project.ProjectRepository;
import com.rivetdeploy.backend.scheduler.JobQueue;
import com.rivetdeploy.backend.auth.User;
import com.rivetdeploy.backend.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.io.File;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;

@SpringBootTest
@AutoConfigureMockMvc
public class FailureInjectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private GitService gitService;

    @MockBean
    private DockerBuildService dockerBuildService;
    
    @Autowired
    private CancellationManager cancellationManager;

    private Project testProject;
    
    private DefaultOAuth2User mockOAuth2User;

    @BeforeEach
    void setUp() {
        deploymentRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        User u = new User();
        u.setId("user_123");
        u.setGithubId("test_github_id");
        u.setUsername("test_login");
        u.setCreatedAt(Instant.now());
        userRepository.save(u);

        testProject = new Project();
        testProject.setId("prj_failure_test");
        testProject.setName("Failure Matrix Project");
        testProject.setOwnerId("user_123");
        testProject.setRepositoryUrl("https://github.com/dummy/repo.git");
        testProject.setBranch("main");
        testProject.setCreatedAt(Instant.now());
        testProject.setUpdatedAt(Instant.now());
        projectRepository.save(testProject);
        
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "test_github_id");
        attrs.put("login", "test_login");
        attrs.put("name", "test_github_id");
        mockOAuth2User = new DefaultOAuth2User(null, attrs, "name");
    }

    @Test
    void testGitCloneTransientFailureIsRetried() throws Exception {
        when(gitService.cloneRepository(anyString(), anyString(), anyString()))
                .thenThrow(new TransientFailureException(FailureType.TRANSIENT_NETWORK, "Simulated Network Error"));

        String response = triggerDeployment();
        String deploymentId = extractId(response);

        Thread.sleep(1500); 

        Deployment deployment = deploymentRepository.findById(deploymentId).orElseThrow();
        assertEquals(DeploymentState.QUEUED, deployment.getStatus(), "Should be queued for retry");
        assertNull(deployment.getFailureType(), "Should not have permanent failure type yet");
    }

    @Test
    void testBuildCommandFailureIsPermanent() throws Exception {
        when(gitService.cloneRepository(anyString(), anyString(), anyString())).thenReturn(new File("/tmp/workspace"));
        when(dockerBuildService.buildImage(any(), any()))
                .thenThrow(new PermanentFailureException(FailureType.BUILD_COMMAND_FAILED, "NPM install failed"));

        String response = triggerDeployment();
        String deploymentId = extractId(response);

        Thread.sleep(1500); 

        Deployment deployment = deploymentRepository.findById(deploymentId).orElseThrow();
        assertEquals(DeploymentState.BUILD_FAILED, deployment.getStatus());
        assertEquals("BUILD_COMMAND_FAILED", deployment.getFailureType());
    }

    @Test
    void testCancellationDuringBuild() throws Exception {
        when(gitService.cloneRepository(anyString(), anyString(), anyString())).thenReturn(new File("/tmp/workspace"));
        when(dockerBuildService.buildImage(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(3000); 
            return "test-image";
        });

        String response = triggerDeployment();
        String deploymentId = extractId(response);
        
        Thread.sleep(500);

        mockMvc.perform(post("/api/deployments/" + deploymentId + "/cancel")
                        .with(oauth2Login().oauth2User(mockOAuth2User))
                        .with(csrf()))
                .andExpect(status().isOk());

        Thread.sleep(1000);

        Deployment deployment = deploymentRepository.findById(deploymentId).orElseThrow();
        assertEquals(DeploymentState.CANCELLED, deployment.getStatus());
        assertTrue(cancellationManager.isCancelled(deploymentId));
    }

    private String triggerDeployment() throws Exception {
        return mockMvc.perform(post("/api/projects/" + testProject.getId() + "/deployments?commitSha=HEAD")
                        .with(oauth2Login().oauth2User(mockOAuth2User))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
    
    private String extractId(String json) {
        String match = "\"id\":\"";
        int start = json.indexOf(match) + match.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
