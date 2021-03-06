package example

import java.nio.ByteBuffer

import cats.effect._
import io.circe.{Decoder, DecodingFailure, ParsingFailure}
import monix.eval._
import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import org.jsfr.json.{JsonPathListener, JsonSurferJackson, ParsingContext}
import org.jsfr.json.compiler.JsonPathCompiler
import org.jsfr.json.exception.JsonSurfingException

import scala.collection.immutable.Queue
import scala.concurrent.Future

/**
  * The states property is a two-dimensional array. Each row represents a state vector and contains the following fields:
  *
  * Index	Property	Type	Description
  * 0	icao24	string	Unique ICAO 24-bit address of the transponder in hex string representation.
  * 1	callsign	string	Callsign of the vehicle (8 chars). Can be null if no callsign has been received.
  * 2	origin_country	string	Country name inferred from the ICAO 24-bit address.
  * 3	time_position	int	Unix timestamp (seconds) for the last position update. Can be null if no position report was received by OpenSky within the past 15s.
  * 4	last_contact	int	Unix timestamp (seconds) for the last update in general. This field is updated for any new, valid message received from the transponder.
  * 5	longitude	float	WGS-84 longitude in decimal degrees. Can be null.
  * 6	latitude	float	WGS-84 latitude in decimal degrees. Can be null.
  * 7	baro_altitude	float	Barometric altitude in meters. Can be null.
  * 8	on_ground	boolean	Boolean value which indicates if the position was retrieved from a surface position report.
  * 9	velocity	float	Velocity over ground in m/s. Can be null.
  * 10	true_track	float	True track in decimal degrees clockwise from north (north=0°). Can be null.
  * 11	vertical_rate	float	Vertical rate in m/s. A positive value indicates that the airplane is climbing, a negative value indicates that it descends. Can be null.
  * 12	sensors	int[]	IDs of the receivers which contributed to this state vector. Is null if no filtering for sensor was used in the request.
  * 13	geo_altitude	float	Geometric altitude in meters. Can be null.
  * 14	squawk	string	The transponder code aka Squawk. Can be null.
  * 15	spi	boolean	Whether flight status indicates special purpose indicator.
  * 16	position_source	int	Origin of this state’s position: 0 = ADS-B, 1 = ASTERIX, 2 = MLAT
  */
case class OpenSkyState(icao24: String,
                        callsign: Option[String],
                        originCountry: String,
                        timePosition: Option[Int],
                        lastContact: Int,
                        longitude: Option[Double],
                        latitude: Option[Double],
                        baroAltitude: Option[Double],
                        onGround: Boolean,
                        velocity: Option[Double],
                        trueTrack: Option[Double],
                        verticalRate: Option[Double],
                        sensors: Seq[Int],
                        geoAltitude: Option[Double],
                        squawk: Option[String],
                        spi: Boolean,
                        positionSource: Int)

object HelloMonix extends TaskApp {
  val apiUrl = "https://opensky-network.org/api/states/all"

  implicit val decodeInstant: Decoder[OpenSkyState] = Decoder.instance { c =>
    c.focus.flatMap(_.asArray) match {
      case Some(arr) =>
        for {
          icao24 <- arr.head.as[String]
          callsign <- arr(1).as[Option[String]]
          originCountry <- arr(2).as[String]
          timePosition <- arr(3).as[Option[Int]]
          lastContact <- arr(4).as[Int]
          longitude <- arr(5).as[Option[Double]]
          latitude <- arr(6).as[Option[Double]]
          baroAltitude <- arr(7).as[Option[Double]]
          onGround <- arr(8).as[Boolean]
          velocity <- arr(9).as[Option[Double]]
          trueTrack <- arr(10).as[Option[Double]]
          verticalRate <- arr(11).as[Option[Double]]
          sensors <- arr(12).as[Option[Seq[Int]]]
          geoAltitude <- arr(13).as[Option[Double]]
          squawk <- arr(14).as[Option[String]]
          spi <- arr(15).as[Boolean]
          positionSource <- arr(16).as[Int]
        } yield OpenSkyState(
          icao24 = icao24,
          callsign = callsign,
          originCountry = originCountry,
          timePosition = timePosition,
          lastContact = lastContact,
          longitude = longitude,
          latitude = latitude,
          baroAltitude = baroAltitude,
          onGround = onGround,
          velocity = velocity,
          trueTrack = trueTrack,
          verticalRate = verticalRate,
          sensors = sensors.getOrElse(Nil),
          geoAltitude = geoAltitude,
          squawk = squawk,
          spi = spi,
          positionSource = positionSource
        )

      case None => Left(DecodingFailure("OpenSkyState", c.history))
    }
  }

  /** App's main entry point. */
  def run(args: List[String]): Task[ExitCode] =
  /*args.headOption match {
    case Some(name) =>
      Task(println(s"Hello, $name!")).as(ExitCode.Success)
    case None =>
      Task(System.err.println("Usage: Hello name")).as(ExitCode(2))
  }*/

    fetchAircrafts().map(_ => ExitCode.Success)

  def fetchAircrafts(): Task[_] = {
    import com.softwaremill.sttp._
    import com.softwaremill.sttp.asynchttpclient.monix._
    import scala.concurrent.duration._

    implicit val sttpBackend = AsyncHttpClientMonixBackend()
    import monix.execution.Scheduler.Implicits.global

    scheduler.scheduleAtFixedRate(0.seconds, 10.seconds) {
      sttp
        .get(uri"$apiUrl")
        .response(asStream[Observable[ByteBuffer]])
        .mapResponse(o => o.map(_.array()).liftByOperator(new ParsingOperator("$.states[*]")))
        .readTimeout(Duration.Inf)
        .send()
        .map({ r =>
          r.unsafeBody.foreach({ b =>
            println(b)
          })
        })
        .runAsyncUncancelable(_ => ())
    }

    Task.never
  }
}

private class ParsingOperator[T: Decoder](path: String) extends Observable.Operator[Array[Byte], Either[Throwable, T]] {
  private val compiledPath = JsonPathCompiler.compile(path)
  private var buffer = Queue.empty[Either[Throwable, T]]

  import io.circe.parser.decode

  private val surfer = JsonSurferJackson.INSTANCE
  private val config = surfer.configBuilder
    .bind(compiledPath, new JsonPathListener {
      override def onValue(value: Any, context: ParsingContext): Unit = {
        buffer = buffer.enqueue(decode[T](value.toString))
      }
    })
    .build

  private val parser = surfer.createNonBlockingParser(config)

  def apply(out: Subscriber[Either[Throwable, T]]): Subscriber[Array[Byte]] = {
    new Subscriber[Array[Byte]] {
      implicit val scheduler = out.scheduler
      private[this] var isDone = false

      def onNext(elem: Array[Byte]): Future[Ack] = {
        try {
          parser.feed(elem, 0, elem.length)

          if (buffer.nonEmpty) {
            out.onNextAll(buffer)
            buffer = Queue.empty[Either[Throwable, T]]
          }
          Continue
        } catch {
          case e: JsonSurfingException =>
            onError(ParsingFailure(e.getMessage, e))
            Stop
        }
      }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          out.onError(ex)
        }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          out.onComplete()
          parser.endOfInput()
        }
    }
  }
}


