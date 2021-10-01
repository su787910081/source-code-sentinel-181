sentinel 数据统计

## dashboard 实时监控

定时拉取客户端的资源数据到dashboard，就在如下这个类里面完成的，它有一个定时调度任务，还有两个线程池`MetricFetcher`。
在这个类中有一个定时任务调度器(`fetchScheduleService`)，每秒钟执行一次。
每次执行都会去拉取每个app 下的所有机器的统计数据，而这个统计数据是以时间戳范围的形式获取的。
最终调用了`InMemoryMetricsRepository#saveAll` 方法来保存到内存中。
通过http 请求的请求API 路径为: `"metric"`，完整路径为：

```java
final String url = "http://" + machine.getIp() + ":" + machine.getPort() + "/" + METRIC_URL_PATH
                + "?startTime=" + startTime + "&endTime=" + endTime + "&refetch=" + false;
```

​    

## sentinel 客户端

对于sentinel 客户端，实现该接口的地方为：`SendMetricCommandHandler`

这些统计数据也是有一个定时任务在跑的，具体为：`MetricTimerListener`
实际执行操作是：`MetricWriter`

统计数据格式：`MetricNode#toFatString`