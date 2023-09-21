package service

import zio._

import file._
import file.FileManager._

import model._

object UserService {
  // 생각보다 하나의 서비스에서 여러 가지 파일을 읽을 일이 많은 것 같습니다

  // ---- V 파일 접근 코드 V ----
  private def getReservations() = for {
    reservations <- FileManager.readJson[Reservation](FILE_RESERVATION)
  } yield reservations

  private def addReservation(reservation: Reservation) = for {
    reservations <- getReservations()

    nextReservations = reservations.appended(reservation)

    _ <- FileManager.writeJson(FILE_RESERVATION, nextReservations)
  } yield ()

  private def saveReservations(reservations: List[Reservation]) = for {
    _ <- FileManager.writeJson(FILE_RESERVATION, reservations)
  } yield ()

  private def getExistingUsers() = for {
    users <- FileManager.readJson[ExistingUser](FILE_USER)
  } yield users

  private def addUser(user: NewUser) = for {
    users <- getExistingUsers()

    nextUsers = users.appended(ExistingUser(user.name, user.phone))

    _ <- FileManager.writeJson(FILE_USER, nextUsers)
  } yield ()

  private def getReviews() = for {
    reviews <- FileManager.readJson[Review](FILE_REVIEW)
  } yield reviews

  private def addReview(review: Review) = for {
    reviews <- getReviews()

    nextReviews = reviews.appended(review)

    _ <- FileManager.writeJson(FILE_REVIEW, nextReviews)
  } yield ()

  // ---- V 단순 매핑 코드 V ----

  private def getClosedReservationsOfUser(user: User) = for {
    reservations <- getReservations()

    closedReservationsOfUser = reservations
      .filter(_.user == user)
      .filter(_.isClosed)
  } yield closedReservationsOfUser

  private def getPaidButNotReviewedReservationsOfUser(user: User) = for {
    reservations <- getReservations()
    paidReservationsOfUser = reservations
      .filter(_.user == user)
      .filter(_.isPaied)

  } yield paidReservationsOfUser

  // ---- V 외부 공개 코드 V ----

  def login(userData: Either[String, (String, String)]) = for {
    users <- getExistingUsers()

    result <- userData match {
      case Left(error) => ZIO.left(error)
      case Right(data) =>
        ZIO.right(
          users.contains(ExistingUser(data._1, data._2)) match {
            case true  => ExistingUser(data._1, data._2)
            case false => NewUser(data._1, data._2)
          }
        )
    }

  } yield result

  def findReservationsByUser(user: User) = for {
    users <- getExistingUsers()
    reviews <- getReviews()

    _ <- Console.printLine(reviews)

    reservations <- getReservations()

    result <- user match {
      case ExistingUser(name, phone) =>
        ZIO.right(reservations.filter(_.user == user))
      case NewUser(name, phone) => ZIO.left("기존 예약이 없는 사용자입니다.")
    }

  } yield result

  def makeReservation(
      user: User,
      date: String,
      time: String,
      guestCount: Int
  ) = for {
    // ID 생성을 어디서 해야 할지?
    uuid <- Random.nextInt
    reservation = Reservation(uuid, user, date, time, guestCount)

    _ <- addReservation(reservation)

    _ <- user match {
      case ExistingUser(name, phone) => ZIO.unit
      case newUser: NewUser          => addUser(newUser)
    }

  } yield reservation

  def pay(reservationId: Int) = for {
    reservations <- getReservations()

    changed = reservations.map { reservation =>
      if (reservation.id == reservationId) reservation.copy(isPaied = true)
      else reservation
    }

    _ <- saveReservations(changed)

    result <- ZIO.attempt(changed.head)

  } yield result

  private def writeReview(reservationId: Int, point: Int, content: String) =
    for {
      _ <- ZIO.unit
      review = Review(reservationId, point, content)
      _ <- addReview(review)
    } yield review
}
