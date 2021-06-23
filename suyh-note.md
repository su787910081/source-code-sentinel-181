这个项目要是直接build 是会失败的，但是可以直接运行dashboard



## protoc

- 下载

  > 下载地址：https://github.com/protocolbuffers/protobuf/releases
  >
  > 下载文件: `protoc-3.17.3-win64.zip`
  >
  > 放到一个目录，我这里放在了D盘，并解压到`protoc-3.17.3-win64`目录。
  >
  > 解压开之后有一个`bin\proto.exe` 可执行文件，完整路径为：`D:\protoc-3.17.3-win64\bin\protoc.exe`
  >
  > 



## 编译失败

- 首先下载安装好`protoc`，参照上面

- `sentinel-grpc-adapter` 模块

  > 报错：程序包com.alibaba.csp.sentinel.adapter.grpc.gen不存在
  >
  > 这个报错是在test 模块下面的，它需要先使用proto 先生成该模块下面的`example.proto`
  >
  > 
  >
  > 执行命令如下：
  >
  > ```cmd
  > # 进入到指定目录
  > $ cd sentinel-adapter\sentinel-grpc-adapter\src\test\proto
  > # 然后执行如下命令
  > $ D:\protoc-3.17.3-win64\bin\protoc.exe --java_out=../java example.proto
  > ```
  >
  > 妈的，最后重新编译还是失败了，还是有类找不到。
  >
  > 删除掉吧，暂时反正是单元测试，我主要看源码。

- `sentinel-cluster-server-envoy-rls` 模块

  > 报错：java: 程序包io.envoyproxy.envoy.api.v2.ratelimit不存在
  >
  > 进入目录: `sentinel-cluster\sentinel-cluster-server-envoy-rls\src\main\proto`
  >
  > 执行命令：
  >
  > ```cmd
  > # 进入到指定目录
  > $ cd sentinel-cluster\sentinel-cluster-server-envoy-rls\src\main\proto
  > # 然后执行如下命令
  > $ D:\protoc-3.17.3-win64\bin\protoc.exe --java_out=../java envoy\api\v2\ratelimit\ratelimit.proto
  > $ D:\protoc-3.17.3-win64\bin\protoc.exe --java_out=../java envoy\service\ratelimit\v2\rls.proto
  > $ D:\protoc-3.17.3-win64\bin\protoc.exe --java_out=../java envoy\api\v2\core\base.proto
  > ```
  >
  > 还是有一个找不到: io.envoyproxy.envoy.service.ratelimit.v2.RateLimitServiceGrpc
  >
  > 不知道这个在哪里，没有多的proto 文件了，暂时不管了。
  >
  > 先把该模块排除掉吧。
  >
  > ```xml
  >     <modules>
  >         <module>sentinel-cluster-client-default</module>
  >         <module>sentinel-cluster-server-default</module>
  >         <module>sentinel-cluster-common-default</module>
  >         <!-- <module>sentinel-cluster-server-envoy-rls</module> -->
  >     </modules>
  > ```
  >
  > 




