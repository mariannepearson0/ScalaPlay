package controllers

import javax.inject.Inject

import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo._
import reactivemongo.api.Cursor
import reactivemongo.play.json.collection.JSONCollection
import play.modules.reactivemongo.json._
import reactivemongo.bson.BSONDocument

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

  class MyController @Inject() (val reactiveMongoApi: ReactiveMongoApi) extends Controller
    with MongoController with ReactiveMongoComponents{

    def collection: Future[JSONCollection] = database.map(
      _.collection[JSONCollection]("persons"))

    import models._
    import models.JsonFormats._

    def create = Action.async {
      val user = User(29, "John", "Smith", List(Feed("Slashdot news", "http://slashdot.org/slashdot.rdf")))
      val futureResult = collection.flatMap(_.insert(user))
      futureResult.map(_ => Ok)
    }

    def createFromJson = Action.async(parse.json) { request =>
      request.body.validate[User].map { user =>
        collection.flatMap(_.insert(user)).map { lastError =>
          Logger.debug(s"Successfully inserted with LastEroor: $lastError")
          Created
        }
      }.getOrElse(Future.successful(BadRequest("invalid json")))
    }

    def findByName(lastName: String) = Action.async {
      val cursor: Future[Cursor[User]] = collection.map {
        _.find(Json.obj("lastName" -> lastName)).
          sort(Json.obj("created" -> -1)).
          cursor[User]
      }
      val futureUsersList: Future[List[User]] = cursor.flatMap(_.collect[List]())
      futureUsersList.map { persons =>
        Ok(persons.toString)
      }
    }

    def removeAll(query: String) = Action.async {
      val selector = BSONDocument("lastName" -> query)
      val futureRemove = collection.flatMap(_.remove(selector))
      futureRemove.map(_ => Ok)
    }

    def removeOne(query: String) = Action.async {
      val selector = BSONDocument("lastName" -> query)
      val futureRemove = collection.flatMap(_.remove(selector, firstMatchOnly = true))
      futureRemove.map(_ => Ok)
    }

    def update(nameQuery:String, changeTo:User) = Action.async {
      val selector = BSONDocument("lastName" -> nameQuery)
      val futureUpdate = collection.map(_.findAndUpdate(selector, changeTo))
      futureUpdate.map(_ => Ok)
    }
  }
