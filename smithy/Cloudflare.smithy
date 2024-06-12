$version: "2"

namespace smithy4s.cf

use alloy#simpleRestJson

@simpleRestJson
@httpBearerAuth
service CloudflareService {
  version: "1.0.0",
  operations: [GetZoneId, GetDnsRec, UpdDnsRec]
}

@readonly
@http(method: "GET", uri: "/zones")
operation GetZoneId {
  input := {
    @required @httpQuery("name") name: String
    @required @httpQuery("status") status: String = "active"
  },
  output := {
    @required result_info: ResultInfo
    @required result: Zones
  }
}

list Zones {
  member: ZoneInfo
}

structure ZoneInfo {
  @required id: String
  @required name: String
  @required status: String
}

@readonly
@http(method: "GET", uri: "/zones/{zoneId}/dns_records")
operation GetDnsRec {
  input := {
    @required @httpLabel zoneId: String
    @required @httpQuery("name") name: String
    @required @httpQuery("type") status: String = "A"
  },
  output := {
    @required result_info: ResultInfo
    @required result: DnsRecs
  }
}

list DnsRecs {
  member: DnsRec
}

structure DnsRec {
  @required id: String
  @required name: String
  @required content: String
  @required proxied: Boolean
  @required ttl: Integer
  @required type: String
}

@http(method: "PATCH", uri: "/zones/{zoneId}/dns_records/{dnsRecId}")
operation UpdDnsRec {
  input := {
    @required @httpLabel zoneId: String
    @required @httpLabel dnsRecId: String
    @required content: String
  },
  output := {
    @required result: DnsRec
  }
}

structure ResultInfo {
  @required page: Integer
  @required per_page: Integer
  @required total_pages: Integer
  @required count: Integer
  @required total_count: Integer
}
