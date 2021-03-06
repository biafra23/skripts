#!/usr/bin/env kotlin-script.sh

package p2p

import io.reactivex.Completable
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.selects.select
import java.util.*
import java.util.concurrent.TimeUnit

data class Msg(val id: Int, val value: Int)


fun main(args: Array<String>) = runBlocking {

    val streamToSend = Channel<Msg>()
    val streamSent = Channel<Msg>()
    val streamError = Channel<Msg>()

    val nearby = FakeNearby("tx42", true, "sender23", streamToSend, streamSent, streamError)

    val launchNearbyJob = launch(CommonPool) {
        //        withTimeout(30, TimeUnit.SECONDS) {
        nearby.orchestrateNearby()
//        }
    }

    val sendMessagesJob = launch(CommonPool) {
        for (id in 1..10) {
            val msg = Msg(id = id, value = id * id)
            println("Please send $msg")
            streamToSend.send(msg)
            delay(200)
        }
    }
    val replayErrorsJob = launch(CommonPool) {
        for (msg in streamError) {
            println("Message error $msg - please retry it")
            streamToSend.send(msg)
        }
    }

    val msgsOkJob = launch(CommonPool) {
        for (msg in streamSent) {
            println("Msg $msg was correctly sent")
        }

    }


    // wait for all jobs to complete
    listOf(launchNearbyJob, sendMessagesJob, replayErrorsJob, msgsOkJob)
            .forEach { job -> job.join() }


}


class FakeNearby(
        val transaction: String,
        val isSender: Boolean,
        var peer: String,
        val inputChannel: ReceiveChannel<Msg>,
        val outputOkChannel: SendChannel<Msg>,
        val outputErrorChannel: SendChannel<Msg>) {

    val canSendChannel = Channel<Boolean>()

    suspend fun orchestrateNearby() {
        val success = connectGoogleClient()
        if (!success) println("Google Play Services is not setup")

        val lifecycleEvents = appLifecycleEvents()
        val randomEvents = randomNetworkErrors()

        var shouldContinue = true


        while (true) {
            select<Unit> {

                canSendChannel.onReceiveOrNull { canSend ->
                    println("Select: canSend=$canSend")
                    if (canSend == true) {
                        startSendingMessages()
                    }
                }
                lifecycleEvents.onReceiveOrNull { value ->
                    //                    canSendChannel.send(false)
                    if (value == "START") {
                        println("Select: lifecycleEvents=$value start")
                        startNearby()
                    } else { // "STOP"
                        println("Stopping")
                    }
                }
                randomEvents.onReceiveOrNull { msg ->
                    println("Select: randomEvents=$msg")
                    canSendChannel.send(false)
                    if (msg == "DISCONNECT") {
                        shouldContinue = false
                        startNearby()
                    } else { // "FAIL"
                        disconnectGoogleClient()
                    }
                }
            }

            if (!shouldContinue) {
                break
            }
        }
        println("Nearby interrupted, you may want to retry it")

    }

    suspend fun startNearby() {
        println("start nearby")
//        canSendChannel.send(false)

        if (isSender) {
            val connectionRequests = advertise()
            for (id in connectionRequests) {
                println("Advertise: $id")
                if (id != peer) continue
                val success = tryConnect(peer)
                if (success) {
                    println("Connect success")
//                    canSendChannel.send(true)
                } else {
                    println("Error while connecting")
                }
            }
        } else {
            val endpointsFound = discover()
            for (id in endpointsFound) {
                println("Discover: $id")
                if (id != peer) continue
                val success = tryConnect(peer)
                if (success) {
                    println("Connect success")
//                    canSendChannel.send(true)
                } else {
                    println("Error while connecting")
                }
            }
        }
    }

    suspend fun startSendingMessages() {
        inputChannel.consumeEach { msg ->
            println("Sending msg $msg")
            delay(500)
            if (badLuck(20)) {
                println("Message has not been sent successfully, you will have to resend it")
                outputErrorChannel.send(msg)
            } else {
                println("Message $msg sent successfully")
                outputOkChannel.send(msg)
            }
        }
    }

    suspend fun appLifecycleEvents() = produce<String>(CommonPool) {
        println("START")
        send("START")
        delay(10000)
        println("STOP")
        send("STOP")
        delay(20000)
        println("START")
        send("START")
        println("STOP")
        send("STOP")
    }

    suspend fun randomNetworkErrors() = produce<String>(CommonPool) {
        while (true) {
            delay(1000)
            if (badLuck(10)) send("DISCONNECT")
            if (badLuck(10)) send("FAIL")
        }
    }

    suspend fun connectGoogleClient(): Boolean {
        delay(100)
        return badLuck(10)
    }

    suspend fun disconnectGoogleClient() {
        delay(10)
    }

    suspend fun discover() = advertise()

    suspend fun advertise() = produce<String>(CommonPool) {
        when (random.nextInt(100)) {
            in 0..30 -> { // Best case scenario, we discover our peer, only once, no error
                delay(100)
                send(peer)
            }
            in 30..50 -> { // We get our peer multiple times
                repeat(3) {
                    delay(100)
                    send(peer)
                }
            }
            in 50..90 -> { // we discover someone but not my peer
                delay(100)
                send("notmypeer")
                delay(100)
                send(peer)
            }
            in 90..100 -> { // we don't discovery my peer
                send("notmypeer")
                delay(300)
            }
        }
    }


    suspend fun tryConnect(peer: String): Boolean {
        delay(100)
        return badLuck(40)
    }

    val random = Random()
    fun badLuck(probability: Int): Boolean {
        check(probability in 0..100) { "Probability $probability invalid, should be in 0..100" }
        return random.nextInt() <= probability
    }

}