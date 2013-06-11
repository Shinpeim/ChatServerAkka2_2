import akka.io.{SymmetricPipePair, PipelineContext, SymmetricPipelineStage}

case class TwoLines(first: String, second: String)

object ClientMessageStage {
  trait Message
  trait Event extends Message
  trait Command extends Message

  case class  ReceivedName(name: String) extends Event
  case class  ReceivedEnter(room: String) extends Event
  case object ReceivedExit extends Event
  case class  ReceivedChatMessage(message: String) extends Event
  case class  ReceivedUnsupportedCommand(eventName: String) extends Event
  case class  ReceivedInvalidCommand(commandName: String, message: String) extends Event

  case class SomeoneEntered(name: String) extends Command
  case class SomeoneExited(name: String) extends Command
  case class SomeoneSaid(name:String, message: String) extends Command
  case class NotifyError(message: String) extends Command
}

class ClientMessageStage extends SymmetricPipelineStage[PipelineContext, ClientMessageStage.Message, String] {
  import ClientMessageStage._

  def apply(ctx:PipelineContext) = new SymmetricPipePair[Message, String] {
    override def commandPipeline = {
      case SomeoneEntered(name) => ctx.singleCommand("> " + name + " entered the room\r\n")
      case SomeoneExited(name) => ctx.singleCommand("> " + name + " left the room\r\n")
      case SomeoneSaid(name, message) => ctx.singleCommand("> " + name + " said: " + message + "\r\n")
      case NotifyError(message) => ctx.singleCommand("> error: " + message + "\r\n")
    }

    override def eventPipeline = {string: String =>
      val inputs = string.split(" ")
      val receivedCommand = inputs.head
      val args = inputs.tail

      val event = receivedCommand match {
        case "NAME" => args.headOption match {
          case None => ReceivedInvalidCommand("NAME", "your name is required")
          case Some(name) => ReceivedName(name)
        }
        case "ENTER" => args.headOption match {
          case None => ReceivedInvalidCommand("ENTER", "room name is required")
          case Some(room) => ReceivedEnter(room)
        }
        case "EXIT" => ReceivedExit
        case "SAY" => args.headOption match {
          case None => ReceivedInvalidCommand("SAY", "message is required")
          case Some(message) => ReceivedChatMessage(message)
        }
        case command => ReceivedUnsupportedCommand(command)
      }

      ctx.singleEvent(event)
    }
  }
}

