package cfddns

import cats.data.NonEmptyList
import cats.syntax.all.*

import cats.effect.*

import org.http4s.*
import org.http4s.client.Client
import org.http4s.client.middleware.Logger
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.*
import org.http4s.syntax.all.*

import com.monovore.decline.*
import com.monovore.decline.effect.*

import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.util.control.NoStackTrace

import java.nio.file.{Files, NoSuchFileException, Path}

import smithy4s.Hints
import smithy4s.cf.*
import smithy4s.http4s.{ClientEndpointMiddleware, SimpleRestJsonBuilder}
import smithy4s.myip.*

given LoggerFactory[IO] = Slf4jFactory.create[IO]

class AppError(msg: String) extends Exception(msg) with NoStackTrace

case class SubDomain(prefix: String, domain: String):
  override def toString(): String = prefix + "." + domain

object SubDomain:
  def apply(s: String): Either[String, SubDomain] =
    val xs = s.split("\\.")
    if xs.length < 2 then Left("sub-domain needs to be specified as <subdomain>.<domain>.<tld>")
    else Right(new SubDomain(xs.take(xs.length - 2).mkString("."), xs.drop(xs.length - 2).mkString(".")))

object Main extends CommandIOApp(name = "cfddns", version = "0.1.0", header = "Cloudflare Dynamic DNS update"):
  def injectAuth(bearerToken: String): ClientEndpointMiddleware[IO] =
    new ClientEndpointMiddleware.Simple[IO]:
      private val mid: Client[IO] => Client[IO] = inputClient =>
        Client[IO](request =>
          inputClient.run(request.putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, bearerToken))))
        )

      def prepareWithHints(serviceHints: Hints, endpointHints: Hints): Client[IO] => Client[IO] =
        serviceHints.get[smithy.api.HttpBearerAuth] match
          case Some(_) =>
            endpointHints.get[smithy.api.Auth] match
              case Some(auths) if auths.value.isEmpty => identity
              case _                                  => mid
          case None => identity

  def prog(client: Client[IO], token: String, zone: String, subDomains: NonEmptyList[SubDomain]): IO[Unit] =
    def first[T](xs: List[T], errMsg: String) =
      if xs.length > 0 then xs(0).pure[IO] else IO.raiseError(AppError(errMsg))

    def updIpAddr(ipSvc: MyIpService[IO], cfSvc: CloudflareService[IO]): IO[Unit] =
      def getDomainId(zoneId: String, x: SubDomain) =
        cfSvc
          .getDnsRec(zoneId, x.toString())
          .flatMap(y =>
            first(y.result, s"Invalid sub-domain '$x'")
              .map(_.id)
          )

      for
        ipAddr <- ipSvc.getIp()
        zoneId <- cfSvc.getZoneId(zone).flatMap(x => first(x.result, s"Unable to obtain zone ID for '$zone'").map(_.id))
        domIds <- subDomains.traverse(getDomainId(zoneId, _))
        newDns <- domIds.traverse(x => cfSvc.updDnsRec(zoneId, x, ipAddr.ip).map(_.result))
        _      <- newDns.traverse(x => IO.println(s"${x.name} <- ${x.content}"))
      yield ()

    val ipRes =
      SimpleRestJsonBuilder(MyIpService)
        .client(client)
        .uri(uri"https://api.myip.com")
        .resource

    val cfRes =
      SimpleRestJsonBuilder(CloudflareService)
        .client(client)
        .uri(uri"https://api.cloudflare.com/client/v4")
        .middleware(injectAuth(token))
        .resource

    ipRes.flatMap(i => cfRes.map((i, _))).use(updIpAddr(_, _))

  def run(tokenIO: IO[String], zone: String, subDomains: NonEmptyList[SubDomain], showLogs: Boolean): IO[ExitCode] =
    def abend(msg: String) = std.Console[IO].errorln(msg).as(ExitCode.Error)

    val loggedClient = Logger[IO](
      logHeaders = false,
      logBody = true,
      logAction = Some((msg: String) => cats.effect.std.Console[IO].println(msg))
    )

    tokenIO
      .flatMap: token =>
        EmberClientBuilder
          .default[IO]
          .build
          .use(client => prog(if showLogs then loggedClient(client) else client, token, zone, subDomains))
          .as(ExitCode.Success)
      .handleErrorWith:
        case err: NoSuchFileException => abend(s"${err.getMessage()} is not a valid file")
        case err: AppError            => abend(err.getMessage())

  def main: Opts[IO[ExitCode]] =
    def validateSameDomain(zone: Option[String], subDomains: NonEmptyList[SubDomain]) =
      val zone_ = zone.getOrElse(subDomains.head.domain)
      subDomains.find(_.domain != zone_).map(x => s"$x must have '$zone_' as domain").toInvalidNel((zone_, subDomains))

    val subDomains = Opts.arguments[String]("sub-domain").mapValidated(_.traverse(SubDomain(_).toValidatedNel))
    val zone       = Opts.option[String]("zone", help = "zone name (default: same as the domain name)").orNone
    val showLogs   = Opts.flag("show-logs", help = "Show HTTP request and response messages").orFalse

    val zoneSubD = (zone, subDomains).tupled.mapValidated(validateSameDomain)

    val token =
      val tokenFile =
        Opts
          .option[Path]("api-token-file", short = "f", help = "Cloudflare API token file")
          .map(f => IO.blocking(Files.readString(f)))
      val tokenEnv = Opts.env[String]("CLOUDFLARE_API_TOKEN", help = "Cloudflare API token").map(IO.pure(_))
      tokenFile.orElse(tokenEnv)

    (token, zoneSubD, showLogs).mapN((t, zs, l) => run(t, zs._1, zs._2, l))
