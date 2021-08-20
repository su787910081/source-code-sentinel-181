ServerTransportConfig 配置的是服务器的端口，从监听器里面读取出来 之后会以该端口作为监听端口，用于客户端通过netty 连接。



集群流控关键类

TokenServerHandler

TokenServer netty 服务器的处理类，用于处理netty client 的请求。

并且，每有一个新的客户端连接上面都会以ip:port为key 将其保存起来，存到一个连接池中。

相应的当客户端断开连接，会将其从连接池中删除。

每当有客户端有消息请求时都将记录其请求时间。

NettyTransportServer

TokenServer netty 服务器的网络启动监听启动类，用于服务监听端口。

SentinelDefaultTokenServer

DefaultEmbeddedTokenServer

通过spi 加载的类



TokenClientHandler

TokenClient netty 客户端处理类，用于处理与netty server 的通信。

NettyTransportClient

连接tokenServer 的客户端





ClusterConfigController

查询当前环境上面对应的集群机器群


2021-08-19 集群流控
ClusterUniversalStatePairVO {
	// sentinel client 的ip 与port
    private String ip;
    private Integer commandPort;

	// 该机器对应的信息
	ClusterUniversalStateVO  state
	{
		// 通过http 请求: "getClusterMode" 获取到的
		ClusterStateSimpleEntity  stateInfo;
		// 通过http 请求: "cluster/client/fetchConfig" 获取到部分信息然后组装成client
		// 返回: ClusterClientStateEntity(该实体与ClusterClientInfoVO 的属性完全一样，只是http 请求处理是使用的两个实体对象。)
		ClusterClientStateVO client;
		{
			// 集群TokenClient 的相关信息
			serverHost
			serverPort
			clientState: 是否正常运行
		}
		// 通过http 请求: "cluster/server/info" 获取到信息
		ClusterServerStateVO server;
		{
			// 集群TokenServer 的相关信息
		}
	}
}


命令请求: "cluster/client/fetchConfig" 里面的ip port 需要处理
命令请求: "cluster/server/info" 里面的各种Port 都需要处理


命令请求: "cluster/server/modifyFlowRules"  修改集群流控规则，当前在sentinelDashboard 里面还没有任何地方调用该请求。


// 集群流控检查
com.alibaba.csp.sentinel.slots.block.flow.FlowRuleChecker.passClusterCheck(..)

// 默认的集群namespace 在ConfigSupplierRegistry 
// 如果没有重新进行set 那么名字空间就是appName

# 独立模式博客：
https://www.jianshu.com/p/67d02420e814
# 独立模式参考链接
https://github.com/all4you/sentinel-tutorial/blob/master/sentinel-practice/sentinel-cluster-flow-control/sentinel-cluster-flow-environment-build.md