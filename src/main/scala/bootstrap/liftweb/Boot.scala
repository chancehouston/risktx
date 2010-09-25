package bootstrap.liftweb

import javax.mail.{Authenticator, PasswordAuthentication}

import _root_.net.liftweb.http._
import _root_.net.liftweb.http.auth._
import _root_.net.liftweb.http.provider.{HTTPRequest}
import _root_.net.liftweb.sitemap._
import _root_.net.liftweb.sitemap.Loc._
import _root_.net.liftweb.common._
import _root_.net.liftweb.util._
import _root_.net.liftweb.mongodb._
import _root_.net.liftweb.widgets.menu.MenuWidget
import _root_.net.liftweb.common._
import Helpers._

import se.scalablesolutions.akka.actor.{SupervisorFactory, Actor}
import se.scalablesolutions.akka.config.ScalaConfig._
import se.scalablesolutions.akka.util.Logging

//import sample.lift.{PersistentSimpleService, SimpleService}
import sample.lift.{SimpleService}

/**
 * the Lift initialisation class
 */
class Boot extends Logger {
  def boot {

    org.apache.log4j.BasicConfigurator.configure

    info("Booting RiskTx...")

    // allow requests for Axis2 to pass straight through the LiftFilter 
    LiftRules.liftRequest.append({
      case r if (r.path.partPath match {
        case "services" :: _ => true
        case _ => false
      }) => false
    })

    configMongoDB
    configMailer
    configAkka

    // package within which Lift looks for snippets
    LiftRules.addToPackages("org.risktx")

    // TODO: build SiteMap using new DSL

    // initialise MenuWidget (from lift-widgets)
    MenuWidget.init()

    // Map request to the primary key for Item to use our view
    LiftRules.statelessRewrite.append {
      case RewriteRequest(
      ParsePath(List("test-harness", "attachment", action, id), _, _, _), _, _) =>
        RewriteResponse("test-harness" :: "attachment" :: action :: Nil, Map("id" -> id))
    }

    /*
     * Show the spinny image when an Ajax call starts
     */
    LiftRules.ajaxStart =
            Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)

    /*
     * Make the spinny image go away when it ends
     */
    LiftRules.ajaxEnd =
            Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

    LiftRules.early.append(makeUtf8)
    LiftRules.useXhtmlMimeType = false

    LiftRules.passNotFoundToChain = true

  }

  /**
   * Force the request to be UTF-8
   */
  private def makeUtf8(req: HTTPRequest) {
    req.setCharacterEncoding("UTF-8")
  }

  /**
   * Configure MongoDB connection from properties file
   */
  def configMongoDB() {
    info("Configuring MongoDB")

    var mongoAddress = MongoAddress(MongoHost(), "risktx")

    (Props.get("mongo.host"), Props.getInt("mongo.port"), Props.get("mongo.db")) match {
      case (Full(host), Full(port), Full(db)) => {
        mongoAddress = MongoAddress(MongoHost(host, port), db)

        (Props.get("mongo.user"), Props.get("mongo.password")) match {
          case (Full(user), Full(password)) => {
            MongoDB.defineDbAuth(DefaultMongoIdentifier, mongoAddress, user, password)
          }
          case _ => {
            MongoDB.defineDb(DefaultMongoIdentifier, mongoAddress)
          }
        }
      }
      case _ => {
        info("MongoDB settings are missing... default is localhost:27017:risktx")
        MongoDB.defineDb(DefaultMongoIdentifier, mongoAddress)
      }
    }

  }

  /**
   * Configure email functionality from properties file
   */
  def configMailer() {
    info("Configuring Mailer")

    Props.get("smtp.host") match {
      case Full(host) => {
        System.setProperty("mail.smtp.host", host)
        info("SMTP host set to " + host)

        Props.get("smtp.enabletls") match {
          case Full(tls) => {
            System.setProperty("mail.smtp.starttls.enable", "true")
            info("SMTP TLS is enabled")
          }
          case _ => //do nothing
        }

        (Props.get("smtp.user"), Props.get("smtp.password")) match {
          case (Full(user), Full(password)) => {
            System.setProperty("mail.smtp.auth", "true")
            Mailer.authenticator = Full(new Authenticator {
              override def getPasswordAuthentication = new PasswordAuthentication(user, password)
            })
            info("SMTP authentication enabled, user set to " + user)
          }
          case _ => //do nothing
        }
      }
      case _ => info("SMTP settings are missing, email notifications are disabled")
    }
  }

  def configAkka() {
    info("Configuring Akka")

    val factory = SupervisorFactory(
      SupervisorConfig(
        RestartStrategy(OneForOne, 3, 100, List(classOf[Exception])),
        Supervise(new SimpleService,LifeCycle(Permanent)) ::Nil))

     //Supervise(new PersistentSimpleService,LifeCycle(Permanent)) ::

    info("Starting Akka ")
    factory.newInstance.start

    info("Akka Started")
  }

}
