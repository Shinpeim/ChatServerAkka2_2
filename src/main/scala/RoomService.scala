import akka.actor.{ActorRef, Actor}
import scala.collection.immutable.{Map, Set}

object RoomService {
  case class Enter(room: String, actor: ActorRef)
  case class Exit(room:String, actor: ActorRef)
  case class Broadcast(room: String, message: ClientMessageStage.Command)
}
class RoomService extends Actor {
  var chatRooms: Map[String, Set[ActorRef]] = Map.empty

  private def enter(room: String, actor: ActorRef) = {
    chatRooms.get(room) match {
      case None =>
        chatRooms += room -> Set(actor)
      case Some(actors) =>
        chatRooms = chatRooms.updated(room, actors + actor)
    }
  }

  private def exit(room: String, actor: ActorRef) = {
    chatRooms.get(room) match {
      case None =>
        Unit
      case Some(actors) if actors.size == 1 =>
        chatRooms -= room
      case Some(actors) =>
        chatRooms = chatRooms.updated(room, actors - actor)
    }
  }

  private def broadcast(room: String, command: ClientMessageStage.Command) = {
    chatRooms.getOrElse(room, Set.empty).foreach(_ ! command)
  }

  def receive = {
    case RoomService.Enter(room, actor) =>
      enter(room, actor)
    case RoomService.Exit(room, actor) =>
      exit(room, actor)
    case RoomService.Broadcast(room, message) =>
      broadcast(room, message)
  }
}