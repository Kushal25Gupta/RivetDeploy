# Benchmark Report

## 21.1 Worker Scaling Experiment
**Workload:** Deployment of a standard static React application using `npm install && npm run build`.
**Test runner:** `k6` load test (`tests/load/deployments.js`)

| Metric | 1 worker | 2 workers | 3 workers | 4 workers |
|--------|----------|-----------|-----------|-----------|
| **Throughput (deployments/min)** | 1.2 | 2.3 | 3.1 | 3.4 |
| **P50 latency (seconds)** | 48.5 | 49.1 | 51.2 | 55.4 |
| **P95 latency (seconds)** | 52.1 | 54.0 | 58.5 | 65.2 |
| **P99 latency (seconds)** | 53.5 | 56.2 | 61.3 | 70.1 |
| **Avg queue wait (seconds)** | 2.1 | 1.5 | 1.2 | 1.1 |
| **Success rate** | 100% | 100% | 100% | 98.5% |

### Bottleneck Analysis
As the worker count scales from 1 to 3, throughput increases almost linearly, demonstrating that the `RedisJobQueue` and scheduler polling loops handle concurrency effectively. However, scaling to 4 workers causes a drop in throughput efficiency and an increase in P99 latency.

The bottleneck at 4 workers is **Disk I/O and Network Bandwidth** on the worker node. Running 4 concurrent `npm install` and Nixpacks container builds simultaneously exhausts the local VM's disk IOPS, causing individual build durations to stretch significantly. To scale further, the workers should be distributed horizontally across multiple Compute Engine instances rather than increasing thread pools vertically on a single node.

## 21.2 Caching Experiment
**Method:** We ran the same repository build twice to observe the Nixpacks layer caching mechanism.

| Run Type | Install Time | Total Build Time | Cache Hit Rate | Upload Time |
|----------|--------------|------------------|----------------|-------------|
| **Cold Run** | 22.4s | 45.1s | 0% | 1.2s |
| **Warm Run** | 1.2s | 14.5s | 100% | 1.3s |

**Conclusion:** Caching reduces the total deployment time by roughly 65%. 
