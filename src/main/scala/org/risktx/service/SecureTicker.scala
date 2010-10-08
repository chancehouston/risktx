package org.risktx.service

import se.scalablesolutions.akka.util._
import se.scalablesolutions.akka.actor._
import se.scalablesolutions.akka.actor.Actor._
import se.scalablesolutions.akka.util.Logging
import se.scalablesolutions.akka.stm.TransactionalMap

import javax.annotation.security.{RolesAllowed, DenyAll, PermitAll}
import javax.ws.rs.{GET, Path, Produces}
import java.lang.Integer

/**
* a REST Actor with class level paranoia settings to deny all access
*
* The interesting part is
* @RolesAllowed
* @PermitAll
* @DenyAll
*/
@Path("/secureticker")
class SecureTickActor extends Actor with Logging {
//  makeTransactionRequired   // Removed worked in 0.7.1 

  case object Tick
  val thisRef = actorOf(this)

  private val KEY = "COUNTER"
  private var hasStartedTicking = false
  private lazy val storage = TransactionalMap[String, Integer] //TransactionalState.newMap[String, Integer]

  /**
     * allow access for any user to "/secureticker/public"
     */
  @GET
  @Produces(Array("text/xml"))    
  @Path("/public")
  @PermitAll
  def publicTick = tick

  /**
     * restrict access to "/secureticker/chef" users with "chef" role
     */
  @GET
  @Path("/chef")
  @Produces(Array("text/xml"))
  @RolesAllowed(Array("chef"))
  def chefTick = tick

  /**
     * access denied for any user to default Path "/secureticker/"
     */
  @GET
  @Produces(Array("text/xml"))
  @DenyAll
  def paranoiaTick = tick

  def tick = (thisRef !! Tick) match {
    case (Some(counter)) => (<success>Tick:
      {counter}
    </success>)
    case _ => (<error>Error in counter</error>)
  }

  def receive = {
    case Tick => if (hasStartedTicking) {
      val counter = storage.get(KEY).get.intValue
      storage.put(KEY, counter + 1)
      self.reply(new Integer(counter + 1))
    } else {
      storage.put(KEY, 0)
      hasStartedTicking = true
      self.reply(new Integer(0))
    }
  }
}