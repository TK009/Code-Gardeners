/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package http

import akka.actor.{ActorSystem, Props, ActorRef}
import akka.io.{IO, Tcp}
import spray.can.Http
import spray.servlet.WebBoot
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.{Await, Awaitable, Future}
import java.util.Date
import java.net.InetSocketAddress
import scala.collection.JavaConversions.asJavaIterable

import agentSystem._
import responses.{RequestHandler, SubscriptionManager}
import types.Path
import types.OdfTypes._
import types.OmiTypes.WriteRequest
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import database._

import scala.util.{Try, Failure, Success}
import xml._

import scala.language.postfixOps

/**
 * Initialize functionality with [[Starter.init]] and then start standalone app with [[Starter.start]],
 * seperated for testing purposes and for easier implementation of different starting methods (standalone, servlet)
 */
trait Starter {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-core")

  /**
   * Settings loaded by akka (typesafe config) and our [[OmiConfigExtension]]
   */
  val settings = Settings(system)

  val subHandlerDbConn: DB = new DatabaseConnection
  val subManager = system.actorOf(SubscriptionManager.props()(subHandlerDbConn), "subscription-handler")
  

  import scala.concurrent.ExecutionContext.Implicits.global
  def saveSettingsOdf(agentSystem: ActorRef) :Unit = {
    if ( settings.settingsOdfPath.nonEmpty ) {
      // Same timestamp for all OdfValues of the settings
      val date = new Date();
      val currentTime = new java.sql.Timestamp(date.getTime)

      // Save settings in db, this works also as a test for writing
      val numDescription =
        "Number of latest values (per sensor) that will be saved to the DB"
      system.log.info(s"$numDescription: ${settings.numLatestValues}")
      database.setHistoryLength(settings.numLatestValues)
      system.log.info("Testing InputPusher...")
      database.setHistoryLength(settings.numLatestValues)
      val objects = fromPath(
        OdfInfoItem(
          Path(settings.settingsOdfPath + "num-latest-values-stored"), 
          Iterable(OdfValue(settings.numLatestValues.toString, "xs:integer", currentTime)),
          Some(OdfDescription(numDescription))
        ))
      
      val writeTestTTL = 10 minutes
      val write = WriteRequest( writeTestTTL, objects)
      var promiseResult = PromiseResult()
      agentSystem ! PromiseWrite( promiseResult, write )
      val future : Future[ResponsibleAgentResponse]= promiseResult.isSuccessful
      future.onSuccess{
        case s =>
        system.log.info("O-MI InputPusher system working.")
        true
      }

      future.onFailure{
        case e => system.log.error(e, "O-MI InputPusher system not working; exception:")
      }
      Await.result(future, writeTestTTL)
    }
  }


  /**
   * Start as stand-alone server.
   * Creates single Actors.
   * Binds to configured external agent interface port.
   *
   * @return O-MI Service actor which is not yet bound to the configured http port
   */
  def start(dbConnection: DB = new DatabaseConnection): ActorRef = {

    // create and start sensor data listener
    // TODO: Maybe refactor to an internal agent!

    val agentManager = system.actorOf(
      AgentSystem.props(dbConnection, subManager),
      "agent-system"
    )
    val sensorDataListener = system.actorOf(ExternalAgentListener.props(agentManager), "agent-listener")
    
    saveSettingsOdf(agentManager)
    val dbmaintainer = system.actorOf(DBMaintainer.props( dbConnection ), "db-maintainer")
    val requestHandler = new RequestHandler(subManager, agentManager)(dbConnection)

    val omiNodeCLIListener =system.actorOf(
      Props(new OmiNodeCLIListener(  agentManager, subManager, requestHandler)),
      "omi-node-cli-listener"
    )

    // create omi service actor
    val omiService = system.actorOf(Props(
      new OmiServiceActor(
        requestHandler
      )
    ), "omi-service")


    implicit val timeoutForBind = Timeout(5.seconds)

    IO(Tcp)  ? Tcp.Bind(sensorDataListener,
      new InetSocketAddress(settings.externalAgentInterface, settings.externalAgentPort))
    IO(Tcp)  ? Tcp.Bind(omiNodeCLIListener,
      new InetSocketAddress("localhost", settings.cliPort))

    return omiService
  }



  /** Start a new HTTP server on configured port with our service actor as the handler.
   */
  def bindHttp(service: ActorRef): Unit = {

    implicit val timeoutForBind = Timeout(5.seconds)

    IO(Http) ? Http.Bind(service, interface = settings.interface, port = settings.port)
  }
}



/**
 * Starting point of the stand-alone program.
 */
object Boot extends Starter {// with App{
  def main(args: Array[String]) = {
  Try {
    val serviceActor = start()
    bindHttp(serviceActor)
  } match {
    case Failure(ex) => system.log.error(ex, "Error during startup")
    case Success(_) => system.log.info("Process exited normally")
  }
  }

}


/**
 * Starting point of the servlet program.
 */
class ServletBoot extends Starter with WebBoot {
  override implicit val system = Boot.system
  val serviceActor = start()
  // bindHttp is not called
}
