package net.corda.finance.obligation.app

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.concurrent.ScheduledService
import javafx.concurrent.Task
import javafx.util.Duration
import tornadofx.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val POLL_INTERVAL = 5.0

data class DataResult(val result: String, val lastRetrieve: LocalDateTime)

class PollController(val asyncTask: () -> DataResult) : Controller() {
    val currentData = SimpleStringProperty()
    val stopped = SimpleBooleanProperty(true)
    val lastUpdated = SimpleStringProperty("")

    val scheduledService = object : ScheduledService<DataResult>() {
        init {
            period = Duration.seconds(POLL_INTERVAL)
        }

        override fun createTask(): Task<DataResult> = FetchDataTask()
    }

    fun start() {
        scheduledService.restart()
        stopped.value = false
    }

    fun stop() {
        scheduledService.cancel()
        stopped.value = true
    }

    inner class FetchDataTask : Task<DataResult>() {

        override fun call(): DataResult = asyncTask()

        override fun succeeded() {
            println("data updated: " + value.result + " (last updated " + value.lastRetrieve + ")")
            lastUpdated.value = value.lastRetrieve.format(DateTimeFormatter.ISO_LOCAL_TIME)
            this@PollController.currentData.value = value.result
        }

        override fun failed() {
            println("failed retrieval")
            exception.printStackTrace()
        }
    }
}