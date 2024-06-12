$version: "2"

namespace smithy4s.myip

use alloy#simpleRestJson

@simpleRestJson
service MyIpService {
  version: "1.0.0",
  operations: [GetIp]
}

@readonly
@http(method: "GET", uri: "/")
operation GetIp {
  input: Unit
  output: IpInfo
}

structure IpInfo {
  @required
  ip: String
  country: String
  cc: String
}
