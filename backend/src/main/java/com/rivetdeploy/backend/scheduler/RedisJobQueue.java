package com.rivetdeploy.backend.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "rivetdeploy.queue.type", havingValue = "redis")
public class RedisJobQueue implements JobQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisJobQueue.class);
    private static final String QUEUE_KEY = "rivetdeploy:queue:jobs";
    private static final String DELAYED_KEY = "rivetdeploy:queue:delayed";
    private static final String PROCESSING_KEY = "rivetdeploy:queue:processing";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService delayedJobScheduler = Executors.newSingleThreadScheduledExecutor();

    public RedisJobQueue(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void startDelayedJobPoller() {
        delayedJobScheduler.scheduleWithFixedDelay(this::pollDelayedJobs, 500, 500, TimeUnit.MILLISECONDS);
        log.info("RedisJobQueue initialized and delayed job poller started.");
    }

    @PreDestroy
    public void stopDelayedJobPoller() {
        delayedJobScheduler.shutdownNow();
    }

    private void pollDelayedJobs() {
        try {
            long now = System.currentTimeMillis();
            Set<String> readyJobs = redisTemplate.opsForZSet().rangeByScore(DELAYED_KEY, 0, now);
            if (readyJobs != null && !readyJobs.isEmpty()) {
                for (String jobJson : readyJobs) {
                    Long removed = redisTemplate.opsForZSet().remove(DELAYED_KEY, jobJson);
                    if (removed != null && removed > 0) {
                        redisTemplate.opsForList().leftPush(QUEUE_KEY, jobJson);
                        log.debug("Moved delayed job back to main queue: {}", jobJson);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error polling delayed jobs in RedisJobQueue", e);
        }
    }

    @Override
    public void enqueue(Job job) {
        try {
            String json = objectMapper.writeValueAsString(job);
            redisTemplate.opsForList().leftPush(QUEUE_KEY, json);
            log.debug("Enqueued job {} to Redis", job.getDeploymentId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to enqueue job to Redis", e);
        }
    }

    @Override
    public Job dequeue() {
        try {
            // Blocking pop from right with 2 second timeout
            String json = redisTemplate.opsForList().rightPop(QUEUE_KEY, 2, TimeUnit.SECONDS);
            if (json == null) {
                return null;
            }
            redisTemplate.opsForSet().add(PROCESSING_KEY, json);
            return objectMapper.readValue(json, Job.class);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Dequeue interrupted");
                return null;
            }
            log.error("Error dequeuing job from Redis", e);
            return null;
        }
    }

    @Override
    public void acknowledge(Job job) {
        try {
            String json = objectMapper.writeValueAsString(job);
            redisTemplate.opsForSet().remove(PROCESSING_KEY, json);
        } catch (Exception e) {
            log.warn("Failed to acknowledge job {}: {}", job.getDeploymentId(), e.getMessage());
        }
    }

    @Override
    public void requeue(Job job, Duration delay) {
        try {
            String json = objectMapper.writeValueAsString(job);
            redisTemplate.opsForSet().remove(PROCESSING_KEY, json);

            if (delay == null || delay.isZero()) {
                redisTemplate.opsForList().leftPush(QUEUE_KEY, json);
            } else {
                long executeAt = System.currentTimeMillis() + delay.toMillis();
                redisTemplate.opsForZSet().add(DELAYED_KEY, json, executeAt);
                log.info("Requeued job {} with delay of {}ms in Redis", job.getDeploymentId(), delay.toMillis());
            }
        } catch (Exception e) {
            log.error("Failed to requeue job {} in Redis", job.getDeploymentId(), e);
        }
    }

    @Override
    public int size() {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY);
        return size == null ? 0 : size.intValue();
    }
}
