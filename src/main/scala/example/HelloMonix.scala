package example

import java.nio.ByteBuffer

import cats.effect._
import cats.implicits._
import monix.eval._
import monix.reactive.Observable

import scala.concurrent.duration.Duration

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

                )

object HelloMonix extends TaskApp {
  val apiUrl = "https://public-api.adsbexchange.com/VirtualRadar/AircraftList.json"
  /** App's main entry point. */
  def run(args: List[String]): Task[ExitCode] =
    args.headOption match {
      case Some(name) =>
        Task(println(s"Hello, $name!")).as(ExitCode.Success)
      case None =>
        Task(System.err.println("Usage: Hello name")).as(ExitCode(2))
    }

  def fetchAircrafts(): Task[_] = {
    import com.softwaremill.sttp._
    import com.softwaremill.sttp.asynchttpclient.monix._

    implicit val sttpBackend = AsyncHttpClientMonixBackend()

    sttp
      .get(uri"https://public-api.adsbexchange.com/VirtualRadar/AircraftList.json")
      .response(asStream[Observable[ByteBuffer]])
      .readTimeout(Duration.Inf)
      .send()
  }
}

